package com.chaykin.jasper.autoconfigure;

import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.exception.JasperModularException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Precompiles all discovered JRXML templates into {@link JasperModularCompiler#CACHE} at startup.
 */
public class JasperReportPrecompiler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JasperReportPrecompiler.class);

    private final JasperModularProperties properties;

    public JasperReportPrecompiler(JasperModularProperties properties) {
        this.properties = properties;
    }

    /**
     * Triggers precompilation if enabled.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isPrecompileEnabled()) {
            log.info("JasperReport precompilation is disabled");
            return;
        }
        precompileAll();
    }

    /**
     * Scans for report classes, compiles all discovered templates, and logs a summary.
     */
    private void precompileAll() {
        if (properties.getBasePackage().isBlank()) {
            log.warn("jasper.modular.base-package is not set - skipping JRXML precompilation");
            return;
        }

        List<String> paths = scanTemplatePaths();

        if (paths.isEmpty()) {
            log.warn("No @JasperModularReport or @JasperSubreport classes found in package: {}. " +
                     "Check jasper.modular.base-package in your configuration.",
                     properties.getBasePackage());
            return;
        }

        log.info("Precompiling {} JRXML templates...", paths.size());
        long totalStart = System.nanoTime();

        paths.forEach(this::compileAndCache);

        long ms = Math.round((System.nanoTime() - totalStart) / 1_000_000.0);
        log.info("Precompilation complete - {}/{} templates compiled in {} ms",
                 paths.size(),
                 paths.size(),
                 ms);
    }

    /**
     * Collects the template paths of all {@link JasperModularReport} and {@link JasperSubreport}
     * classes in the configured base package.
     */
    private List<String> scanTemplatePaths() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AnnotationTypeFilter(JasperModularReport.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(JasperSubreport.class));

        List<String> paths = new ArrayList<>();

        for (BeanDefinition bd: scanner.findCandidateComponents(properties.getBasePackage())) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());

                JasperModularReport root = clazz.getAnnotation(JasperModularReport.class);
                if (root != null && !root.templatePath().isEmpty()) {
                    paths.add(root.templatePath());
                }

                JasperSubreport sub = clazz.getAnnotation(JasperSubreport.class);
                if (sub != null && !sub.templatePath().isEmpty()) {
                    paths.add(sub.templatePath());
                }
            } catch (ClassNotFoundException e) {
                log.error("Cannot load class: {}", bd.getBeanClassName());
            }
        }
        return paths;
    }

    /**
     * Compiles the JRXML template at the given classpath path and stores it in the cache;
     * logs and rethrows on failure (fail-fast).
     */
    private void compileAndCache(String path) {
        long start = System.nanoTime();
        try {
            JasperModularCompiler.CACHE.computeIfAbsent(path, p -> {
                try (InputStream stream = getClass().getResourceAsStream(p)) {
                    if (stream == null) {
                        throw new JasperModularException("JRXML not found: " + p);
                    }
                    return JasperCompileManager.compileReport(stream);
                } catch (JRException | IOException e) {
                    throw new JasperModularException("Failed to compile: " + p, e);
                }
            });
            long ms = Math.round((System.nanoTime() - start) / 1_000_000.0);
            log.info("  ✓ {} - {} ms", path, ms);
        } catch (JasperModularException e) {
            log.error("  ✗ {} - FAILED: {}", path, e.getMessage());
            throw e;
        }
    }
}
