package dev.modularforge.security;

import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAccessDeniedHandlerTest {

    @Mock AuthenticationErrorAudit audit;
    @Mock JwtUtils jwtUtils;
    @Mock Authentication authentication;

    private CustomAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomAccessDeniedHandler();
        ReflectionTestUtils.setField(handler, "authErrorLogService", audit);
        ReflectionTestUtils.setField(handler, "jwtUtils", jwtUtils);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesSafeJsonAndAuditsAnonymousDenial() throws Exception {
        MockHttpServletRequest request = request("/api/admin/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("policy denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("Forbidden", "Access denied", "/api/admin/settings")
                .doesNotContain("policy denied");
        verify(audit).log403(null, null, null, "192.0.2.20", "test-agent", "/api/admin/settings",
                "GET", "policy denied", "Access denied to protected resource");
    }

    @Test
    void prefersSecurityContextIdentityAndUsesJwtForRole() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.getDetails()).thenReturn(7L);
        when(authentication.getPrincipal()).thenReturn("context-user");
        when(jwtUtils.extractRole("token")).thenReturn("ADMIN");
        MockHttpServletRequest request = request("/api/admin");
        request.addHeader("Authorization", "Bearer token");

        handler.handle(request, new MockHttpServletResponse(), new AccessDeniedException("denied"));

        verify(audit).log403(7L, "ADMIN", "context-user", "192.0.2.20", "test-agent", "/api/admin",
                "GET", "denied", "Access denied to protected resource");
        verify(jwtUtils, never()).extractUsername(anyString());
        verify(jwtUtils, never()).extractUserIdAsLong(anyString());
    }

    @Test
    void extractsAllIdentityFieldsFromJwtAndToleratesInvalidJwt() throws Exception {
        MockHttpServletRequest request = request("/api/admin");
        request.addHeader("Authorization", "Bearer token");
        when(jwtUtils.extractUsername("token")).thenReturn("jwt-user");
        when(jwtUtils.extractUserIdAsLong("token")).thenReturn(8L);
        when(jwtUtils.extractRole("token")).thenReturn("USER");
        handler.handle(request, new MockHttpServletResponse(), new AccessDeniedException("denied"));
        verify(audit).log403(8L, "USER", "jwt-user", "192.0.2.20", "test-agent", "/api/admin",
                "GET", "denied", "Access denied to protected resource");

        MockHttpServletRequest invalid = request("/api/profile");
        invalid.addHeader("Authorization", "Bearer broken");
        when(jwtUtils.extractUsername("broken")).thenThrow(new IllegalArgumentException("bad jwt"));
        handler.handle(invalid, new MockHttpServletResponse(), new AccessDeniedException("denied"));

        MockHttpServletRequest basic = request("/api/profile");
        basic.addHeader("Authorization", "Basic credentials");
        handler.handle(basic, new MockHttpServletResponse(), new AccessDeniedException("denied"));
    }

    @Test
    void auditFailureDoesNotBreakForbiddenResponse() throws Exception {
        doThrow(new IllegalStateException("audit down")).when(audit)
                .log403(any(), any(), any(), any(), any(), any(), any(), any(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request("/api/admin"), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void unexpectedSecurityContextFailureIsContained() throws Exception {
        SecurityContext context = org.mockito.Mockito.mock(SecurityContext.class);
        when(context.getAuthentication()).thenThrow(new IllegalStateException("context unavailable"));
        SecurityContextHolder.setContext(context);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request("/api/admin"), response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        verify(audit).log403(null, null, null, "192.0.2.20", "test-agent", "/api/admin",
                "GET", "denied", "Access denied to protected resource");
    }

    @Test
    void nonStringPrincipalAndNonLongDetailsAreIgnored() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.getDetails()).thenReturn("details");
        when(authentication.getPrincipal()).thenReturn(new Object());

        handler.handle(request("/api/admin"), new MockHttpServletResponse(), new AccessDeniedException("denied"));

        verify(audit).log403(null, null, null, "192.0.2.20", "test-agent", "/api/admin",
                "GET", "denied", "Access denied to protected resource");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/.env", "/wp-includes/a", "/wp-content/a", "/wordpress/admin", "/wp/login",
            "/xmlrpc.php", "/sitemap.xml", "/config.yml", "/config.yaml", "/web.config", "/.git/config"
    })
    void skipsAuditForBotScannerPaths(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request(path), response, new AccessDeniedException("denied"));
        assertThat(response.getStatus()).isEqualTo(403);
        verify(audit, never()).log403(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServletPath(uri);
        request.setRemoteAddr("192.0.2.20");
        request.addHeader("User-Agent", "test-agent");
        return request;
    }
}
