package com.chaykin.jasper.core.model;

import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.contract.JasperModularDataFiller;

public abstract class SubreportModule
        extends JasperModularDataFiller
        implements JasperModularCompiler {

    public abstract Class<?> getRootReport();

    public abstract boolean isEmpty();

    int getOrder() {
        return 0;
    }

    boolean isStartNewPage() {
        return false;
    }

}
