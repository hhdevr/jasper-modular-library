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
import net.sf.jasperreports.engine.JRException;
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
     * @throws Exception if injection or serialization fails
     */
    public void inject(JasperDesign design,
                       List<JrxmlParameter> fields,
                       OutputStream output) throws Exception {
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
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Parameter already exists - skipping: " + field.name());
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
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Dataset already exists - skipping: "
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
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "List component already exists - skipping: "
                                      + field.name());
                continue;
            }

            JRDesignBand band = new JRDesignBand();
            band.setHeight(LIST_HEIGHT);
            band.setSplitType(SplitTypeEnum.STRETCH);

            if (field.dataset().componentType() == CollectionComponentType.TABLE) {
                band.addElement(createTableComponent(field));
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Injected table component: " + field.name());
            } else {
                band.addElement(createListComponent(field));
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Injected list component: " + field.name());
            }

            detailSection.addBand(band);
        }
    }

    private boolean collectionComponentExists(JRDesignSection section, String datasetName) {
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignComponentElement)
                     .map(e -> (JRDesignComponentElement) e)
                     .anyMatch(e -> {
                         if (e.getComponent() instanceof StandardListComponent lc) {
                             JRDesignDatasetRun run = (JRDesignDatasetRun) lc.getDatasetRun();
                             return run != null && datasetName.equals(run.getDatasetName());
                         }
                         if (e.getComponent() instanceof StandardTable tbl) {
                             JRDesignDatasetRun run = (JRDesignDatasetRun) tbl.getDatasetRun();
                             return run != null && datasetName.equals(run.getDatasetName());
                         }
                         return false;
                     });
    }

    private JRDesignComponentElement createListComponent(JrxmlParameter field) {
        List<JrxmlDatasetField> datasetFields = field.dataset().fields();
        int columnWidth = field.dataset().columnWidth();
        int totalWidth = datasetFields.size() * columnWidth;

        JRDesignDatasetRun datasetRun = buildDatasetRun(field);

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
            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{" + datasetField.name() + "}");
            textField.setExpression(expr);
            contents.addElement(textField);
            x += columnWidth;
        }

        StandardListComponent listComponent = new StandardListComponent();
        listComponent.setDatasetRun(datasetRun);
        listComponent.setContents(contents);

        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0);
        element.setY(0);
        element.setWidth(totalWidth);
        element.setHeight(LIST_HEIGHT);
        element.setComponent(listComponent);
        applyComponentKeyIfNeeded(element, "list");
        return element;
    }

    private JRDesignComponentElement createTableComponent(JrxmlParameter field) {
        List<JrxmlDatasetField> datasetFields = field.dataset().fields();
        int columnWidth = field.dataset().columnWidth();
        int totalWidth = datasetFields.size() * columnWidth;

        JRDesignDatasetRun datasetRun = buildDatasetRun(field);

        StandardTable table = new StandardTable();
        table.setDatasetRun(datasetRun);

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
            headerText.setText(datasetField.name());
            headerCell.addElement(headerText);
            column.setColumnHeader(headerCell);

            DesignCell detailCell = new DesignCell();
            detailCell.setHeight(CELL_HEIGHT);
            JRDesignTextField detailTextField = new JRDesignTextField();
            detailTextField.setX(0);
            detailTextField.setY(0);
            detailTextField.setWidth(columnWidth);
            detailTextField.setHeight(CELL_HEIGHT);
            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{" + datasetField.name() + "}");
            detailTextField.setExpression(expr);
            detailCell.addElement(detailTextField);
            column.setDetailCell(detailCell);

            table.addColumn(column);
        }

        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0);
        element.setY(0);
        element.setWidth(totalWidth);
        element.setHeight(HEADER_HEIGHT + CELL_HEIGHT);
        element.setComponent(table);
        applyComponentKeyIfNeeded(element, "table");
        return element;
    }

    private JRDesignDatasetRun buildDatasetRun(JrxmlParameter field) {
        JRDesignDatasetRun datasetRun = new JRDesignDatasetRun();
        datasetRun.setDatasetName(field.dataset().name());
        JRDesignExpression dsExpr = new JRDesignExpression();
        dsExpr.setText("$P{" + field.name() + "}");
        datasetRun.setDataSourceExpression(dsExpr);
        return datasetRun;
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
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Subreport band already exists - skipping: " + prefix);
                continue;
            }
            detailSection.addBand(createSubreportBand(prefix, columnWidth));
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Injected subreport band: " + prefix);
        }
    }

    private boolean subreportBandExists(JRDesignSection section, String prefix) {
        String expectedExpr = "$P{" + prefix + "Report}";
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignSubreport)
                     .map(e -> (JRDesignSubreport) e)
                     .anyMatch(sr -> sr.getExpression() != null
                                     && expectedExpr.equals(sr.getExpression().getText()));
    }

    private JRDesignBand createSubreportBand(String prefix, int columnWidth) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(SUBREPORT_HEIGHT);
        band.setSplitType(SplitTypeEnum.STRETCH);

        JRDesignSubreport subreport = new JRDesignSubreport(null);
        subreport.setX(0);
        subreport.setY(0);
        subreport.setWidth(columnWidth);
        subreport.setHeight(SUBREPORT_HEIGHT);
        subreport.setPositionType(PositionTypeEnum.FLOAT);
        subreport.setRemoveLineWhenBlank(true);

        JRDesignExpression paramsExpr = new JRDesignExpression();
        paramsExpr.setText("$P{" + prefix + "MapParameter}");
        subreport.setParametersMapExpression(paramsExpr);

        JRDesignExpression dsExpr = new JRDesignExpression();
        dsExpr.setText("new net.sf.jasperreports.engine.JREmptyDataSource()");
        subreport.setDataSourceExpression(dsExpr);

        JRDesignExpression expr = new JRDesignExpression();
        expr.setText("$P{" + prefix + "Report}");
        subreport.setExpression(expr);

        band.addElement(subreport);
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
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Subreport list already exists - skipping: " + field.name());
                continue;
            }

            JRDesignBand band = new JRDesignBand();
            band.setHeight(SUBREPORT_HEIGHT);
            band.setSplitType(SplitTypeEnum.STRETCH);
            band.addElement(createSubreportListComponent(field, columnWidth));
            detailSection.addBand(band);

            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Injected subreport list: " + field.subreportPrefix());
        }
    }

    private JRDesignComponentElement createSubreportListComponent(JrxmlParameter field, int width) {
        JRDesignDatasetRun datasetRun = new JRDesignDatasetRun();
        datasetRun.setDatasetName(field.dataset().name());
        JRDesignExpression dsExpr = new JRDesignExpression();
        dsExpr.setText("$P{" + field.name() + "}");
        datasetRun.setDataSourceExpression(dsExpr);

        JRDesignSubreport subreport = new JRDesignSubreport(null);
        subreport.setX(0);
        subreport.setY(0);
        subreport.setWidth(width);
        subreport.setHeight(SUBREPORT_HEIGHT);
        subreport.setPositionType(PositionTypeEnum.FLOAT);
        subreport.setRemoveLineWhenBlank(true);

        JRDesignExpression paramsExpr = new JRDesignExpression();
        paramsExpr.setText("$F{" + JasperModularDataFiller.SUBREPORT_PARAMS_FIELD + "}");
        subreport.setParametersMapExpression(paramsExpr);

        JRDesignExpression emptyDs = new JRDesignExpression();
        emptyDs.setText("new net.sf.jasperreports.engine.JREmptyDataSource()");
        subreport.setDataSourceExpression(emptyDs);

        JRDesignExpression reportExpr = new JRDesignExpression();
        reportExpr.setText("$F{" + JasperModularDataFiller.SUBREPORT_REPORT_FIELD + "}");
        subreport.setExpression(reportExpr);

        DesignListContents contents = new DesignListContents();
        contents.setHeight(SUBREPORT_HEIGHT);
        contents.setWidth(width);
        contents.addElement(subreport);

        StandardListComponent listComponent = new StandardListComponent();
        listComponent.setDatasetRun(datasetRun);
        listComponent.setContents(contents);

        JRDesignComponentElement element = new JRDesignComponentElement();
        element.setX(0);
        element.setY(0);
        element.setWidth(width);
        element.setHeight(SUBREPORT_HEIGHT);
        element.setComponent(listComponent);
        applyComponentKeyIfNeeded(element, "list");
        return element;
    }

    private void applyComponentKeyIfNeeded(JRDesignComponentElement element,
                                           String componentName) {
        try {
            Class<?> keyClass = Class.forName(
                    "net.sf.jasperreports.engine.component.ComponentKey");
            Constructor<?> ctor = keyClass.getConstructor(
                    String.class, String.class, String.class);
            Object key = ctor.newInstance(
                    "http://jasperreports.sourceforge.net/jasperreports/components",
                    "jr",
                    componentName);
            Method setter = element.getClass().getMethod("setComponentKey", keyClass);
            setter.invoke(element, key);
        } catch (ClassNotFoundException ignored) {
            // JR7: ComponentKey removed, namespace inferred automatically - expected path.
        } catch (ReflectiveOperationException e) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                                  "Could not set ComponentKey for " + componentName
                                  + ": " + e.getMessage());
        }
    }
}
