package com.chaykin.jasper.processor.model;

/**
 * Describes a single field within a JasperReports sub-dataset.
 *
 * <p>Each instance corresponds to one {@code <field>} element inside a {@code <dataset>}
 * in the generated JRXML. Fields are derived from the declared fields of the collection's
 * element class during annotation processing.</p>
 *
 * @param name       the field name as it will appear in the JRXML, matching the bean
 *                   property name
 * @param jrxmlClass the fully qualified Java class name of the field's type,
 *                   e.g. {@code "java.lang.String"} or {@code "java.lang.Double"}
 */
public record JrxmlDatasetField(String name,
                                String jrxmlClass) {

}
