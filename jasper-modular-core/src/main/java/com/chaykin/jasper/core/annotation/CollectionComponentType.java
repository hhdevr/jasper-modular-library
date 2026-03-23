package com.chaykin.jasper.core.annotation;

/**
 * Defines the JRXML component type generated for a {@link java.util.Collection}-typed
 * field in a report class.
 *
 * <p>Used as the {@link JasperCollection#type()} attribute to control whether the
 * annotation processor generates a {@code list} component or a {@code table} component
 * in the JRXML detail section.</p>
 *
 * @see JasperCollection
 */
public enum CollectionComponentType {

    /**
     * Generates a JasperReports {@code list} component.
     *
     * <p>A list renders each row of the collection sequentially, with all fields
     * displayed in a single horizontal row per item. This is the processor's fallback
     * when the {@code @JasperCollection} annotation is absent entirely.</p>
     */
    LIST,

    /**
     * Generates a JasperReports {@code table} component.
     *
     * <p>A table renders the collection with a column header row and a detail row
     * per item, making it suitable for structured tabular data. This is the default
     * when the {@code @JasperCollection} annotation is present.</p>
     */
    TABLE
}
