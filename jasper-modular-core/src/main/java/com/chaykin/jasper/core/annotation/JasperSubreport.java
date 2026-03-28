package com.chaykin.jasper.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a JasperReports subreport module.
 *
 * <p>Classes annotated with {@code @JasperSubreport} must extend
 * {@link com.chaykin.jasper.core.model.SubreportModule}. At compile time, the annotation
 * processor generates or updates the corresponding JRXML template. At runtime, subreport
 * modules are embedded into a root report by being declared as fields in a
 * {@link JasperModularReport} annotated class.</p>
 *
 * <p>When the data filler processes a root report, it finds all fields whose type is
 * annotated with {@code @JasperSubreport} and automatically injects two parameters
 * into the root report's parameters map:</p>
 * <ul>
 *   <li>{@code <prefix>Report} - the compiled {@code JasperReport} for the subreport</li>
 *   <li>{@code <prefix>MapParameter} - the filled parameters map for the subreport</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @JasperSubreport(templatePath = "/reports/sub_items.jrxml", prefix = "Items")
 * public class ItemsModule extends SubreportModule {
 *
 *     private List<Item> items;
 *     private BigDecimal subtotal;
 *
 *     @Override
 *     public boolean isEmpty() { return items == null || items.isEmpty(); }
 * }
 * }</pre>
 *
 * @see com.chaykin.jasper.core.model.SubreportModule
 * @see JasperModularReport
 * @see GenerationMode
 * @see PageOrientation
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JasperSubreport {

    /**
     * The classpath-relative path to the JRXML template file for this subreport.
     *
     * <p>The path must start with {@code /} and point to a resource available on
     * the classpath.</p>
     *
     * <p>Example: {@code "/reports/sub_items.jrxml"}</p>
     *
     * @return the path to the JRXML template
     */
    String templatePath();

    /**
     * The prefix used to generate parameter names in the root report's JRXML.
     *
     * <p>Two parameters will be injected into the root report:</p>
     * <ul>
     *   <li>{@code <prefix>Report} - the compiled subreport</li>
     *   <li>{@code <prefix>MapParameter} - the subreport's parameter map</li>
     * </ul>
     *
     * <p>If empty (the default), the simple class name of the subreport is used
     * as the prefix.</p>
     *
     * @return the parameter prefix, or empty string to use the class name
     */
    String prefix() default "";

    /**
     * The JRXML generation strategy to use at compile time.
     * Defaults to {@link GenerationMode#INJECT}.
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
