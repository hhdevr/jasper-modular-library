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
 * Scans the configured base package at application startup and precompiles all discovered
 * JRXML report templates into the shared {@link JasperModularCompiler#CACHE}.
 *
 * <p>Precompilation ensures that the first report request in production does not incur
 * JRXML compilation latency. Templates are compiled once and cached for the lifetime
 * of the application.</p>
 *
 * <p>This component is registered automatically by {@link JasperModularAutoConfiguration}
 * and runs after the Spring application context is fully initialized (via
 * {@link ApplicationRunner}).</p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Scans {@link JasperModularProperties#getBasePackage()} for classes annotated with
 *       {@link JasperModularReport} and {@link JasperSubreport}</li>
 *   <li>Skips templates that are already present in the cache</li>
 *   <li>Logs each compiled template with its compilation time in milliseconds</li>
 *   <li>Logs an error for templates that fail to compile without stopping the application</li>
 *   <li>Does nothing if {@link JasperModularProperties#isPrecompileEnabled()} is
 *       {@code false}</li>
 * </ul>
 *
 * @see JasperModularAutoConfiguration
 * @see JasperModularProperties
 * @see JasperModularCompiler#CACHE
 */
public class JasperReportPrecompiler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JasperReportPrecompiler.class);

    private final JasperModularProperties properties;

    /**
     * Constructs a new precompiler with the given configuration properties.
     *
     * @param properties the jasper-modular configuration properties
     */
    public JasperReportPrecompiler(JasperModularProperties properties) {
        this.properties = properties;
    }

    /**
     * Entry point called by Spring Boot after the application context is ready.
     *
     * <p>Triggers precompilation if {@link JasperModularProperties#isPrecompileEnabled()}
     * is {@code true}. Otherwise logs a message and returns immediately.</p>
     *
     * @param args the application arguments (not used)
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
     * Scans for report classes, compiles all discovered templates, and logs the summary.
     */
    private void precompileAll() {
        List<String> paths = scanTemplatePaths();

        if (paths.isEmpty()) {
            log.warn("No @JasperModularReport or @JasperSubreport classes found in package: {}",
                     properties.getBasePackage());
            return;
        }

        log.info("Precompiling {} JRXML templates...", paths.size());
        long totalStart = System.nanoTime();

        paths.forEach(this::compileAndCache);

        long ms = Math.round((System.nanoTime() - totalStart) / 1_000_000.0);
        log.info("Precompilation complete - {} ms", ms);
    }

    /**
     * Scans the configured base package for all classes annotated with
     * {@link JasperModularReport} or {@link JasperSubreport} and collects their template
     * paths.
     *
     * @return a list of JRXML template paths to precompile; never {@code null}
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
     * Compiles the JRXML template at the given classpath path and stores it in the cache.
     *
     * <p>If the template is already cached, this method returns immediately. Compilation
     * failures are logged as errors but do not propagate - the application continues to
     * start normally and compilation will be retried lazily on first use.</p>
     *
     * @param path the classpath-relative JRXML path, e.g. {@code /reports/invoice.jrxml}
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
        }
    }
}
