package dev.modularforge.audit;

import dev.modularforge.shared.notification.NotificationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveEndpointAccessLogServiceTest {

    @Mock
    private SensitiveEndpointAccessLogRepository repository;
    @Mock
    private NotificationGateway notifications;

    private SensitiveEndpointAccessLogService service;

    @BeforeEach
    void setUp() {
        service = new SensitiveEndpointAccessLogService();
        ReflectionTestUtils.setField(service, "accessLogRepository", repository);
        ReflectionTestUtils.setField(service, "emailService", notifications);
        ReflectionTestUtils.setField(service, "logSensitiveAccess", true);
        ReflectionTestUtils.setField(service, "sendEmailAlert", true);
        ReflectionTestUtils.setField(service, "adminEmail", "security@example.com");
        lenient().when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void skipsLoggingWhenFeatureIsDisabled() {
        ReflectionTestUtils.setField(service, "logSensitiveAccess", false);

        service.logAccess(SensitiveEndpointAccessLog.SeverityLevel.CRITICAL, 1L, "ADMIN", "root",
                "127.0.0.1", "agent", "/admin", "GET", "ADMIN", "opened", 200);

        verify(repository, never()).save(any());
        verify(notifications, never()).sendSystemNotificationEmail(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SensitiveEndpointAccessLog.SeverityLevel.class,
            names = {"LOW", "MEDIUM"})
    void storesLowerSeverityEventsWithoutSendingEmail(SensitiveEndpointAccessLog.SeverityLevel severity) {
        service.logAccess(severity, null, null, "", null, "agent", "/profile", "GET",
                "PROFILE", null, null);

        ArgumentCaptor<SensitiveEndpointAccessLog> log = ArgumentCaptor.forClass(SensitiveEndpointAccessLog.class);
        verify(repository).save(log.capture());
        assertThat(log.getValue().getSeverity()).isEqualTo(severity);
        assertThat(log.getValue().getEmailAlertSent()).isFalse();
        verify(notifications, never()).sendSystemNotificationEmail(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SensitiveEndpointAccessLog.SeverityLevel.class,
            names = {"HIGH", "CRITICAL"})
    void sendsDetailedEmailForHighSeverityEvents(SensitiveEndpointAccessLog.SeverityLevel severity) {
        service.logAccess(severity, 42L, "ADMIN", "root", "203.0.113.4", "browser",
                "/api/admin/backups", "POST", "DATABASE_BACKUP", "Backup requested", 202);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendSystemNotificationEmail(subject.capture(), body.capture());
        verify(repository, times(2)).save(any());
        assertThat(subject.getValue()).contains(severity.name(), "DATABASE_BACKUP");
        assertThat(body.getValue())
                .contains("42", "root", "ADMIN", "203.0.113.4", "POST /api/admin/backups",
                        "202", "Backup requested")
                .contains(severity == SensitiveEndpointAccessLog.SeverityLevel.CRITICAL ? "header critical" : "header high");
    }

    @Test
    void emailCanBeDisabledForHighSeverityEvents() {
        ReflectionTestUtils.setField(service, "sendEmailAlert", false);

        service.logAccess(SensitiveEndpointAccessLog.SeverityLevel.HIGH, 1L, "ADMIN", "root",
                "127.0.0.1", "agent", "/admin", "GET", "ADMIN", "opened", 200);

        verify(repository).save(any());
        verify(notifications, never()).sendSystemNotificationEmail(any(), any());
    }

    @Test
    void highSeverityEmailOmitsAnEmptyUsername() {
        service.logAccess(SensitiveEndpointAccessLog.SeverityLevel.HIGH, null, null, "",
                null, null, "/admin", "GET", "ADMIN", null, null);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendSystemNotificationEmail(any(), body.capture());
        assertThat(body.getValue()).doesNotContain("Username</div>");
    }

    @Test
    void toleratesNotificationAndRepositoryFailures() {
        doThrow(new IllegalStateException("mail unavailable"))
                .when(notifications).sendSystemNotificationEmail(any(), any());

        service.logAccess(SensitiveEndpointAccessLog.SeverityLevel.HIGH, null, null, null,
                null, null, "/admin", "GET", "ADMIN", null, null);

        verify(repository, times(2)).save(any());

        reset(repository, notifications);
        doThrow(new IllegalStateException("database unavailable")).when(repository).save(any());

        service.logAccess(SensitiveEndpointAccessLog.SeverityLevel.CRITICAL, 1L, "ADMIN", "root",
                "127.0.0.1", "agent", "/admin", "GET", "ADMIN", "opened", 200);

        verify(notifications, never()).sendSystemNotificationEmail(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "admin", "token", "2fa", "activity", "error", "file", "probe"})
    void convenienceMethodsMapToExpectedCategories(String kind) {
        switch (kind) {
            case "database" -> service.logDatabaseBackupAccess(1L, "ADMIN", "root", "ip", "ua", "/db", "POST", 200);
            case "admin" -> service.logAdminManagementAccess(1L, "ADMIN", "root", "ip", "ua", "/admins", "GET", 200);
            case "token" -> service.logTokenManagementAccess(1L, "ADMIN", "root", "ip", "ua", "/tokens", "DELETE", 204);
            case "2fa" -> service.log2FASettingsAccess(1L, "USER", "alice", "ip", "ua", "/2fa", "PUT", 200);
            case "activity" -> service.logActivityLogsAccess(1L, "ADMIN", "root", "ip", "ua", "/activity", "GET", 200);
            case "error" -> service.logErrorLogsAccess(1L, "ADMIN", "root", "ip", "ua", "/errors", "GET", 200);
            case "file" -> service.logSuspiciousFileAccess(1L, "USER", "alice", "ip", "ua", "/.env", "GET", 200,
                    SensitiveEndpointAccessLog.SeverityLevel.MEDIUM);
            case "probe" -> service.logSuspiciousPathProbe(1L, "USER", "alice", "ip", "ua", "/wp-admin", "GET", 200,
                    SensitiveEndpointAccessLog.SeverityLevel.LOW);
            default -> throw new AssertionError("Unexpected case: " + kind);
        }

        ArgumentCaptor<SensitiveEndpointAccessLog> captured = ArgumentCaptor.forClass(SensitiveEndpointAccessLog.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captured.capture());
        assertThat(captured.getAllValues().getFirst().getEndpointCategory()).isNotBlank();
    }
}
