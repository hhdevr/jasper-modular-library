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

}
