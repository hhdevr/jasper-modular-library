package com.chaykin.jasper.autoconfigure;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(JasperCompileManager.class)
@EnableConfigurationProperties(JasperModularProperties.class)
public class JasperModularAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JasperReportPrecompiler jasperReportPrecompiler(JasperModularProperties properties) {
        return new JasperReportPrecompiler(properties);
    }
}