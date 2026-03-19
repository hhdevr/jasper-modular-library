package com.chaykin.jasper.processor.model;

import java.util.List;

/**
 * Describes a JasperReports sub-dataset to be injected into a JRXML template.
 *
 * <p>A dataset is required for any {@code List}-typed field whose element type is not a
 * primitive wrapper or {@code String}. Each dataset maps to a {@code <dataset>} element
 * in the JRXML and is referenced by a {@code list} component via a {@code <datasetRun>}.</p>
 *
 * @param name   the dataset name, derived from the collection field name in the report class
 * @param fields the list of fields that the dataset exposes, derived from the element
 *               class's declared fields
 */
public record JrxmlDataset(String name,
                           List<JrxmlDatasetField> fields) {

}
