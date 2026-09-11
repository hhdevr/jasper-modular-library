package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures JRXML component generation for a {@link java.util.Collection}-typed field
 * in a {@link JasperModularReport} or {@link JasperSubreport} annotated class.
 * Ignored for collections of {@link JasperSubreport} modules.
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
     */
    CollectionComponentType type() default CollectionComponentType.TABLE;

    /**
     * The width in pixels of each generated column; total width is {@code columnCount * columnWidth}.
     */
    int columnWidth() default DEFAULT_COLUMN_WIDTH;
}
