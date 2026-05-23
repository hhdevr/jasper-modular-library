package com.chaykin.jasper.core.renderer;

import com.chaykin.jasper.core.exception.JasperModularException;
import com.chaykin.jasper.core.model.ModularReport;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

import java.util.Map;

/**
 * Fills a {@link ModularReport} with data and returns a {@link JasperPrint} ready for export.
 *
 * <p>This class bridges the data model and the JasperReports engine. It performs two steps:</p>
 * <ol>
 *   <li>Compiles (or retrieves from cache) the JRXML template via
 *       {@link ModularReport#compileReport()}</li>
 *   <li>Fills the compiled template with parameters collected from the report's fields via
 *       {@link ModularReport#fillMapParameters()}</li>
 * </ol>
 *
 * <p>The returned {@link JasperPrint} is a format-neutral in-memory representation of the
 * rendered report. Callers are responsible for exporting it to the desired output format
 * using the JasperReports exporter of their choice. This design keeps the library free of
 * format-specific dependencies - add only the exporter you need to your project.</p>
 *
 * <h2>PDF export example</h2>
 * <pre>{@code
 * JasperPrint print = new JasperModularRenderer().render(myReport);
 *
 * ByteArrayOutputStream out = new ByteArrayOutputStream();
 * JRPdfExporter exporter = new JRPdfExporter();
 * exporter.setExporterInput(new SimpleExporterInput(print));
 * exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
 * exporter.exportReport();
 *
 * byte[] pdf = out.toByteArray();
 * }</pre>
 *
 * @see ModularReport
 * @see JasperPrint
 */
public class JasperModularRenderer {

    /**
     * Compiles the report template and fills it with data from the given module.
     *
     * <p>The root data source argument to {@code JasperFillManager.fillReport()} is always
     * {@link JREmptyDataSource} — the library does not use the band-iteration data source.
     * All data travels through the parameters map built by
     * {@link ModularReport#fillMapParameters()}: scalar fields as plain values, collections
     * as {@code JRBeanCollectionDataSource} <em>parameters</em> (accessible in JRXML via
     * {@code $P{fieldName}} in a list or table {@code <dataSourceExpression>}), and
     * subreport modules as compiled template + nested parameters map pairs.</p>
     *
     * @param module the populated report module to render
     * @return a filled {@link JasperPrint} ready for export to any supported format
     * @throws JasperModularException if template compilation fails, the JRXML is not found,
     *                                or report filling encounters an error
     */
    public JasperPrint render(ModularReport module) throws JasperModularException {
        try {
            JasperReport jasperReport = module.compileReport();
            Map<String, Object> parameters = module.fillMapParameters();

            return JasperFillManager.fillReport(jasperReport,
                                                parameters,
                                                new JREmptyDataSource());
        } catch (Exception e) {
            throw new JasperModularException(
                    "Error while rendering report: " + module.getModuleClassName(), e);
        }
    }
}
