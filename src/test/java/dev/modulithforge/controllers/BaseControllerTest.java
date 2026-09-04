package dev.modulithforge.controllers;

import dev.modulithforge.audit.UserActivityLoggingInterceptor;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.security.SecurityConfig;
import dev.modulithforge.shared.error.GlobalExceptionHandler;

import dev.modulithforge.security.CustomAccessDeniedHandler;
import dev.modulithforge.ratelimit.GlobalRateLimitFilter;
import dev.modulithforge.security.JwtAuthEntryPoint;
import dev.modulithforge.security.JwtAuthFilter;
import dev.modulithforge.security.JwtUtils;
import dev.modulithforge.audit.SensitiveEndpointAccessFilter;
import dev.modulithforge.audit.AuthErrorLogService;
import dev.modulithforge.audit.UserActivityLogger;
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
