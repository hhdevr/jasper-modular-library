package com.chaykin.jasper.processor.model;

/**
 * Represents a single JasperReports parameter to be injected into a JRXML template.
 *
 * <p>This record is produced by the annotation processor during compile-time analysis
 * of report and subreport class fields. Each parameter corresponds to one
 * {@code <parameter>} element in the generated or updated JRXML file.</p>
 *
 * @param name            the parameter name as it will appear in the JRXML
 * @param jrxmlClass      the fully qualified, erased Java class name of the value type
 * @param dataset         the dataset descriptor for collection parameters; {@code null} otherwise
 * @param subreportPrefix the prefix for a {@code <prefix>Report} subreport-template parameter;
 *                        {@code null} for all other parameters
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
