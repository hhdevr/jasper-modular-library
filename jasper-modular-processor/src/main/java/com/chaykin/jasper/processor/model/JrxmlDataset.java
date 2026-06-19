package com.chaykin.jasper.processor.model;

import com.chaykin.jasper.core.annotation.CollectionComponentType;

import java.util.List;

/**
 * Describes a JasperReports sub-dataset to be injected into a JRXML template.
 *
 * @param componentType the JRXML component type to generate ({@code list} or {@code table})
 * @param columnWidth   the pixel width of each generated column
 */
public record JrxmlDataset(String name,
                           List<JrxmlDatasetField> fields,
                           CollectionComponentType componentType,
                           int columnWidth) {

}
