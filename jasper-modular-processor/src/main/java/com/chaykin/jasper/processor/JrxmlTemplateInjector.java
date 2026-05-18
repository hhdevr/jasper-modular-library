package com.chaykin.jasper.processor;

import com.chaykin.jasper.core.annotation.CollectionComponentType;
import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;
import net.sf.jasperreports.components.list.DesignListContents;
import net.sf.jasperreports.components.list.StandardListComponent;
import net.sf.jasperreports.components.table.DesignCell;
import net.sf.jasperreports.components.table.StandardColumn;
import net.sf.jasperreports.components.table.StandardTable;
import net.sf.jasperreports.engine.JRBand;
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
import net.sf.jasperreports.engine.xml.JRXmlWriter;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Injects missing elements derived from annotated class fields into a {@link JasperDesign}
 * and writes the result to an output stream.
 *
 * <p>This class is used exclusively by {@link JrxmlGeneratorProcessor} at compile time.
 * It uses the JasperReports native {@link JasperDesign} API rather than raw XML
 * manipulation, which ensures correct element ordering and valid JRXML output.</p>
 *
 * <h2>Injection order</h2>
 * <ol>
 *   <li>Datasets — {@code <dataset>} elements for each collection field</li>
 *   <li>Parameters — {@code <parameter>} elements for all fields</li>
 *   <li>Collection components — {@code list} or {@code table} component in the detail
 *       section for each collection field with a dataset</li>
 *   <li>Subreport bands — {@code <band>} elements containing a subreport element for
 *       each subreport field</li>
 * </ol>
 *
 * <p>All injections are idempotent: existing elements are detected by name and skipped,
 * so re-running the processor after adding new fields only adds the genuinely new
 * elements without disturbing user-defined design.</p>
 *
 * <h2>JasperReports 6.x compatibility</h2>
 * <p>In JR6, {@code JRXmlWriter} requires a {@code ComponentKey} to be set on every
 * list/table component element in order to resolve the XML namespace during serialization.
 * In JR7 the {@code ComponentKey} class was removed. This class detects the presence of
 * {@code ComponentKey} at runtime via reflection and applies it when available, so the
 * same compiled jar works with both JR6 and JR7.</p>
 */
public class JrxmlTemplateInjector {

    private static final int LIST_HEIGHT = 90;

    private static final int CELL_HEIGHT = 30;

    private static final int HEADER_HEIGHT = 20;

    private static final int SUBREPORT_HEIGHT = 100;

    private static final int SUBREPORT_WIDTH = 555;

    private final Messager messager;

    public JrxmlTemplateInjector(Messager messager) {
        this.messager = messager;
    }

    /**
     * Injects all missing elements into the design and writes the result.
     *
     * @param design the report design to inject into
     * @param fields the list of field descriptors derived from the annotated class
     * @param output the output stream to write the updated JRXML to
     * @throws Exception if injection or serialization fails
     */
    public void inject(JasperDesign design,
                       List<JrxmlParameter> fields,
                       OutputStream output) throws Exception {
        injectDatasets(design, fields);
        injectParameters(design, fields);
        injectCollectionComponents(design, fields);
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

    private void injectCollectionComponents(JasperDesign design, List<JrxmlParameter> fields)
            throws JRException {
        List<JrxmlParameter> collectionFields = fields.stream()
                                                      .filter(f -> f.dataset() != null)
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

            JRDesignBand band = getOrCreateLastBand(detailSection);

            if (field.dataset().componentType() == CollectionComponentType.TABLE) {
                band.addElement(createTableComponent(field));
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Injected table component: " + field.name());
            } else {
                band.addElement(createListComponent(field));
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Injected list component: " + field.name());
            }
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

    private JRDesignBand getOrCreateLastBand(JRDesignSection section) throws JRException {
        JRBand[] bands = section.getBands();
        if (bands.length > 0) {
            return (JRDesignBand) bands[bands.length - 1];
        }
        JRDesignBand band = new JRDesignBand();
        band.setHeight(LIST_HEIGHT);
        section.addBand(band);
        return band;
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

    private void injectSubreportBands(JasperDesign design, List<JrxmlParameter> fields)
            throws JRException {
        List<String> subreportPrefixes = fields.stream()
                                               .filter(f -> "net.sf.jasperreports.engine.JasperReport"
                                                       .equals(f.jrxmlClass()))
                                               .map(f -> f.name().replace("Report", ""))
                                               .toList();

        if (subreportPrefixes.isEmpty()) {
            return;
        }

        JRDesignSection detailSection = (JRDesignSection) design.getDetailSection();

        for (String prefix: subreportPrefixes) {
            if (subreportBandExists(detailSection, prefix)) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Subreport band already exists - skipping: " + prefix);
                continue;
            }
            detailSection.addBand(createSubreportBand(prefix));
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Injected subreport band: " + prefix);
        }
    }

    private boolean subreportBandExists(JRDesignSection section, String prefix) {
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignSubreport)
                     .map(e -> (JRDesignSubreport) e)
                     .anyMatch(sr -> sr.getExpression() != null
                                     && sr.getExpression().getText().contains(prefix + "Report"));
    }

    private JRDesignBand createSubreportBand(String prefix) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(SUBREPORT_HEIGHT);
        band.setSplitType(net.sf.jasperreports.engine.type.SplitTypeEnum.STRETCH);

        JRDesignSubreport subreport = new JRDesignSubreport(null);
        subreport.setX(0);
        subreport.setY(0);
        subreport.setWidth(SUBREPORT_WIDTH);
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

    /**
     * Sets {@code ComponentKey} on the element via reflection when running under
     * JasperReports 6.x. In JR6 {@code JRXmlWriter} requires an explicit
     * {@code ComponentKey} to resolve the XML namespace for list/table components;
     * in JR7 the class was removed and the namespace is inferred automatically.
     */
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
            // JR7: ComponentKey removed, namespace inferred automatically
        } catch (ReflectiveOperationException e) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                                  "Could not set ComponentKey for " + componentName
                                  + ": " + e.getMessage());
        }
    }
}
