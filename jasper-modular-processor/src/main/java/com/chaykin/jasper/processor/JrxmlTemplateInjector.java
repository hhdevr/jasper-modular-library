package com.chaykin.jasper.processor;

import com.chaykin.jasper.processor.model.JrxmlDatasetField;
import com.chaykin.jasper.processor.model.JrxmlParameter;
import net.sf.jasperreports.components.list.DesignListContents;
import net.sf.jasperreports.components.list.ListComponent;
import net.sf.jasperreports.components.list.StandardListComponent;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignComponentElement;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignDatasetRun;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignSection;
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

public class JrxmlTemplateInjector {

    private static final int COLUMN_WIDTH = 120;
    private static final int LIST_HEIGHT = 90;
    private static final int CELL_HEIGHT = 30;
    private static final int SUBREPORT_HEIGHT = 94;
    private static final int SUBREPORT_BAND_HEIGHT = 100;
    private static final int SUBREPORT_WIDTH = 555;

    private final Messager messager;

    public JrxmlTemplateInjector(Messager messager) {
        this.messager = messager;
    }

    public void inject(InputStream template,
                       List<JrxmlParameter> fields,
                       OutputStream output) throws Exception {
        JasperDesign design = JRXmlLoader.load(template);

        injectDatasets(design, fields);
        injectParameters(design, fields);
        injectListComponents(design, fields);
        injectSubreportBands(design, fields);

        JRXmlWriter.writeReport(design, output, "UTF-8");
    }

    private void injectParameters(JasperDesign design, List<JrxmlParameter> fields) throws JRException {
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

    private void injectDatasets(JasperDesign design, List<JrxmlParameter> fields) throws JRException {
        for (JrxmlParameter field: fields) {
            if (field.dataset() == null) {
                continue;
            }

            if (design.getDatasetMap().containsKey(field.dataset().name())) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "Dataset already exists - skipping: " + field.dataset().name());
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

    private void injectListComponents(JasperDesign design, List<JrxmlParameter> fields) throws JRException {
        List<JrxmlParameter> collectionFields = fields.stream()
                                                      .filter(f -> f.dataset() != null)
                                                      .toList();
        if (collectionFields.isEmpty()) {
            return;
        }

        JRDesignSection detailSection = (JRDesignSection) design.getDetailSection();

        for (JrxmlParameter field: collectionFields) {
            if (listComponentExists(detailSection, field.name())) {
                messager.printMessage(Diagnostic.Kind.NOTE,
                                      "List component already exists - skipping: " + field.name());
                continue;
            }

            JRDesignBand band = getOrCreateLastBand(detailSection);
            band.addElement(createListComponent(design, field));
            messager.printMessage(Diagnostic.Kind.NOTE,
                                  "Injected list component: " + field.name());
        }
    }

    private boolean listComponentExists(JRDesignSection section, String paramName) {
        return Arrays.stream(section.getBands())
                     .flatMap(b -> Arrays.stream(b.getElements()))
                     .filter(e -> e instanceof JRDesignComponentElement)
                     .map(e -> (JRDesignComponentElement) e)
                     .filter(e -> e.getComponent() instanceof ListComponent)
                     .map(e -> (StandardListComponent) e.getComponent())
                     .anyMatch(lc -> {
                         JRDesignDatasetRun run = (JRDesignDatasetRun) lc.getDatasetRun();
                         return run != null &&
                                run.getDataSourceExpression() != null &&
                                run.getDataSourceExpression().getText().contains(paramName);
                     });
    }

    private JRDesignBand getOrCreateLastBand(JRDesignSection section) throws JRException {
        JRDesignBand[] bands = (JRDesignBand[]) section.getBands();
        if (bands.length > 0) {
            return bands[bands.length - 1];
        }
        JRDesignBand band = new JRDesignBand();
        band.setHeight(LIST_HEIGHT);
        section.addBand(band);
        return band;
    }

    private JRDesignComponentElement createListComponent(JasperDesign design, JrxmlParameter field) {
        List<JrxmlDatasetField> datasetFields = field.dataset().fields();
        int columnCount = datasetFields.size();
        int totalWidth = columnCount * COLUMN_WIDTH;

        JRDesignDatasetRun datasetRun = new JRDesignDatasetRun();
        datasetRun.setDatasetName(field.dataset().name());
        JRDesignExpression dsExpr = new JRDesignExpression();
        dsExpr.setText("$P{" + field.name() + "}");
        datasetRun.setDataSourceExpression(dsExpr);

        DesignListContents contents = new DesignListContents();
        contents.setHeight(CELL_HEIGHT);
        contents.setWidth(totalWidth);

        int x = 0;
        for (JrxmlDatasetField datasetField: datasetFields) {
            JRDesignTextField textField = new JRDesignTextField();
            textField.setX(x);
            textField.setY(0);
            textField.setWidth(COLUMN_WIDTH);
            textField.setHeight(CELL_HEIGHT);

            JRDesignExpression expr = new JRDesignExpression();
            expr.setText("$F{" + datasetField.name() + "}");
            textField.setExpression(expr);
            contents.addElement(textField);
            x += COLUMN_WIDTH;
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

    private void injectSubreportBands(JasperDesign design, List<JrxmlParameter> fields) throws JRException {
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
                     .anyMatch(sr -> sr.getExpression() != null &&
                                     sr.getExpression().getText().contains(prefix + "Report"));
    }

    private JRDesignBand createSubreportBand(String prefix) throws JRException {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(SUBREPORT_BAND_HEIGHT);
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