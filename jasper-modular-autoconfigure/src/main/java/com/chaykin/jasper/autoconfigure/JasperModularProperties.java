package com.chaykin.jasper.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "jasper.modular")
public class JasperModularProperties {

    private boolean precompileEnabled = true;

    private String basePackage = "";

    public boolean isPrecompileEnabled() { return precompileEnabled; }
    public void setPrecompileEnabled(boolean precompileEnabled) {
        this.precompileEnabled = precompileEnabled;
    }

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }
}