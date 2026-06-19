package com.chaykin.jasper.core.model;

import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;

/**
 * Base class for a self-contained subreport module rendered within a modular report.
 *
 * @see com.chaykin.jasper.core.annotation.JasperSubreport
 */
public abstract class SubreportModule
        extends JasperModularDataFiller
        implements JasperModularCompiler {

    protected SubreportModule() {
    }

    /**
     * Returns {@code true} if this subreport has nothing to render, in which case the data
     * filler skips it entirely.
     */
    public abstract boolean isEmpty();

    /**
     * Returns the rendering order relative to sibling subreports; lower values render first.
     *
     * @deprecated Not yet consulted by the renderer; control ordering via field declaration
     * order and the JRXML layout instead.
     */
    @Deprecated(forRemoval = true)
    public int getOrder() {
        return 0;
    }

    /**
     * Returns whether this subreport should force a page break before rendering.
     *
     * @deprecated Not yet consulted by the renderer; add a page break in the JRXML template
     * instead.
     */
    @Deprecated(forRemoval = true)
    public boolean isStartNewPage() {
        return false;
    }
}
