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

public interface JasperModularCompiler {

    Map<String, JasperReport> CACHE = new ConcurrentHashMap<>();

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

    default String getTemplatePath() {
        JasperModularReport root = getClass().getAnnotation(JasperModularReport.class);
        if (root != null) return root.templatePath();

        JasperSubreport sub = getClass().getAnnotation(JasperSubreport.class);
        if (sub != null) return sub.templatePath();

        throw new JasperModularException(
                "No @JasperRootReport or @JasperSubreport annotation found on: "
                + getClass().getSimpleName());
    }

    default String getModuleClassName() {
        return getClass().getSimpleName();
    }
}