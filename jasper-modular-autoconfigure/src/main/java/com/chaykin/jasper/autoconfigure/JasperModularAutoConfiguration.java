package com.chaykin.jasper.autoconfigure;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot autoconfiguration for the jasper-modular library.
 *
 * <p>This configuration is activated automatically when {@code jasperreports.jar} is present
 * on the classpath (detected via {@link JasperCompileManager}). It registers the
 * {@link JasperReportPrecompiler} bean, which precompiles all discovered JRXML templates
 * at application startup.</p>
 *
 * <p>The autoconfiguration respects the {@link ConditionalOnMissingBean} condition, meaning
 * you can fully replace the precompiler by declaring your own {@link JasperReportPrecompiler}
 * bean in your application context.</p>
 *
 * <h2>Customization</h2>
 * <pre>{@code
 * jasper:
 *   modular:
 *     precompile-enabled: true
 *     base-package: com.example.reports
 * }</pre>
 *
 * @see JasperReportPrecompiler
 * @see JasperModularProperties
 */
@AutoConfiguration
@ConditionalOnClass(JasperCompileManager.class)
@EnableConfigurationProperties(JasperModularProperties.class)
public class JasperModularAutoConfiguration {

    /**
     * Creates the {@link JasperReportPrecompiler} bean that precompiles JRXML templates
     * at application startup.
     *
     * <p>This bean is only registered if no other {@link JasperReportPrecompiler} bean
     * is already present in the application context.</p>
     *
     * @param properties the configuration properties for jasper-modular
     * @return the configured precompiler
     */
    @Bean
    @ConditionalOnMissingBean
    public JasperReportPrecompiler jasperReportPrecompiler(JasperModularProperties properties) {
        return new JasperReportPrecompiler(properties);
    }
}
