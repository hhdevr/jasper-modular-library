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
 * @see com.chaykin.jasper.core.model.SubreportModule
 * @see JasperModularReport
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JasperSubreport {

    /**
     * Classpath-relative path to the JRXML template; must start with {@code /}.
     */
    String templatePath();

    /**
     * Prefix for the injected {@code <prefix>Report} and {@code <prefix>MapParameter}
     * parameters; defaults to the subreport's simple class name when empty.
     */
    String prefix() default "";

    /**
     * Compile-time JRXML generation strategy.
     */
    GenerationMode mode() default GenerationMode.INJECT;

    /**
     * Page orientation of the blank template used during JRXML generation; has no
     * effect when an existing template is already present on the classpath.
     */
    PageOrientation orientation() default PageOrientation.PORTRAIT;
}
