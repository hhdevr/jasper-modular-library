package com.chaykin.jasper.processor.model;

/**
 * Describes a single {@code <field>} within a JasperReports sub-dataset.
 *
 * @param jrxmlClass the fully qualified Java class name of the field's type
 */
public record JrxmlDatasetField(String name,
                                String jrxmlClass) {

}
