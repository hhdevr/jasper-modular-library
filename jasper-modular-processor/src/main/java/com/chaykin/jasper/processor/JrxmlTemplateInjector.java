package com.chaykin.jasper.processor;

import com.chaykin.jasper.core.annotation.CollectionComponentType;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;
import net.sf.jasperreports.components.list.DesignListContents;
import net.sf.jasperreports.components.list.StandardListComponent;
import net.sf.jasperreports.components.table.DesignCell;
import net.sf.jasperreports.components.table.StandardColumn;
import net.sf.jasperreports.components.table.StandardTable;
import net.sf.jasperreports.engine.JRDatasetRun;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.component.Component;
import net.sf.jasperreports.engine.data.JRAbstractBeanDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignComponentElement;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignDatasetRun;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignSubreport;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.PositionTypeEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.xml.JRXmlWriter;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static com.chaykin.jasper.core.contract.JasperModularDataFiller.MAP_PARAMETER_SUFFIX;
import static com.chaykin.jasper.core.contract.JasperModularDataFiller.REPORT_SUFFIX;

/**
 * Idempotently injects missing elements derived from annotated class fields into a
 * {@link JasperDesign} and writes the result to an output stream.
 */
public class JrxmlTemplateInjector {

    private static final int LIST_HEIGHT = 90;

    private static final int CELL_HEIGHT = 30;

    private static final int HEADER_HEIGHT = 20;

    private static final int SUBREPORT_HEIGHT = 100;

    private final Messager messager;

    public JrxmlTemplateInjector(Messager messager) {
        this.messager = messager;
    }

    /**
     * Injects all missing elements into the design and writes the result.
     *
     * @throws JRException if injection or serialization fails
     */
    public void inject(JasperDesign design,
                       List<JrxmlParameter> fields,
                       OutputStream output) throws JRException {
        injectDatasets(design, fields);
        injectParameters(design, fields);
        injectCollectionComponents(design, fields);
        injectSubreportListComponents(design, fields);
        injectSubreportBands(design, fields);

        JRXmlWriter.writeReport(design, output, "UTF-8");
    }

    private void injectParameters(JasperDesign design, List<JrxmlParameter> fields)
            throws JRException {
        for (JrxmlParameter field: fields) {
            if (design.getParametersMap().containsKey(field.name())) {
                note("Parameter already exists - skipping: " + field.name());
                continue;
            }
            JRDesignParameter param = new JRDesignParameter();
            param.setName(field.name());
            param.setValueClassName(field.jrxmlClass());
            design.addParameter(param);
        }
    }

    private void injectDatasets(JasperDesign design, List<JrxmlParameter> fields)
            throws JRException {
        for (JrxmlParameter field: fields) {
            if (field.dataset() == null) {
                continue;
            }

            if (design.getDatasetMap().containsKey(field.dataset().name())) {
                note("Dataset already exists - skipping: "
                                      + field.dataset().name());
                continue;
            }

            JRDesignDataset dataset = new JRDesignDataset(false);
            dataset.setName(field.dataset().name());

            for (JrxmlDatasetField datasetField: field.dataset().fields()) {
                JRDesignField jrField = new JRDesignField();
                jrField.setName(datasetField.name());
                jrField.setValueClassName(datasetField.jrxmlClass());
                dataset.addField(jrField);
            }
            design.addDataset(dataset);
        }
    }

    private void injectCollectionComponents(JasperDesign design, List<JrxmlParameter> fields) {
        List<JrxmlParameter> collectionFields = fields.stream()
                                                      .filter(f -> f.dataset() != null
                                                                   && f.subreportPrefix() == null)
                                                      .toList();
        if (collectionFields.isEmpty()) {
            return;
        }

        JRDesignSection detailSection = (JRDesignSection) design.getDetailSection();

        for (JrxmlParameter field: collectionFields) {
            if (collectionComponentExists(detailSection, field.dataset().name())) {
                note("Collection component already exists - skipping: " + field.name());
                continue;
            }

            JRDesignBand band = emptyBand(LIST_HEIGHT);

            if (field.dataset().componentType() == CollectionComponentType.TABLE) {
                band.addElement(createTableComponent(field));
                note("Injected table component: " + field.name());
            } else {
                band.addElement(createListComponent(field));
                note("Injected list component: " + field.name());
            }

            detailSection.addBand(band);
        }
    }

