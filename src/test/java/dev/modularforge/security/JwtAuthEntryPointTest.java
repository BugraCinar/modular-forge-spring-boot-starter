package dev.modularforge.security;

import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JwtAuthEntryPointTest {

    private final AuthenticationErrorAudit audit = mock(AuthenticationErrorAudit.class);
    private JwtAuthEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        entryPoint = new JwtAuthEntryPoint();
        ReflectionTestUtils.setField(entryPoint, "authErrorLogService", audit);
    }

    @Test
    void auditsNormalRequestsAndWritesGenericUnauthorizedJson() throws Exception {
        MockHttpServletRequest request = request("/api/v1/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("token detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("Unauthorized", "Authentication required", "/api/v1/profile")
                .doesNotContain("token detail");
        verify(audit).log401("192.0.2.30", "test-agent", "/api/v1/profile", "GET", "token detail");
    }

    @Test
    void auditFailureDoesNotChangeUnauthorizedResponse() throws Exception {
        doThrow(new IllegalStateException("audit down")).when(audit)
                .log401(any(), any(), any(), any(), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request("/api/v1/profile"), response,
                new InsufficientAuthenticationException("denied"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/.env", "/wp-includes/a", "/wp-content/a", "/wordpress/admin", "/wp/login",
            "/xmlrpc.php", "/sitemap.xml", "/config.yml", "/config.yaml", "/web.config", "/.git/config"
    })
    void scannerPathsAreNotWrittenToAuditStorage(String path) throws Exception {
        entryPoint.commence(request(path), new MockHttpServletResponse(),
                new InsufficientAuthenticationException("denied"));

        verify(audit, never()).log401(any(), any(), any(), any(), any());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setRemoteAddr("192.0.2.30");
        request.addHeader("User-Agent", "test-agent");
        return request;
    }
}
