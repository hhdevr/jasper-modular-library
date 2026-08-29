package com.chaykin.jasper.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties bound from the {@code jasper.modular} prefix.
 */
@ConfigurationProperties(prefix = "jasper.modular")
public class JasperModularProperties {

    /** Whether to precompile discovered JRXML templates at startup. */
    private boolean precompileEnabled = true;

    /** Base package scanned for report and subreport classes; empty disables scanning. */
    private String basePackage = "";

    public boolean isPrecompileEnabled() {return precompileEnabled;}

    public void setPrecompileEnabled(boolean precompileEnabled) {
        this.precompileEnabled = precompileEnabled;
    }

    public String getBasePackage() {return basePackage;}

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

}
