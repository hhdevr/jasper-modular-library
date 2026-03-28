package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a root JasperReports report module.
 *
 * <p>Classes annotated with {@code @JasperModularReport} must extend
 * {@link com.chaykin.jasper.core.model.ModularReport}.
 * At compile time, the annotation processor scans all such classes and generates or
 * updates the corresponding JRXML template according to the specified {@link #mode()}.</p>
 *
 * <p>At runtime, the class is used by
 * {@link com.chaykin.jasper.core.renderer.JasperModularRenderer}
 * to compile the template (with caching), fill it with parameters collected from the
 * fields, and produce a {@code JasperPrint} ready for export.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JasperModularReport(templatePath = "/reports/invoice.jrxml")
 * public class InvoiceReport extends ModularReport {
 *
 *     private String customerName;
 *     private BigDecimal totalAmount;
 *     private LineItemsModule lineItems; // @JasperSubreport annotated class
 * }
 * }</pre>
 *
 * <h2>Landscape example</h2>
 * <pre>{@code
 * @JasperModularReport(
 *     templatePath = "/reports/wide_report.jrxml",
 *     orientation = PageOrientation.LANDSCAPE
 * )
 * public class WideReport extends ModularReport { ... }
 * }</pre>
 *
 * @see com.chaykin.jasper.core.model.ModularReport
 * @see GenerationMode
 * @see PageOrientation
 * @see JasperSubreport
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JasperModularReport {

    /**
     * The classpath-relative path to the JRXML template file.
     *
     * <p>The path must start with {@code /} and point to a resource available on the
     * classpath, typically located under {@code src/main/resources}.</p>
     *
     * <p>Example: {@code "/reports/invoice.jrxml"}</p>
     *
     * @return the path to the JRXML template
     */
    String templatePath();

    /**
     * The JRXML generation strategy to use at compile time.
     *
     * <p>Defaults to {@link GenerationMode#INJECT}, which injects missing elements into
     * an existing template without overwriting user-defined layout.</p>
     *
     * @return the generation mode
     * @see GenerationMode
     */
    GenerationMode mode() default GenerationMode.INJECT;

    /**
     * The page orientation for the blank template used during JRXML generation.
     *
     * <p>Controls whether the processor picks {@code Portrait.jrxml} (portrait) or
     * {@code Landscape.jrxml} (landscape) as the starting point. Has no effect
     * on existing templates in {@link GenerationMode#INJECT} mode when the template file
     * is already present on the classpath.</p>
     *
     * <p>Defaults to {@link PageOrientation#PORTRAIT}.</p>
     *
     * @return the page orientation
     * @see PageOrientation
     */
    PageOrientation orientation() default PageOrientation.PORTRAIT;
}
