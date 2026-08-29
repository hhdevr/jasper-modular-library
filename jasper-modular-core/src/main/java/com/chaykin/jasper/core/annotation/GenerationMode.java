package com.chaykin.jasper.core.annotation;

/**
 * JRXML generation strategy applied by the annotation processor to a report or subreport class.
 */
public enum GenerationMode {

    /** No JRXML generation; the file is managed entirely by hand. */
    NONE,

    /** Creates a new JRXML from a blank template, overwriting any existing file. */
    CREATE,

    /** Injects missing elements into an existing JRXML, preserving user layout (default). */
    INJECT
}
