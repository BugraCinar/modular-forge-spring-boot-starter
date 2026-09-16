package dev.modularforge.shared.config;

import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import dev.modularforge.shared.audit.UserActivityAudit;
import dev.modularforge.shared.notification.NotificationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DisabledModuleFallbackConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.modules.audit", name = "enabled", havingValue = "false")
    UserActivityAudit disabledUserActivityAudit() {
        return new UserActivityAudit() {
            public void logRegister(Long a, String b, boolean c, String d, jakarta.servlet.http.HttpServletRequest e) { }
            public void logLoginFailure(Long a, String b, String c, jakarta.servlet.http.HttpServletRequest d) { }
            public void logLoginSuccess(Long a, String b, jakarta.servlet.http.HttpServletRequest c) { }
            public void logPasswordResetRequest(Long a, String b, jakarta.servlet.http.HttpServletRequest c) { }
            public void logPasswordResetComplete(Long a, String b, boolean c, jakarta.servlet.http.HttpServletRequest d) { }
            public void logEmailVerification(Long a, String b, boolean c, jakarta.servlet.http.HttpServletRequest d) { }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.modules.audit", name = "enabled", havingValue = "false")
    AdminActivityAudit disabledAdminActivityAudit() {
        return new AdminActivityAudit() {
            public void logActivity(Long a, String b, String c, String d, java.util.Map<String, Object> e, jakarta.servlet.http.HttpServletRequest f) { }
            public void logCreate(Long a, String b, String c, Object d, jakarta.servlet.http.HttpServletRequest e) { }
            public void logUpdate(Long a, String b, String c, java.util.Map<String, Object> d, jakarta.servlet.http.HttpServletRequest e) { }
            public void logDelete(Long a, String b, String c, jakarta.servlet.http.HttpServletRequest d) { }
            public void logActivate(Long a, String b, String c, jakarta.servlet.http.HttpServletRequest d) { }
            public void logDeactivate(Long a, String b, String c, jakarta.servlet.http.HttpServletRequest d) { }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.modules.audit", name = "enabled", havingValue = "false")
    AuthenticationErrorAudit disabledAuthenticationErrorAudit() {
        return new AuthenticationErrorAudit() {
            public void log401(String a, String b, String c, String d, String e) { }
            public void log403(Long a, String b, String c, String d, String e, String f, String g, String h, String i) { }
            public void log404(Long a, String b, String c, String d, String e, String f, String g, String h) { }
            public void log400(Long a, String b, String c, String d, String e, String f, String g, String h) { }
            public void log500(Long a, String b, String c, String d, String e, String f, String g, String h) { }
            public void logAccessDenied(Long a, String b, String c, String d, String e, String f, String g, String h) { }
        };
    }

    @Bean
    @ConditionalOnMissingBean(NotificationGateway.class)
    NotificationGateway missingNotificationGateway() {
        return new NotificationGateway() {
            private IllegalStateException unavailable() {
                return new IllegalStateException("No notification provider is configured");
            }
            public void sendVerificationEmail(String a, String b, String c) { throw unavailable(); }
            public void sendPasswordResetEmail(String a, String b, String c) { throw unavailable(); }
            public void sendEmailChangeVerificationEmail(String a, String b, String c) { throw unavailable(); }
            public void sendSystemNotificationEmail(String a, String b) { throw unavailable(); }
            public void sendEmailChangeNotice(String a, String b) { throw unavailable(); }
        };
    }
}
