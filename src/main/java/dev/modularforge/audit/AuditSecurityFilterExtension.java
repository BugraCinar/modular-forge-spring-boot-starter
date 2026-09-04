package dev.modularforge.audit;

import dev.modularforge.security.JwtAuthFilter;
import dev.modularforge.security.SecurityFilterExtension;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

@Component
@AuditModule
@RequiredArgsConstructor
class AuditSecurityFilterExtension implements SecurityFilterExtension {

    private final SensitiveEndpointAccessFilter filter;

    @Override
    public void configure(HttpSecurity http) {
        http.addFilterAfter(filter, JwtAuthFilter.class);
    }
}
