package com.chaykin.jasper.autoconfigure;

import com.chaykin.jasper.core.annotation.JasperModularReport;
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

public class JasperReportPrecompiler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JasperReportPrecompiler.class);

    private final JasperModularProperties properties;

    public JasperReportPrecompiler(JasperModularProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.isPrecompileEnabled()) {
            log.info("JasperReport precompilation is disabled");
            return;
        }
        precompileAll();
    }

    private void precompileAll() {
        List<String> paths = scanTemplatePaths();

        if (paths.isEmpty()) {
            log.warn("No @JasperTemplate classes found in package: {}",
                     properties.getBasePackage());
            return;
        }

        log.info("Precompiling {} JRXML templates...", paths.size());
        long totalStart = System.nanoTime();

        paths.forEach(this::compileAndCache);

        long ms = Math.round((System.nanoTime() - totalStart) / 1_000_000.0);
        log.info("Precompilation complete - {} ms", ms);
    }

    private List<String> scanTemplatePaths() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);

        scanner.addIncludeFilter(new AnnotationTypeFilter(JasperModularReport.class));

        List<String> paths = new ArrayList<>();

        for (BeanDefinition bd: scanner.findCandidateComponents(
                properties.getBasePackage())) {
            try {
                Class<?> clazz = Class.forName(bd.getBeanClassName());

                JasperModularReport template = clazz.getAnnotation(JasperModularReport.class);
                if (template != null && !template.templatePath().isEmpty()) {
                    paths.add(template.templatePath());
                }
            } catch (ClassNotFoundException e) {
                log.error("Cannot load class: {}", bd.getBeanClassName());
            }
        }
        return paths;
    }

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
        }
    }
}