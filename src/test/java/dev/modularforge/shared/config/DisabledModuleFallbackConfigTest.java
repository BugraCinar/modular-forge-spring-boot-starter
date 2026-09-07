package dev.modularforge.shared.config;

import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import dev.modularforge.shared.audit.UserActivityAudit;
import dev.modularforge.shared.notification.NotificationGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DisabledModuleFallbackConfigTest {

    private final DisabledModuleFallbackConfig config = new DisabledModuleFallbackConfig();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void disabledUserAuditAcceptsEveryEventWithoutSideEffects() {
        UserActivityAudit audit = config.disabledUserActivityAudit();

        assertThatCode(() -> {
            audit.logRegister(1L, "user", true, null, request);
            audit.logLoginFailure(1L, "user", "reason", request);
            audit.logLoginSuccess(1L, "user", request);
            audit.logPasswordResetRequest(1L, "user", request);
            audit.logPasswordResetComplete(1L, "user", true, request);
            audit.logEmailVerification(1L, "user", true, request);
        }).doesNotThrowAnyException();
    }

    @Test
    void disabledAdminAuditAcceptsEveryEventWithoutSideEffects() {
        AdminActivityAudit audit = config.disabledAdminActivityAudit();

        assertThatCode(() -> {
            audit.logActivity(1L, "ACTION", "TYPE", "id", Map.of("field", "value"), request);
            audit.logCreate(1L, "TYPE", "id", Map.of("field", "value"), request);
            audit.logUpdate(1L, "TYPE", "id", Map.of("field", "value"), request);
            audit.logDelete(1L, "TYPE", "id", request);
            audit.logActivate(1L, "TYPE", "id", request);
            audit.logDeactivate(1L, "TYPE", "id", request);
        }).doesNotThrowAnyException();
    }

    @Test
    void disabledAuthenticationAuditAcceptsEveryErrorWithoutSideEffects() {
        AuthenticationErrorAudit audit = config.disabledAuthenticationErrorAudit();

        assertThatCode(() -> {
            audit.log401("message", "uri", "method", "ip", "agent");
            audit.log403(1L, "user", "message", "uri", "method", "ip", "agent", "role", "resource");
            audit.log404(1L, "user", "message", "uri", "method", "ip", "agent", "resource");
            audit.log400(1L, "user", "message", "uri", "method", "ip", "agent", "resource");
            audit.log500(1L, "user", "message", "uri", "method", "ip", "agent", "resource");
            audit.logAccessDenied(1L, "user", "message", "uri", "method", "ip", "agent", "resource");
        }).doesNotThrowAnyException();
    }

    @Test
    void missingNotificationProviderFailsClearlyForEveryMessageType() {
        NotificationGateway gateway = config.missingNotificationGateway();

        assertUnavailable(() -> gateway.sendVerificationEmail("a@b.test", "name", "token"));
        assertUnavailable(() -> gateway.sendPasswordResetEmail("a@b.test", "name", "token"));
        assertUnavailable(() -> gateway.sendEmailChangeVerificationEmail("a@b.test", "name", "token"));
        assertUnavailable(() -> gateway.sendSystemNotificationEmail("subject", "body"));
    }

    private void assertUnavailable(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No notification provider is configured");
    }
}
