package com.chaykin.jasper.processor.model;

/**
 * A single JasperReports parameter to inject into a JRXML template.
 *
 * @param dataset         dataset descriptor for collection parameters; {@code null} otherwise
 * @param subreportPrefix prefix for a {@code <prefix>Report} subreport-template parameter; {@code null} otherwise
 */
public record JrxmlParameter(String name,
                             String jrxmlClass,
                             JrxmlDataset dataset,
                             String subreportPrefix) {

    /**
     * Convenience constructor for scalar and collection parameters (no subreport prefix).
     */
    public JrxmlParameter(String name, String jrxmlClass, JrxmlDataset dataset) {
        this(name, jrxmlClass, dataset, null);
    }

}
