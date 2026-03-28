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
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a JRXML template into a {@link JasperDesign} object, injects missing elements
 * derived from annotated class fields, and writes the result back to an output stream.
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
 */
public class JrxmlTemplateInjector {

    /**
     * Default height of the collection component wrapper band, in pixels.
     */
    private static final int LIST_HEIGHT = 90;

    /**
     * Default height of each row in the list component contents, in pixels.
     */
    private static final int CELL_HEIGHT = 30;

    /**
     * Default height of the table header cell, in pixels.
     */
    private static final int HEADER_HEIGHT = 20;

    /**
     * Default height of the subreport element within its band, in pixels.
     */
    private static final int SUBREPORT_HEIGHT = 100;

    /**
     * Default width of the subreport element, matching a standard A4 column width.
     */
    private static final int SUBREPORT_WIDTH = 555;

    private final Messager messager;

    /**
     * Creates a new injector with the given compiler messager for logging.
     *
     * @param messager the compiler messager used to emit notes during injection
     */
    public JrxmlTemplateInjector(Messager messager) {
        this.messager = messager;
    }

    /**
     * Loads the template, injects all missing elements, and writes the result.
     *
     * @param template the source JRXML template input stream
     * @param fields   the list of field descriptors derived from the annotated class
     * @param output   the output stream to write the updated JRXML to
     * @throws Exception if loading, injection, or serialization fails
     */
    public void inject(InputStream template,
                       List<JrxmlParameter> fields,
                       OutputStream output) throws Exception {
        JasperDesign design = JRXmlLoader.load(template);

        injectDatasets(design, fields);
        injectParameters(design, fields);
        injectCollectionComponents(design, fields);
        injectSubreportBands(design, fields);

        JRXmlWriter.writeReport(design, output, "UTF-8");
    }

    /**
     * Injects missing {@code <parameter>} elements into the design.
     *
     * <p>Parameters that already exist in the design (detected by name) are skipped
     * and a note is logged.</p>
     *
     * @param design the report design to inject into
     * @param fields the field descriptors to inject as parameters
     * @throws JRException if adding a parameter to the design fails
     */
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

    /**
     * Injects missing {@code <dataset>} elements into the design.
     *
     * <p>Only fields that carry a non-null {@link com.chaykin.jasper.processor.model.JrxmlDataset}
     * are processed. Datasets that already exist are skipped.</p>
     *
     * @param design the report design to inject into
     * @param fields the field descriptors; only those with datasets are processed
     * @throws JRException if adding a dataset to the design fails
     */
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

    /**
     * Injects missing collection component elements into the detail section.
     *
     * <p>For each collection field with a dataset, delegates to
     * {@link #createListComponent} or {@link #createTableComponent} depending on
     * {@link com.chaykin.jasper.processor.model.JrxmlDataset#componentType()}.
     * Existing components are detected by dataset name via
     * {@link #collectionComponentExists} and skipped.</p>
     *
     * @param design the report design to inject into
     * @param fields the field descriptors; only collection fields with datasets are used
     * @throws JRException if modifying the design fails
     */
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

