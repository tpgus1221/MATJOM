package com.matjom.matjom.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.h2.H2ConsoleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class H2ConsoleSupportConfig {

    @Bean
    @ConditionalOnMissingBean(H2ConsoleProperties.class)
    @ConditionalOnProperty(value = "spring.h2.console.enabled", havingValue = "false", matchIfMissing = true)
    public H2ConsoleProperties h2ConsolePropertiesFallback() {
        return new H2ConsoleProperties();
    }
}
