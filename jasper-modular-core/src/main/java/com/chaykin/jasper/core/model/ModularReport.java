package com.chaykin.jasper.core.model;

import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;

/**
 * Base class for all root JasperReports report modules.
 *
 * <p>A root report is the top-level document that may embed one or more subreports.
 * Concrete report classes must:</p>
 * <ol>
 *   <li>Extend {@code ModularReport}</li>
 *   <li>Be annotated with {@link com.chaykin.jasper.core.annotation.JasperModularReport}</li>
 *   <li>Declare fields for all data that should appear in the report, including fields
 *       of subreport types annotated with
 *       {@link com.chaykin.jasper.core.annotation.JasperSubreport}</li>
 * </ol>
 *
 * <p>This class composes {@link JasperModularDataFiller} (for building the parameters map)
 * and {@link JasperModularCompiler} (for compiling and caching the JRXML template).
 * Both concerns are handled automatically - no manual override is required.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JasperModularReport(templatePath = "/reports/invoice.jrxml")
 * public class InvoiceReport extends ModularReport {
 *
 *     private String customerName;
 *     private BigDecimal totalAmount;
 *     private LineItemsModule lineItems; // @JasperSubreport annotated
 * }
 * }</pre>
 *
 * @see com.chaykin.jasper.core.annotation.JasperModularReport
 * @see SubreportModule
 * @see com.chaykin.jasper.core.renderer.JasperModularRenderer
 */
public abstract class ModularReport extends JasperModularDataFiller implements JasperModularCompiler {

    /**
     * Creates a new {@code ModularReport} instance.
     * Subclasses do not need to call this constructor explicitly.
     */
    protected ModularReport() {
    }
}
