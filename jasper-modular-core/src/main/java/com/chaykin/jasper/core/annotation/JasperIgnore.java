package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field in a {@link JasperModularReport} or {@link JasperSubreport} class
 * to be excluded from JRXML parameter generation and report data filling.
 *
 * <p>By default, the annotation processor and the data filler inspect all declared fields
 * of a report class to generate JRXML parameters and populate the parameters map.
 * Annotating a field with {@code @JasperIgnore} instructs both the compile-time processor
 * and the runtime filler to skip that field entirely.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JasperModularReport(templatePath = "/reports/order.jrxml")
 * public class OrderReport extends ModularReport {
 *
 *     private String customerName;   // included in JRXML and filling
 *
 *     @JasperIgnore
 *     private transient String internalCache; // excluded from JRXML and filling
 * }
 * }</pre>
 *
 * @see JasperModularReport
 * @see JasperSubreport
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JasperIgnore {
}
