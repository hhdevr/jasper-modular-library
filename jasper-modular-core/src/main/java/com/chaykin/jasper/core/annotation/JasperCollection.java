package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures JRXML component generation for a {@link java.util.Collection}-typed field
 * in a {@link JasperModularReport} or {@link JasperSubreport} annotated class.
 *
 * <p>When placed on a collection field, this annotation controls:</p>
 * <ul>
 *   <li>Which JRXML component type is generated — {@code list} or {@code table}</li>
 *   <li>The pixel width of each generated column</li>
 * </ul>
 *
 * <p>If the annotation is absent, the processor defaults to a {@code list} component
 * with a column width of {@value #DEFAULT_COLUMN_WIDTH} pixels for backwards
 * compatibility. When the annotation is present without an explicit {@code type},
 * the default is {@link CollectionComponentType#TABLE}.</p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Default list with custom column width
 * @JasperCollection(columnWidth = 80)
 * private List<LineItem> items;
 *
 * // Table component with default column width
 * @JasperCollection(type = CollectionComponentType.TABLE)
 * private List<LineItem> items;
 *
 * // Table with custom column width
 * @JasperCollection(type = CollectionComponentType.TABLE, columnWidth = 100)
 * private List<LineItem> items;
 * }</pre>
 *
 * @see CollectionComponentType
 * @see JasperModularReport
 * @see JasperSubreport
 * @see JasperIgnore
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface JasperCollection {

    /**
     * Default column width in pixels used when no explicit value is specified.
     */
    int DEFAULT_COLUMN_WIDTH = 100;

    /**
     * The JRXML component type to generate for this collection field.
     * Defaults to {@link CollectionComponentType#TABLE}.
     *
     * <p>Note: when the {@code @JasperCollection} annotation is absent entirely,
     * the processor falls back to {@link CollectionComponentType#LIST} for
     * backwards compatibility.</p>
     *
     * @return the component type
     */
    CollectionComponentType type() default CollectionComponentType.TABLE;

    /**
     * The width in pixels of each generated column.
     *
     * <p>The total component width is calculated as
     * {@code columnCount * columnWidth}, where {@code columnCount} is the number
     * of fields in the collection element class.</p>
     *
     * <p>Defaults to {@value DEFAULT_COLUMN_WIDTH}.</p>
     *
     * @return the column width in pixels
     */
    int columnWidth() default DEFAULT_COLUMN_WIDTH;
}
