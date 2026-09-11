package com.chaykin.jasper.core.contract;

import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.exception.JasperModularException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.text.MessageFormat.format;

/**
 * Contract for compiling and caching a JasperReports template.
 */
public interface JasperModularCompiler {

    /**
     * Thread-safe cache of compiled reports, keyed by JRXML template path.
     */
    Map<String, JasperReport> CACHE = new ConcurrentHashMap<>();

    /**
     * Returns the compiled report for this module, compiling and caching it on first access.
     *
     * @throws JasperModularException if the JRXML resource is not found or compilation fails
     */
    default JasperReport compileReport() {
        return compileReport(getClass(), getTemplatePath());
    }

    /**
     * Compiles the JRXML at the given classpath path and caches it, resolving the resource
     * against {@code moduleType}. The path is read from the classpath root with or without
     * a leading {@code /}; both forms share one cache entry.
     *
     * @throws JasperModularException if the JRXML resource is not found or compilation fails
     */
    static JasperReport compileReport(Class<?> moduleType, String templatePath) {
        String absolutePath = templatePath.startsWith("/") ? templatePath : "/" + templatePath;
        return CACHE.computeIfAbsent(absolutePath, path -> {
            try (InputStream stream = moduleType.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new JasperModularException(
                            format("JRXML not found: {0}", path));
                }
                return JasperCompileManager.compileReport(stream);
            } catch (JRException | IOException e) {
                throw new JasperModularException(
                        format("Error compiling JRXML for {0} in {1}",
                               moduleType.getSimpleName(), path), e);
            }
        });
    }

    /**
     * Returns the classpath JRXML template path of this module, resolved by
     * {@link #templatePathOf(Class)}.
     *
     * @throws JasperModularException if the hierarchy carries no report annotation, or the
     *                                nearest annotated class carries both
     */
    default String getTemplatePath() {
        return templatePathOf(getClass());
    }

    /**
     * Returns the template path from the {@link JasperModularReport} or {@link JasperSubreport}
     * annotation of {@code type} or, if it has none, of its nearest annotated superclass.
     *
     * @throws JasperModularException if the hierarchy carries no report annotation, or the
     *                                nearest annotated class carries both
     */
    static String templatePathOf(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            JasperModularReport root = current.getDeclaredAnnotation(JasperModularReport.class);
            JasperSubreport subreport = current.getDeclaredAnnotation(JasperSubreport.class);

            if (root != null && subreport != null) {
                throw new JasperModularException(
                        "A class cannot be annotated with both @JasperModularReport and "
                        + "@JasperSubreport: " + current.getName());
            }
            if (root != null) {
                return root.templatePath();
            }
            if (subreport != null) {
                return subreport.templatePath();
            }
        }

        throw new JasperModularException(
                "No @JasperModularReport or @JasperSubreport annotation found on: "
                + type.getSimpleName());
    }

    /**
     * Returns the simple class name of this module, used in error messages and logging.
     */
    default String getModuleClassName() {
        return getClass().getSimpleName();
    }
}
