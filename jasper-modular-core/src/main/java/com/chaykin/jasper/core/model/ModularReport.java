package com.chaykin.jasper.core.model;

import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;

/**
 * Base class for all root JasperReports report modules.
 *
 * @see com.chaykin.jasper.core.annotation.JasperModularReport
 */
public abstract class ModularReport extends JasperModularDataFiller implements JasperModularCompiler {

    protected ModularReport() {
    }
}
