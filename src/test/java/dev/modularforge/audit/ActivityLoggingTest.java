package dev.modularforge.audit;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityLoggingTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminLoggerStoresDetailedAndConvenienceEvents() {
        AdminActivityLogRepository repository = org.mockito.Mockito.mock(AdminActivityLogRepository.class);
        AdminActivityLogger logger = new AdminActivityLogger(repository, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        logger.logActivity(1L, "READ", "Thing", "1", Map.of("key", "value"), request);
        logger.logActivity(1L, "READ", "Thing", "1", null);
        logger.logActivity(1L, "READ", "Thing", "1", Map.of(), null);
        logger.logCreate(1L, "Thing", "1", Map.of("id", 1), request);
        logger.logCreate(1L, "Thing", "1", null, request);
        logger.logUpdate(1L, "Thing", "1", Map.of("name", "new"), request);
        logger.logUpdate(1L, "Thing", "1", null, request);
        logger.logUpdate(1L, "Thing", "1", Map.of(), request);
        logger.logDelete(1L, "Thing", "1", request);
        logger.logRead(1L, "Thing", "1", request);
        logger.logActivate(1L, "Thing", "1", request);
        logger.logDeactivate(1L, "Thing", "1", request);

        verify(repository, times(12)).save(any(AdminActivityLog.class));
    }

    @Test
    void adminLoggerContainsSerializationAndRepositoryFailures() throws Exception {
        AdminActivityLogRepository repository = org.mockito.Mockito.mock(AdminActivityLogRepository.class);
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenAnswer(call -> { throw new IOException("bad json"); });
        AdminActivityLogger logger = new AdminActivityLogger(repository, mapper);

        assertThatCode(() -> logger.logActivity(1L, "READ", "Thing", "1", Map.of("x", 1), null))
                .doesNotThrowAnyException();
        verify(repository, never()).save(any());

        AdminActivityLogger repositoryFailure = new AdminActivityLogger(repository, new ObjectMapper());
        org.mockito.Mockito.doThrow(new IllegalStateException("database down")).when(repository).save(any());
        assertThatCode(() -> repositoryFailure.logActivity(1L, "READ", "Thing", "1", null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void userLoggerStoresEveryConvenienceEventAndHandlesOptionalValues() {
        UserActivityLogRepository repository = org.mockito.Mockito.mock(UserActivityLogRepository.class);
        UserActivityLogger logger = new UserActivityLogger(repository, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/profile");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        logger.logActivity(1L, "USER", "DIRECT", "Thing", "1", Map.of("x", 1), false, "failed", request);
        logger.logActivity(1L, null, "DIRECT", "Thing", "1", Map.of(), true, null, null);
        logger.logActivity(1L, "user", "OVERLOAD", "Thing", "1", Map.of("x", 1), request);
        logger.logActivity(1L, "user", "SHORT", request);
        logger.logLogin(1L, "user", false, "bad password", request);
        logger.logLogin(1L, "user", false, null, request);
        logger.logLoginSuccess(1L, "user", request);
        logger.logLoginFailure(1L, "user", "bad", request);
        logger.logRegister(1L, "user", false, "duplicate", request);
        logger.logRegister(1L, "user", false, null, request);
        logger.logRegister(null, "user", true, null, request);
        logger.logLogout(1L, "user", request);
        logger.logPasswordResetRequest(1L, "user", request);
        logger.logPasswordResetComplete(1L, "user", true, request);
        logger.logPasswordChange(1L, "user", true, request);
        logger.logEmailVerification(1L, "user", true, request);
        logger.logProfileUpdate(1L, "user", Map.of("name", "Ada"), request);
        logger.logProfileUpdate(1L, "user", null, request);
        logger.logProfileUpdate(1L, "user", Map.of(), request);
        logger.logProfilePictureUpload(1L, "user", true, request);
        logger.logProfilePictureDelete(1L, "user", request);
        logger.logAccountDeactivated(1L, "user", 2L, request);
        logger.logAccountReactivated(1L, "user", 2L, request);
        logger.logVerificationEmailResent(1L, "user", request);
        logger.logSessionRefresh(1L, "user", request);
        logger.logRead(1L, "user", "Thing", "1", request);
        logger.logCreate(1L, "user", "Thing", "1", request);
        logger.logUpdate(1L, "user", "Thing", "1", request);
        logger.logDelete(1L, "user", "Thing", "1", request);

        verify(repository, times(29)).save(any(UserActivityLog.class));
    }

    @Test
    void userLoggerContainsSerializationAndRepositoryFailures() throws Exception {
        UserActivityLogRepository repository = org.mockito.Mockito.mock(UserActivityLogRepository.class);
        ObjectMapper mapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenAnswer(call -> { throw new IOException("bad json"); });
        UserActivityLogger logger = new UserActivityLogger(repository, mapper);
        assertThatCode(() -> logger.logActivity(1L, "user", "READ", null, null,
                Map.of("x", 1), true, null, null)).doesNotThrowAnyException();

        UserActivityLogger repositoryFailure = new UserActivityLogger(repository, new ObjectMapper());
        org.mockito.Mockito.doThrow(new IllegalStateException("database down")).when(repository).save(any());
        assertThatCode(() -> repositoryFailure.logActivity(1L, "user", "READ", null, null,
                null, true, null, null)).doesNotThrowAnyException();
    }

    @Test
    void interceptorRejectsIneligibleRequestsAndLogsEligibleOnes() {
        UserActivityLogger logger = org.mockito.Mockito.mock(UserActivityLogger.class);
        UserActivityLoggingInterceptor interceptor = new UserActivityLoggingInterceptor(logger);
        HandlerMethod handler = org.mockito.Mockito.mock(HandlerMethod.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest async = new MockHttpServletRequest();
        async.setDispatcherType(DispatcherType.ASYNC);
        interceptor.afterCompletion(async, response, handler, null);
        interceptor.afterCompletion(new MockHttpServletRequest(), response, new Object(), null);
        interceptor.afterCompletion(new MockHttpServletRequest(), response, handler, null);

        var unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated("user", "n/a");
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        interceptor.afterCompletion(new MockHttpServletRequest(), response, handler, null);

        var admin = UsernamePasswordAuthenticationToken.authenticated("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        admin.setDetails(1L);
        SecurityContextHolder.getContext().setAuthentication(admin);
        interceptor.afterCompletion(new MockHttpServletRequest(), response, handler, null);

        var wrongDetails = UsernamePasswordAuthenticationToken.authenticated("user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        wrongDetails.setDetails("not-a-long");
        SecurityContextHolder.getContext().setAuthentication(wrongDetails);
        interceptor.afterCompletion(new MockHttpServletRequest(), response, handler, null);
        verify(logger, never()).logActivity(any(), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any());

        var user = UsernamePasswordAuthenticationToken.authenticated("user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        user.setDetails(7L);
        SecurityContextHolder.getContext().setAuthentication(user);
        MockHttpServletRequest eligible = new MockHttpServletRequest("GET", "/api/profile");
        eligible.setQueryString("page=1");
        response.setStatus(500);
        interceptor.afterCompletion(eligible, response, handler, new IllegalStateException("failed"));

        verify(logger).logActivity(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.eq("GET /api/profile"), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq("HTTP 500"),
                org.mockito.ArgumentMatchers.eq(eligible));

        MockHttpServletRequest success = new MockHttpServletRequest("POST", "/api/profile");
        success.setQueryString(" ");
        response.setStatus(204);
        interceptor.afterCompletion(success, response, handler, null);
    }

    @Test
    void interceptorContainsLoggerFailures() {
        UserActivityLogger logger = org.mockito.Mockito.mock(UserActivityLogger.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("logger down")).when(logger)
                .logActivity(any(), any(), any(), any(), any(), any(),
                        org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
        UserActivityLoggingInterceptor interceptor = new UserActivityLoggingInterceptor(logger);
        var user = UsernamePasswordAuthenticationToken.authenticated("user", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        user.setDetails(7L);
        SecurityContextHolder.getContext().setAuthentication(user);

        assertThatCode(() -> interceptor.afterCompletion(new MockHttpServletRequest(),
                new MockHttpServletResponse(), org.mockito.Mockito.mock(HandlerMethod.class), null))
                .doesNotThrowAnyException();
    }

    @Test
    void cleanupJobDeletesRowsOlderThanRetention() {
        UserActivityLogRepository repository = org.mockito.Mockito.mock(UserActivityLogRepository.class);
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(3);
        ActivityLogCleanupScheduledService service = new ActivityLogCleanupScheduledService(repository);
        ReflectionTestUtils.setField(service, "retentionDays", 30);

        service.cleanupOldActivityLogs();

        verify(repository).deleteByCreatedAtBefore(any());
    }
}
