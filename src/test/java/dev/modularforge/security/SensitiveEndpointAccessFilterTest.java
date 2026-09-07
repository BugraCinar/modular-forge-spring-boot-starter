package dev.modularforge.security;

import dev.modularforge.audit.SensitiveEndpointAccessFilter;
import dev.modularforge.audit.SensitiveEndpointAccessLog;

import dev.modularforge.audit.SensitiveEndpointAccessLog.SeverityLevel;
import dev.modularforge.audit.SensitiveEndpointAccessLogService;
import dev.modularforge.shared.web.ApiRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveEndpointAccessFilterTest {

    @Mock
    private SensitiveEndpointAccessLogService accessLogService;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private SensitiveEndpointAccessFilter filter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "logSensitiveAccess", true);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsDeniedDatabaseBackupAttemptUsingTheControllerRouteContract() throws Exception {
        MockHttpServletRequest request = request("POST", ApiRoutes.DATABASE_BACKUP + "/create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(403));

        verify(accessLogService).logDatabaseBackupAccess(
                any(), any(), any(), anyString(), any(),
                eq(ApiRoutes.DATABASE_BACKUP + "/create"), eq("POST"), eq(403));
    }

    @Test
    void logsDeniedAdminImageAttemptInsteadOfIgnoringNon2xxResponses() throws Exception {
        MockHttpServletRequest request = request("DELETE", ApiRoutes.ADMIN_IMAGE + "/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);

        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401));

        verify(accessLogService).logAccess(
                eq(SeverityLevel.LOW), any(), any(), any(), anyString(), any(),
                eq(ApiRoutes.ADMIN_IMAGE + "/profile"), eq("DELETE"),
                eq("IMAGE_MANAGEMENT"), anyString(), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(401);
    }

    @Test
    void filteringCanBeDisabledAndOrdinaryPublicPathsAreSkipped() {
        MockHttpServletRequest request = request("GET", "/api/v1/public");
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(filter, "shouldNotFilter", request)).isTrue();

        ReflectionTestUtils.setField(filter, "logSensitiveAccess", false);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(filter, "shouldNotFilter",
                request("GET", "/api/v1/admin/admins"))).isTrue();
    }

    @Test
    void suspiciousPublicPathsAreFilteredAndUnmappedPathsAreIgnored() {
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(filter, "shouldNotFilter",
                request("GET", "/files/last.jks"))).isFalse();
        ReflectionTestUtils.invokeMethod(filter, "logSensitiveAccessIfNeeded",
                request("GET", "/ordinary"), new org.springframework.web.util.ContentCachingResponseWrapper(
                        new MockHttpServletResponse()));
        verify(accessLogService, never()).logAccess(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/admin/admins/12", "/api/v1/admin/tokens/reset", "/api/v1/admin/2fa/setup",
            "/api/v1/admin/2fa/enable", "/api/v1/admin/2fa/disable",
            "/api/v1/admin/activity-logs/1", "/api/v1/admin/auth-error-logs/1"
    })
    void logsEveryConfiguredAdminCategory(String path) throws Exception {
        filter.doFilter(request("GET", path), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(org.mockito.Mockito.mockingDetails(accessLogService).getInvocations()).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.env.production", "/private/final.jks", "/wp-admin/login"})
    void logsSuspiciousFilesAndProbePaths(String path) throws Exception {
        filter.doFilter(request("GET", path), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(org.mockito.Mockito.mockingDetails(accessLogService).getInvocations()).isNotEmpty();
    }

    @Test
    void extractsIdentityFromRequestAttributesAndSecurityContext() throws Exception {
        MockHttpServletRequest attributes = request("GET", "/api/v1/admin/admins");
        attributes.setAttribute("userId", 5L);
        attributes.setAttribute("role", "ADMIN");
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("root", "n/a", java.util.List.of());
        authentication.setDetails(8L);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(attributes, new MockHttpServletResponse(), (req, res) -> { });
        verify(accessLogService).logAdminManagementAccess(eq(5L), eq("ADMIN"), eq("root"),
                anyString(), any(), anyString(), eq("GET"), eq(200));

        SecurityContextHolder.clearContext();
        MockHttpServletRequest contextOnly = request("GET", "/api/v1/admin/admins");
        filter.doFilter(contextOnly, new MockHttpServletResponse(), (req, res) -> { });
    }

    @Test
    void extractsUserIdFromAuthenticatedDetailsAndMapsMediumSeverity() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("root", "n/a", java.util.List.of());
        authentication.setDetails(8L);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = request("GET", "/api/v1/admin/activity-logs");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        verify(accessLogService).logActivityLogsAccess(eq(8L), any(), eq("root"), anyString(), any(),
                anyString(), eq("GET"), eq(200));
        SeverityLevel severity = ReflectionTestUtils.invokeMethod(filter, "getSeverityLevel", "MEDIUM");
        assertThat(severity).isEqualTo(SeverityLevel.MEDIUM);
    }

    @Test
    void ignoresUnauthenticatedContextAndDoesNotReplaceKnownIdentityFromBearerHeader() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("guest", "n/a"));
        MockHttpServletRequest unauthenticated = request("GET", "/api/v1/admin/activity-logs");
        filter.doFilter(unauthenticated, new MockHttpServletResponse(), (req, res) -> { });

        UsernamePasswordAuthenticationToken authenticated =
                UsernamePasswordAuthenticationToken.authenticated("root", "n/a", java.util.List.of());
        authenticated.setDetails("not-a-long");
        SecurityContextHolder.getContext().setAuthentication(authenticated);
        MockHttpServletRequest known = request("GET", "/api/v1/admin/activity-logs");
        known.setAttribute("userId", 12L);
        known.addHeader("Authorization", "Bearer jwt");
        filter.doFilter(known, new MockHttpServletResponse(), (req, res) -> { });

        MockHttpServletRequest basic = request("GET", "/api/v1/admin/activity-logs");
        basic.addHeader("Authorization", "Basic value");
        filter.doFilter(basic, new MockHttpServletResponse(), (req, res) -> { });
    }

    @Test
    void extractsIdentityFromBearerTokenAndToleratesInvalidToken() throws Exception {
        MockHttpServletRequest bearer = request("GET", "/api/v1/admin/tokens");
        bearer.addHeader("Authorization", "Bearer jwt");
        when(jwtUtils.extractUserIdAsLong("jwt")).thenReturn(9L);
        when(jwtUtils.extractRole("jwt")).thenReturn("ADMIN");
        when(jwtUtils.extractUsername("jwt")).thenReturn("root");
        filter.doFilter(bearer, new MockHttpServletResponse(), (req, res) -> { });
        verify(accessLogService).logTokenManagementAccess(eq(9L), eq("ADMIN"), eq("root"),
                anyString(), any(), anyString(), eq("GET"), eq(200));

        MockHttpServletRequest invalid = request("GET", "/api/v1/admin/tokens");
        invalid.addHeader("Authorization", "Bearer broken");
        when(jwtUtils.extractUserIdAsLong("broken")).thenThrow(new IllegalArgumentException("bad jwt"));
        filter.doFilter(invalid, new MockHttpServletResponse(), (req, res) -> { });
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
