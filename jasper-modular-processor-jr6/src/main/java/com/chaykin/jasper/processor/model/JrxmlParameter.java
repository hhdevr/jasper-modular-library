package com.chaykin.jasper.processor.model;

/**
 * Represents a single JasperReports parameter to be injected into a JRXML template.
 *
 * <p>This record is produced by the annotation processor during compile-time analysis
 * of report and subreport class fields. Each parameter corresponds to one
 * {@code <parameter>} element in the generated or updated JRXML file.</p>
 *
 * @param name       the parameter name as it will appear in the JRXML
 * @param jrxmlClass the fully qualified Java class name of the parameter's value type,
 *                   e.g. {@code "java.lang.String"} or
 *                   {@code "net.sf.jasperreports.engine.data.JRBeanCollectionDataSource"}
 * @param dataset    the dataset descriptor if this parameter holds a collection data source;
 *                   {@code null} for scalar parameters
 */
public record JrxmlParameter(String name,
                             String jrxmlClass,
                             JrxmlDataset dataset) {

}
