package dev.modularforge.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthErrorLogServiceTest {

    @Mock AuthenticationErrorLogRepository repository;
    private AuthErrorLogService service;

    @BeforeEach
    void setUp() {
        service = new AuthErrorLogService();
        ReflectionTestUtils.setField(service, "authErrorLogRepository", repository);
        ReflectionTestUtils.setField(service, "logAuthErrors", true);
    }

    @Test
    void storesDirectAndConvenienceErrorEvents() {
        service.logAuthError(AuthenticationErrorLog.ErrorType.ACCESS_DENIED, 1L, "admin", "root",
                "127.0.0.1", "JUnit", "/api", "GET", "denied", "read");
        service.log401("127.0.0.1", "JUnit", "/api", "GET", "missing");
        service.log403(1L, "admin", "root", "127.0.0.1", "JUnit", "/api", "GET", "denied", "read");
        service.log404(1L, "admin", "root", "127.0.0.1", "JUnit", "/api", "GET", "resource");
        service.log400(1L, "admin", "root", "127.0.0.1", "JUnit", "/api", "POST", "bad");
        service.log500(1L, "admin", "root", "127.0.0.1", "JUnit", "/api", "GET", "failed");
        service.logInvalidToken("127.0.0.1", "JUnit", "/api", "GET", "expired");
        service.logAccessDenied(1L, "admin", "root", "127.0.0.1", "JUnit", "/api", "GET", "denied");

        ArgumentCaptor<AuthenticationErrorLog> captor = ArgumentCaptor.forClass(AuthenticationErrorLog.class);
        verify(repository, org.mockito.Mockito.times(8)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AuthenticationErrorLog::getErrorType)
                .contains(AuthenticationErrorLog.ErrorType.UNAUTHORIZED_401,
                        AuthenticationErrorLog.ErrorType.FORBIDDEN_403,
                        AuthenticationErrorLog.ErrorType.NOT_FOUND_404,
                        AuthenticationErrorLog.ErrorType.BAD_REQUEST_400,
                        AuthenticationErrorLog.ErrorType.INTERNAL_SERVER_ERROR_500,
                        AuthenticationErrorLog.ErrorType.INVALID_TOKEN,
                        AuthenticationErrorLog.ErrorType.ACCESS_DENIED);
    }

    @Test
    void honorsDisabledLoggingAndContainsRepositoryFailure() {
        ReflectionTestUtils.setField(service, "logAuthErrors", false);
        service.log401("127.0.0.1", "JUnit", "/api", "GET", "missing");
        verify(repository, never()).save(any());

        ReflectionTestUtils.setField(service, "logAuthErrors", true);
        org.mockito.Mockito.doThrow(new IllegalStateException("database down")).when(repository).save(any());
        assertThatCode(() -> service.log401("127.0.0.1", "JUnit", "/api", "GET", "missing"))
                .doesNotThrowAnyException();
    }
}
