package com.chaykin.jasper.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the jasper-modular Spring Boot autoconfiguration.
 *
 * <p>All properties are bound from the {@code jasper.modular} prefix in
 * {@code application.yml} or {@code application.properties}.</p>
 *
 * <h2>Example configuration</h2>
 * <pre>{@code
 * jasper:
 *   modular:
 *     precompile-enabled: true
 *     base-package: com.example.reports
 * }</pre>
 */
@ConfigurationProperties(prefix = "jasper.modular")
public class JasperModularProperties {

    /**
     * Whether to precompile all discovered JRXML templates at application startup.
     *
     * <p>When {@code true} (the default), {@code JasperReportPrecompiler} scans the
     * {@link #basePackage} for classes annotated with
     * {@code @JasperModularReport} and {@code @JasperSubreport},
     * compiles each template, and stores the result in the shared cache.
     * This eliminates compilation latency on the first report request.</p>
     *
     * <p>Set to {@code false} to disable precompilation, e.g. in test environments
     * where report templates may not be present.</p>
     */
    private boolean precompileEnabled = true;

    /**
     * The base package to scan for report and subreport classes during precompilation.
     *
     * <p>Must be set to a package that contains your {@code @JasperModularReport} and
     * {@code @JasperSubreport} annotated classes. An empty string causes no scanning
     * and triggers a warning at startup.</p>
     *
     * <p>Example: {@code com.example.reports}</p>
     */
    private String basePackage = "";

    /**
     * Returns whether JRXML precompilation is enabled at startup.
     *
     * @return {@code true} if precompilation is enabled (default), {@code false} otherwise
     */
    public boolean isPrecompileEnabled() {return precompileEnabled;}

    /**
     * Sets whether JRXML precompilation is enabled at startup.
     *
     * @param precompileEnabled {@code true} to enable, {@code false} to disable
     */
    public void setPrecompileEnabled(boolean precompileEnabled) {
        this.precompileEnabled = precompileEnabled;
    }

    /**
     * Returns the base package used to scan for report classes.
     *
     * @return the base package, or an empty string if not configured
     */
    public String getBasePackage() {return basePackage;}

    /**
     * Sets the base package to scan for report and subreport classes.
     *
     * @param basePackage the fully qualified package name, e.g. {@code com.example.reports}
     */
    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

}
