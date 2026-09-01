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

    /** Thread-safe cache of compiled reports, keyed by JRXML template path. */
    Map<String, JasperReport> CACHE = new ConcurrentHashMap<>();

    /**
     * Returns the compiled report for this module, compiling and caching it on first access.
     *
     * @throws JasperModularException if the JRXML resource is not found or compilation fails
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
     * Returns the classpath-relative JRXML template path, resolved from the
     * {@link JasperModularReport} or {@link JasperSubreport} annotation.
     *
     * @throws JasperModularException if the implementing class has neither annotation
     */
    default String getTemplatePath() {
        JasperModularReport root = getClass().getAnnotation(JasperModularReport.class);
        if (root != null) {
            return root.templatePath();
        }

        JasperSubreport subreport = getClass().getAnnotation(JasperSubreport.class);
        if (subreport != null) {
            return subreport.templatePath();
        }

        throw new JasperModularException(
                "No @JasperModularReport or @JasperSubreport annotation found on: "
                + getClass().getSimpleName());
    }

    /** Returns the simple class name of this module, used in error messages and logging. */
    default String getModuleClassName() {
        return getClass().getSimpleName();
    }
}
