package com.chaykin.jasper.core.model;

import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;

/**
 * Base class for all JasperReports subreport modules.
 *
 * <p>A subreport module represents a self-contained section of a larger report document.
 * It has its own JRXML template and its own set of parameters, but is rendered as part
 * of a root report declared with
 * {@link com.chaykin.jasper.core.annotation.JasperModularReport}.</p>
 *
 * <p>Concrete subreport classes must:</p>
 * <ol>
 *   <li>Extend {@code SubreportModule}</li>
 *   <li>Be annotated with {@link com.chaykin.jasper.core.annotation.JasperSubreport}</li>
 *   <li>Implement {@link #isEmpty()} to signal whether the subreport has renderable content</li>
 * </ol>
 *
 * <p>When the root report's data filler encounters a field whose type is a
 * {@code SubreportModule}, it automatically compiles the subreport's template and
 * recursively fills its parameters, injecting them into the root report under the names
 * {@code <prefix>Report} and {@code <prefix>MapParameter}.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JasperSubreport(templatePath = "/reports/sub_items.jrxml", prefix = "Items")
 * public class ItemsModule extends SubreportModule {
 *
 *     private List<Item> items;
 *     private BigDecimal subtotal;
 *
 *     @Override
 *     public boolean isEmpty() { return items == null || items.isEmpty(); }
 * }
 * }</pre>
 *
 * @see com.chaykin.jasper.core.annotation.JasperSubreport
 * @see ModularReport
 */
public abstract class SubreportModule
        extends JasperModularDataFiller
        implements JasperModularCompiler {

    /**
     * Creates a new {@code SubreportModule} instance.
     * Subclasses do not need to call this constructor explicitly.
     */
    protected SubreportModule() {
    }

    /**
     * Returns {@code true} if this subreport contains no renderable data.
     *
     * <p>Implementations should check whether the primary data collection or value
     * that drives this subreport's content is absent or empty.</p>
     *
     * @return {@code true} if there is nothing to render; {@code false} otherwise
     */
    public abstract boolean isEmpty();

    /**
     * Returns the rendering order of this subreport relative to siblings in the same
     * root report. Lower values are rendered first. Defaults to {@code 0}.
     *
     * @return the relative order of this subreport
     */
    public int getOrder() {
        return 0;
    }

    /**
     * Returns whether this subreport should start on a new page in the rendered document.
     * Defaults to {@code false}.
     *
     * @return {@code true} if this subreport should force a page break before rendering
     */
    public boolean isStartNewPage() {
        return false;
    }
}
