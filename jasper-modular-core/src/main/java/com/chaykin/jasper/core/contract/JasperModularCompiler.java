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
 *
 * <p>All report and subreport classes (extending {@link com.chaykin.jasper.core.model.ModularReport}
 * or {@link com.chaykin.jasper.core.model.SubreportModule}) implement this interface through
 * their base classes. It provides a default implementation that:</p>
 * <ol>
 *   <li>Resolves the JRXML template path from the {@link JasperModularReport} or
 *       {@link JasperSubreport} annotation on the implementing class</li>
 *   <li>Compiles the JRXML to a {@link JasperReport} on first access</li>
 *   <li>Caches the compiled result in the shared application-wide {@link #CACHE}
 *       to avoid redundant compilation on subsequent calls</li>
 * </ol>
 *
 * <p>The cache is populated eagerly at application startup by
 * {@code JasperReportPrecompiler} when precompilation is enabled (the default).
 * This eliminates compilation latency on the first report request in production.</p>
 */
public interface JasperModularCompiler {

    /**
     * Shared application-wide cache of compiled {@link JasperReport} objects,
     * keyed by the JRXML template path.
     *
     * <p>This cache is thread-safe and shared across all report instances.
     * It is populated by {@code JasperReportPrecompiler} at startup and lazily
     * on first use of any uncached template.</p>
     */
    Map<String, JasperReport> CACHE = new ConcurrentHashMap<>();

    /**
     * Returns the compiled {@link JasperReport} for this module, compiling and caching
     * it on first access.
     *
     * <p>The JRXML resource is loaded from the classpath using the path returned by
     * {@link #getTemplatePath()}. Subsequent calls return the cached instance without
     * recompilation.</p>
     *
     * @return the compiled report template
     * @throws JasperModularException if the JRXML resource is not found on the classpath
     *                                or if compilation fails
     */
    default JasperReport compileReport() {
        return CACHE.computeIfAbsent(getTemplatePath(), path -> {
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                if (stream == null) {
                    throw new JasperModularException(
                            format("JRXML not found: {0}", path));
                }
                return JasperCompileManager.compileReport(stream);
            } catch (JRException | IOException e) {
                throw new JasperModularException(
                        format("Error compiling JRXML for {0} in {1}",
                               getModuleClassName(), path), e);
            }
        });
    }

    /**
     * Returns the classpath-relative path to the JRXML template for this module.
     *
     * <p>The path is resolved from the {@link JasperModularReport} annotation if present,
     * otherwise from the {@link JasperSubreport} annotation. Throws if neither is found.</p>
     *
     * @return the JRXML template path, e.g. {@code /reports/invoice.jrxml}
     * @throws JasperModularException if the implementing class has neither annotation
     */
    default String getTemplatePath() {
        JasperModularReport root = getClass().getAnnotation(JasperModularReport.class);
        if (root != null) {
            return root.templatePath();
        }

        JasperSubreport sub = getClass().getAnnotation(JasperSubreport.class);
        if (sub != null) {
            return sub.templatePath();
        }

        throw new JasperModularException(
                "No @JasperModularReport or @JasperSubreport annotation found on: "
                + getClass().getSimpleName());
    }

    /**
     * Returns the simple class name of this module, used in error messages and logging.
     *
     * @return the simple class name of the implementing class
     */
    default String getModuleClassName() {
        return getClass().getSimpleName();
    }
}
