package dev.modularforge.audit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AuditModule
class AuditFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<SensitiveEndpointAccessFilter> sensitiveEndpointFilterRegistration(
            SensitiveEndpointAccessFilter filter) {
        FilterRegistrationBean<SensitiveEndpointAccessFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
