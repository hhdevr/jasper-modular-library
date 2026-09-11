package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a JasperReports subreport module; the class must extend
 * {@link com.chaykin.jasper.core.model.SubreportModule}.
 *
 * <p>Parameter names come from the field: a field {@code items} of this type is passed as
 * {@code itemsReport} and {@code itemsMapParameter}; a field {@code items} holding a collection
 * of this type is passed as {@code itemsDataSource}, iterated by the dataset {@code itemsDataset}.
 *
 * @see com.chaykin.jasper.core.model.SubreportModule
 * @see JasperModularReport
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JasperSubreport {

    /**
     * Classpath path to the JRXML template, read from the classpath root with or without
     * a leading {@code /}.
     */
    String templatePath();

    /**
     * Compile-time JRXML generation strategy.
     */
    GenerationMode mode() default GenerationMode.INJECT;

    /**
     * Page orientation of the blank template used when generating a new JRXML; has no
     * effect in {@link GenerationMode#INJECT} mode when the template already exists.
     */
    PageOrientation orientation() default PageOrientation.PORTRAIT;
}
