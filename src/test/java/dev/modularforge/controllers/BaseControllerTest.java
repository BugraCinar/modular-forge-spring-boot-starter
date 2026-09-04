package dev.modularforge.controllers;

import dev.modularforge.audit.UserActivityLoggingInterceptor;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.security.SecurityConfig;
import dev.modularforge.shared.error.GlobalExceptionHandler;

import dev.modularforge.security.CustomAccessDeniedHandler;
import dev.modularforge.ratelimit.GlobalRateLimitFilter;
import dev.modularforge.security.JwtAuthEntryPoint;
import dev.modularforge.security.JwtAuthFilter;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.audit.SensitiveEndpointAccessFilter;
import dev.modularforge.audit.AuthErrorLogService;
import dev.modularforge.audit.UserActivityLogger;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
@TestPropertySource(properties = {
    "recaptcha.enabled=false"  // override the ${RECAPTCHA_ENABLED:true} default
})
public abstract class BaseControllerTest {

    @MockitoBean protected JwtAuthFilter jwtAuthFilter;
    @MockitoBean protected JwtAuthEntryPoint jwtAuthEntryPoint;
    @MockitoBean protected CustomAccessDeniedHandler customAccessDeniedHandler;
    @MockitoBean protected GlobalRateLimitFilter globalRateLimitFilter;
    @MockitoBean protected SensitiveEndpointAccessFilter sensitiveEndpointAccessFilter;
    @MockitoBean protected UserActivityLogger userActivityLogger;

    @MockitoBean protected AuthErrorLogService authErrorLogService;
    @MockitoBean protected JwtUtils jwtUtils;
    protected Authentication makeAdminAuth(Long adminId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testAdmin", null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        auth.setDetails(adminId);
        return auth;
    }
    protected Authentication makeUserAuth(Long userId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testUser", null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        auth.setDetails(userId);
        return auth;
    }
}
