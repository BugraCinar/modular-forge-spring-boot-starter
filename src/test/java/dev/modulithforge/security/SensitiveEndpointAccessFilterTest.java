package dev.modulithforge.security;

import dev.modulithforge.audit.SensitiveEndpointAccessFilter;
import dev.modulithforge.audit.SensitiveEndpointAccessLog;

import dev.modulithforge.audit.SensitiveEndpointAccessLog.SeverityLevel;
import dev.modulithforge.audit.SensitiveEndpointAccessLogService;
import dev.modulithforge.shared.web.ApiRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

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

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
