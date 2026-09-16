package dev.modularforge.security;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import dev.modularforge.security.CustomAccessDeniedHandler;
import dev.modularforge.ratelimit.GlobalRateLimitFilter;
import dev.modularforge.security.JwtAuthEntryPoint;
import dev.modularforge.security.JwtAuthFilter;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private JwtAuthEntryPoint jwtAuthEntryPoint;

    @Autowired
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Autowired
    private org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private GlobalRateLimitFilter globalRateLimitFilter;

    @Autowired
    private ObjectProvider<SecurityFilterExtension> filterExtensions;

    @Value("${app.refresh-token.cookie-name:refreshToken}")
    private String refreshCookieName;

    @Value("${app.refresh-token.cookie-secure:true}")
    private boolean secureCookies;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<GlobalRateLimitFilter> globalRateLimitFilterRegistration(GlobalRateLimitFilter filter) {
        FilterRegistrationBean<GlobalRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .requireCsrfProtectionMatcher(new RefreshCookieCsrfMatcher(refreshCookieName)))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> {
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true)
                                .preload(true))
                        .referrerPolicy(referrerPolicy -> referrerPolicy
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()")))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll();
                    auth.requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout-all").authenticated();
                    auth.requestMatchers("/api/v1/auth/**").permitAll();
                    auth.requestMatchers("/api/v1/auth/verify-email-change").permitAll();
                    auth.requestMatchers("/api/v1/admin/2fa/verify-login").permitAll();
                    auth.requestMatchers("/api/v1/health/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/modules").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");

                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/users/profile").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/api/v1/users/profile").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers(HttpMethod.GET,    "/api/v1/profile").hasRole("USER");
                    auth.requestMatchers(HttpMethod.PUT,    "/api/v1/profile").hasRole("USER");
                    auth.requestMatchers(HttpMethod.POST,   "/api/v1/profile/change-password").hasRole("USER");
                    auth.requestMatchers(HttpMethod.POST,   "/api/v1/profile/change-email").hasRole("USER");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/v1/profile").hasRole("USER");
                    auth.requestMatchers(HttpMethod.POST,   "/api/v1/profile/image").hasRole("USER");
                    auth.requestMatchers(HttpMethod.PUT,    "/api/v1/profile/image").hasRole("USER");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/v1/profile/image").hasRole("USER");
                    auth.requestMatchers("/api/v1/chat/**").hasAnyRole("USER", "ADMIN");
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })

                .addFilterBefore(new CspNonceFilter(), org.springframework.security.web.header.HeaderWriterFilter.class)
                .addFilterBefore(globalRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        for (SecurityFilterExtension extension : filterExtensions.orderedStream().toList()) {
            extension.configure(http);
        }

        return http.build();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(secureCookies)
                .sameSite("Strict"));
        return repository;
    }
}
