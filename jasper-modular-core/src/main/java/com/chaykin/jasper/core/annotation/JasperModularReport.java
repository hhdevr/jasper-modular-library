package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a root JasperReports report module; the annotated class must extend
 * {@link com.chaykin.jasper.core.model.ModularReport}.
 *
 * <pre>{@code
 * @JasperModularReport(templatePath = "/reports/invoice.jrxml")
 * public class InvoiceReport extends ModularReport { ... }
 * }</pre>
 *
 * @see com.chaykin.jasper.core.model.ModularReport
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JasperModularReport {

    /**
     * Classpath-relative path to the JRXML template; must start with {@code /}
     * (e.g. {@code "/reports/invoice.jrxml"}).
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
