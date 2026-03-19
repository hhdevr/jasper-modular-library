package com.chaykin.jasper.core.annotation;

/**
 * Defines the JRXML generation strategy used by the annotation processor
 * when processing {@link JasperModularReport} and {@link JasperSubreport} annotated classes.
 *
 * <p>The mode is declared on each report or subreport class and controls how the
 * annotation processor creates or updates the corresponding {@code .jrxml} template file
 * at compile time.</p>
 *
 * @see JasperModularReport#mode()
 * @see JasperSubreport#mode()
 */
public enum GenerationMode {

    /**
     * No JRXML generation is performed.
     *
     * <p>Use this mode when you want to manage the JRXML file entirely by hand
     * and prevent the processor from modifying it at all.</p>
     */
    NONE,

    /**
     * Creates a new JRXML file from scratch using a built-in blank template.
     *
     * <p>The processor loads the default blank template (e.g. {@code Blank_A4.jrxml})
     * and injects all discovered parameters, datasets, list components and subreport bands
     * derived from the annotated class fields. If a file already exists at the target path,
     * it will be overwritten.</p>
     *
     * <p>Use this mode when creating a new report to quickly scaffold a working template
     * that you can then open in Jaspersoft Studio to add your design.</p>
     */
    CREATE,

    /**
     * Injects missing elements into an existing JRXML file without overwriting
     * user-defined layout.
     *
     * <p>The processor reads the existing template and adds only the elements that are
     * not already present - parameters, datasets, list components and subreport bands.
     * Existing elements are detected by name and skipped, so custom layout and styling
     * created in Jaspersoft Studio is preserved.</p>
     *
     * <p>This is the default mode and the recommended approach for production use.</p>
     */
    INJECT
}