    private boolean collectionComponentExists(JRDesignSection section, String datasetName) {
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignComponentElement)
                     .map(e -> (JRDesignComponentElement) e)
                     .map(e -> datasetRunOf(e.getComponent()))
                     .anyMatch(run -> run != null && datasetName.equals(run.getDatasetName()));
    }

    private static JRDatasetRun datasetRunOf(Component component) {
        if (component instanceof StandardListComponent list) {
            return list.getDatasetRun();
        }
        if (component instanceof StandardTable table) {
            return table.getDatasetRun();
        }
        return null;
    }

    private JRDesignComponentElement createListComponent(JrxmlParameter field) {
        List<JrxmlDatasetField> datasetFields = field.dataset().fields();
        int columnWidth = field.dataset().columnWidth();
        int totalWidth = datasetFields.size() * columnWidth;

        DesignListContents contents = new DesignListContents();
        contents.setHeight(CELL_HEIGHT);
        contents.setWidth(totalWidth);

        int x = 0;
        for (JrxmlDatasetField datasetField: datasetFields) {
            JRDesignTextField textField = new JRDesignTextField();
            textField.setX(x);
            textField.setY(0);
            textField.setWidth(columnWidth);
            textField.setHeight(CELL_HEIGHT);
            textField.setExpression(expression("$F{" + datasetField.name() + "}"));
            contents.addElement(textField);
            x += columnWidth;
        }

        StandardListComponent listComponent = new StandardListComponent();
        listComponent.setDatasetRun(buildDatasetRun(field));
        listComponent.setContents(contents);

        return componentElement(listComponent, totalWidth, LIST_HEIGHT, "list");
    }

    private JRDesignComponentElement createTableComponent(JrxmlParameter field) {
        List<JrxmlDatasetField> datasetFields = field.dataset().fields();
        int columnWidth = field.dataset().columnWidth();
        int totalWidth = datasetFields.size() * columnWidth;

        StandardTable table = new StandardTable();
        table.setDatasetRun(buildDatasetRun(field));

        for (JrxmlDatasetField datasetField: datasetFields) {
            StandardColumn column = new StandardColumn();
            column.setWidth(columnWidth);

            DesignCell headerCell = new DesignCell();
            headerCell.setHeight(HEADER_HEIGHT);
            JRDesignStaticText headerText = new JRDesignStaticText();
            headerText.setX(0);
            headerText.setY(0);
            headerText.setWidth(columnWidth);
            headerText.setHeight(HEADER_HEIGHT);
            headerText.setText(
                    JRAbstractBeanDataSource.CURRENT_BEAN_MAPPING.equals(datasetField.name())
                    ? field.name()
                    : datasetField.name());
            headerCell.addElement(headerText);
            column.setColumnHeader(headerCell);

            DesignCell detailCell = new DesignCell();
            detailCell.setHeight(CELL_HEIGHT);
            JRDesignTextField detailTextField = new JRDesignTextField();
            detailTextField.setX(0);
            detailTextField.setY(0);
            detailTextField.setWidth(columnWidth);
            detailTextField.setHeight(CELL_HEIGHT);
            detailTextField.setExpression(expression("$F{" + datasetField.name() + "}"));
            detailCell.addElement(detailTextField);
            column.setDetailCell(detailCell);

            table.addColumn(column);
        }

        return componentElement(table, totalWidth, HEADER_HEIGHT + CELL_HEIGHT, "table");
    }

    private JRDesignDatasetRun buildDatasetRun(JrxmlParameter field) {
        JRDesignDatasetRun datasetRun = new JRDesignDatasetRun();
        datasetRun.setDatasetName(field.dataset().name());
        datasetRun.setDataSourceExpression(expression("$P{" + field.name() + "}"));
        return datasetRun;
    }

    private JRDesignComponentElement componentElement(Component component,
                                                      int width,
                                                      int height,
                                                      String componentName) {
        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0);
        element.setY(0);
        element.setWidth(width);
        element.setHeight(height);
        element.setComponent(component);
        element.setPositionType(PositionTypeEnum.FLOAT);
        element.setRemoveLineWhenBlank(true);
        applyComponentKeyIfNeeded(element, componentName);
        return element;
    }

    private JRDesignSubreport createSubreport(int width,
                                              String parametersMapExpression,
                                              String reportExpression) {
        JRDesignSubreport subreport = new JRDesignSubreport(null);
        subreport.setX(0);
        subreport.setY(0);
        subreport.setWidth(width);
        subreport.setHeight(SUBREPORT_HEIGHT);
        subreport.setPositionType(PositionTypeEnum.FLOAT);
        subreport.setRemoveLineWhenBlank(true);
        subreport.setParametersMapExpression(expression(parametersMapExpression));
        subreport.setDataSourceExpression(
                expression("new net.sf.jasperreports.engine.JREmptyDataSource()"));
        subreport.setExpression(expression(reportExpression));
        return subreport;
    }

    static JRDesignBand emptyBand(int height) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(height);
        band.setSplitType(SplitTypeEnum.STRETCH);
        return band;
    }

    private static JRDesignExpression expression(String text) {
        JRDesignExpression expression = new JRDesignExpression();
        expression.setText(text);
        return expression;
    }

    private void injectSubreportBands(JasperDesign design, List<JrxmlParameter> fields) {

        List<String> subreportPrefixes = fields.stream()
                                               .filter(f -> f.subreportPrefix() != null
                                                            && f.dataset() == null)
                                               .map(JrxmlParameter::subreportPrefix)
                                               .toList();

        if (subreportPrefixes.isEmpty()) {
            return;
        }

        JRDesignSection detailSection = (JRDesignSection) design.getDetailSection();
        int columnWidth = design.getColumnWidth();

        for (String prefix: subreportPrefixes) {
            if (subreportBandExists(detailSection, prefix)) {
                note("Subreport band already exists - skipping: " + prefix);
                continue;
            }
            detailSection.addBand(createSubreportBand(prefix, columnWidth));
            note("Injected subreport band: " + prefix);
        }
    }

    private boolean subreportBandExists(JRDesignSection section, String prefix) {
        String expectedExpression = "$P{" + prefix + REPORT_SUFFIX + "}";
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignSubreport)
                     .map(e -> (JRDesignSubreport) e)
                     .anyMatch(sr -> sr.getExpression() != null
                                     && expectedExpression.equals(sr.getExpression().getText()));
    }

    private JRDesignBand createSubreportBand(String prefix, int columnWidth) {
        JRDesignBand band = emptyBand(SUBREPORT_HEIGHT);

        band.addElement(createSubreport(columnWidth,
                                        "$P{" + prefix + MAP_PARAMETER_SUFFIX + "}",
                                        "$P{" + prefix + REPORT_SUFFIX + "}"));
        return band;
    }

    private void injectSubreportListComponents(JasperDesign design, List<JrxmlParameter> fields) {
        List<JrxmlParameter> listFields = fields.stream()
                                                .filter(f -> f.dataset() != null
                                                             && f.subreportPrefix() != null)
                                                .toList();
        if (listFields.isEmpty()) {
            return;
        }

        JRDesignSection detailSection = (JRDesignSection) design.getDetailSection();
        int columnWidth = design.getColumnWidth();

        for (JrxmlParameter field: listFields) {
            if (collectionComponentExists(detailSection, field.dataset().name())) {
                note("Subreport list already exists - skipping: " + field.name());
                continue;
            }

            JRDesignBand band = emptyBand(SUBREPORT_HEIGHT);
            band.addElement(createSubreportListComponent(field, columnWidth));
            detailSection.addBand(band);

            note("Injected subreport list: " + field.subreportPrefix());
        }
    }

    private JRDesignComponentElement createSubreportListComponent(JrxmlParameter field, int width) {
        DesignListContents contents = new DesignListContents();
        contents.setHeight(SUBREPORT_HEIGHT);
        contents.setWidth(width);
        contents.addElement(createSubreport(
                width,
                "$F{" + JasperModularDataFiller.SUBREPORT_PARAMS_FIELD + "}",
                "$F{" + JasperModularDataFiller.SUBREPORT_REPORT_FIELD + "}"));

        StandardListComponent listComponent = new StandardListComponent();
        listComponent.setDatasetRun(buildDatasetRun(field));
        listComponent.setContents(contents);

        return componentElement(listComponent, width, SUBREPORT_HEIGHT, "list");
    }

    private void applyComponentKeyIfNeeded(JRDesignComponentElement element,
                                           String componentName) {
        try {
            Class<?> keyClass = Class.forName("net.sf.jasperreports.engine.component.ComponentKey");
            Constructor<?> constructor = keyClass.getConstructor(String.class,
                                                                 String.class,
                                                                 String.class);
            Object key = constructor.newInstance("http://jasperreports.sourceforge.net/jasperreports/components",
                                                 "jr",
                                                 componentName);
            Method setter = element.getClass().getMethod("setComponentKey", keyClass);
            setter.invoke(element, key);
        } catch (ClassNotFoundException ignored) {
            // JR7: ComponentKey removed, namespace inferred automatically - expected path.
        } catch (ReflectiveOperationException e) {
            warn("Could not set ComponentKey for " + componentName
                                  + ": " + e.getMessage());
        }
    }

    private void note(String message) {
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    private void warn(String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message);
    }
}
