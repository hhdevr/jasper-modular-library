package com.chaykin.jasper.autoconfigure;

import com.chaykin.jasper.core.annotation.JasperModularReport;
import com.chaykin.jasper.core.annotation.JasperSubreport;
import com.chaykin.jasper.core.contract.JasperModularCompiler;
import com.chaykin.jasper.core.exception.JasperModularException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        JasperModularCompiler.CACHE.clear();

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

        List<ReportTemplate> templates = scanTemplates();

        if (templates.isEmpty()) {
            log.warn("No @JasperModularReport or @JasperSubreport classes found in package: {}. " +
                     "Check jasper.modular.base-package in your configuration.",
                     properties.getBasePackage());
            return;
        }

        log.info("Precompiling {} JRXML templates...", templates.size());
        long totalStart = System.nanoTime();

        templates.forEach(this::compileAndCache);

        long ms = Math.round((System.nanoTime() - totalStart) / 1_000_000.0);
        log.info("Precompilation complete - {} templates compiled in {} ms", templates.size(), ms);
    }

    /**
     * Collects the distinct template paths of all {@link JasperModularReport} and
     * {@link JasperSubreport} classes in the configured base package, including subclasses that
     * inherit the annotation.
     */
    private List<ReportTemplate> scanTemplates() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AnnotationTypeFilter(JasperModularReport.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(JasperSubreport.class));

        Map<String, ReportTemplate> templates = new LinkedHashMap<>();

        for (BeanDefinition beanDefinition: scanner.findCandidateComponents(properties.getBasePackage())) {
            try {
                Class<?> clazz = Class.forName(beanDefinition.getBeanClassName());
                String templatePath = JasperModularCompiler.templatePathOf(clazz);

                if (!templatePath.isEmpty()) {
                    templates.putIfAbsent(templatePath, new ReportTemplate(clazz, templatePath));
                }
            } catch (ClassNotFoundException e) {
                log.error("Cannot load class: {}", beanDefinition.getBeanClassName());
            }
        }
        return List.copyOf(templates.values());
    }

    /**
     * Compiles the JRXML template at the given classpath path and stores it in the cache;
     * logs and rethrows on failure (fail-fast).
     */
    private void compileAndCache(ReportTemplate template) {
        long start = System.nanoTime();
        try {
            JasperModularCompiler.compileReport(template.type(), template.path());
            long ms = Math.round((System.nanoTime() - start) / 1_000_000.0);
            log.info("  ✓ {} - {} ms", template.path(), ms);
        } catch (JasperModularException e) {
            log.error("  ✗ {} - FAILED: {}", template.path(), e.getMessage());
            throw e;
        }
    }

}