    /**
     * Returns {@code true} if a list or table component referencing the given dataset name
     * already exists in the detail section.
     *
     * <p>Detection is based on {@code datasetName} — a direct string attribute on the
     * dataset run that survives JRXML serialization and deserialization reliably.</p>
     *
     * @param section     the detail section to search
     * @param datasetName the dataset name to look for in the component's dataset run
     * @return {@code true} if the component is already present
     */
    private boolean collectionComponentExists(JRDesignSection section, String datasetName) {
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignComponentElement)
                     .map(e -> (JRDesignComponentElement) e)
                     .anyMatch(e -> {
                         if (e.getComponent() instanceof StandardListComponent lc) {
                             JRDesignDatasetRun run =
                                     (JRDesignDatasetRun) lc.getDatasetRun();
                             return run != null
                                    && datasetName.equals(run.getDatasetName());
                         }
                         if (e.getComponent() instanceof StandardTable tbl) {
                             JRDesignDatasetRun run =
                                     (JRDesignDatasetRun) tbl.getDatasetRun();
                             return run != null
                                    && datasetName.equals(run.getDatasetName());
                         }
                         return false;
                     });
    }

    /**
     * Returns the last band in the detail section, creating a new one if none exists.
     *
     * @param section the detail section
     * @return an existing or newly created band
     * @throws JRException if adding a new band fails
     */
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

    /**
     * Creates a {@code list} component element for the given collection field.
     *
     * <p>The total width equals {@code fieldCount * columnWidth}. Each dataset field
     * gets one text field column. The data source expression references the parameter
     * by name: {@code $P{fieldName}}.</p>
     *
     * @param field the collection field descriptor
     * @return the constructed list component element
     */
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
        return element;
    }

    /**
     * Creates a {@code table} component element for the given collection field.
     *
     * <p>The table contains one column per dataset field. Each column has a header cell
     * showing the field name as a static label and a detail cell showing the field value.
     * The total width equals {@code fieldCount * columnWidth}.</p>
     *
     * @param field the collection field descriptor
     * @return the constructed table component element
     */
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

            // header cell — static text with field name
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

            // detail cell — text field with $F{fieldName}
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
        return element;
    }

    /**
     * Builds a {@link JRDesignDatasetRun} for the given collection field, referencing
     * the field's dataset by name and setting the data source expression to
     * {@code $P{fieldName}}.
     *
     * @param field the collection field descriptor
     * @return the configured dataset run
     */
    private JRDesignDatasetRun buildDatasetRun(JrxmlParameter field) {
        JRDesignDatasetRun datasetRun = new JRDesignDatasetRun();
        datasetRun.setDatasetName(field.dataset().name());
        JRDesignExpression dsExpr = new JRDesignExpression();
        dsExpr.setText("$P{" + field.name() + "}");
        datasetRun.setDataSourceExpression(dsExpr);
        return datasetRun;
    }

    /**
     * Injects missing subreport bands into the detail section of the design.
     *
     * <p>Each subreport field produces one new band containing a subreport element.
     * The subreport receives its data via {@code $P{<prefix>MapParameter}} and its
     * compiled template via {@code $P{<prefix>Report}}. Existing subreport bands are
     * detected by checking whether any subreport expression contains the prefix.</p>
     *
     * @param design the report design to inject into
     * @param fields the field descriptors; only subreport fields are processed
     * @throws JRException if modifying the design fails
     */
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

    /**
     * Returns {@code true} if a subreport element referencing the given prefix already
     * exists in the detail section.
     *
     * @param section the detail section to search
     * @param prefix  the subreport prefix to look for
     * @return {@code true} if the subreport band is already present
     */
    private boolean subreportBandExists(JRDesignSection section, String prefix) {
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignSubreport)
                     .map(e -> (JRDesignSubreport) e)
                     .anyMatch(sr -> sr.getExpression() != null
                                     && sr.getExpression()
                                          .getText()
                                          .contains(prefix + "Report"));
    }

    /**
     * Creates a band containing a subreport element for the given prefix.
     *
     * <p>The subreport element is configured with:</p>
     * <ul>
     *   <li>{@code positionType = FLOAT} - allows the band to shrink when empty</li>
     *   <li>{@code removeLineWhenBlank = true} - collapses the band if nothing renders</li>
     *   <li>{@code splitType = STRETCH} - stretches to fit the subreport content</li>
     *   <li>Parameters map: {@code $P{<prefix>MapParameter}}</li>
     *   <li>Data source: {@code new JREmptyDataSource()}</li>
     *   <li>Subreport expression: {@code $P{<prefix>Report}}</li>
     * </ul>
     *
     * @param prefix the subreport prefix, e.g. {@code Revenue}
     * @return the constructed band with a subreport element
     * @throws JRException if creating the band fails
     */
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
}
