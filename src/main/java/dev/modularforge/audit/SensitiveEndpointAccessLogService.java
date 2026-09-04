package dev.modularforge.audit;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;
import dev.modularforge.shared.notification.NotificationGateway;

import dev.modularforge.audit.SensitiveEndpointAccessLog;
import dev.modularforge.audit.SensitiveEndpointAccessLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Service
@AuditModule
@Slf4j
public class SensitiveEndpointAccessLogService {

    @Autowired
    private SensitiveEndpointAccessLogRepository accessLogRepository;

    @Autowired
    private NotificationGateway emailService;

    @Value("${app.security.log-sensitive-access:true}")
    private boolean logSensitiveAccess;

    @Value("${app.security.sensitive-access-email-alert:true}")
    private boolean sendEmailAlert;

    @Value("${app.admin.email:admin@example.com}")
    private String adminEmail;
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccess(
            SensitiveEndpointAccessLog.SeverityLevel severity,
            Long userId,
            String role,
            String username,
            String ipAddress,
            String userAgent,
            String endpoint,
            String httpMethod,
            String endpointCategory,
            String description,
            Integer responseStatus
    ) {
        if (!logSensitiveAccess) {
            return;
        }

        try {
            boolean emailSent = false;
            SensitiveEndpointAccessLog accessLog = SensitiveEndpointAccessLog.builder()
                    .severity(severity)
                    .userId(userId)
                    .role(role)
                    .username(username)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .endpointCategory(endpointCategory)
                    .description(description)
                    .responseStatus(responseStatus)
                    .emailAlertSent(false)
                    .build();
            accessLogRepository.save(accessLog);
            if (sendEmailAlert && (severity == SensitiveEndpointAccessLog.SeverityLevel.HIGH
                    || severity == SensitiveEndpointAccessLog.SeverityLevel.CRITICAL)) {
                sendSecurityAlert(accessLog);
                emailSent = true;
                accessLog.setEmailAlertSent(true);
                accessLogRepository.save(accessLog);
            }
            logToConsole(severity, userId, username, ipAddress, endpoint, endpointCategory, description, emailSent);

        } catch (Exception e) {
            log.error("Failed to log sensitive endpoint access: {}", e.getMessage());
        }
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDatabaseBackupAccess(Long userId, String role, String username,
                                        String ipAddress, String userAgent, String endpoint,
                                        String httpMethod, Integer responseStatus) {
        logAccess(
                SensitiveEndpointAccessLog.SeverityLevel.CRITICAL,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "DATABASE_BACKUP",
                "Database backup operation accessed",
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdminManagementAccess(Long userId, String role, String username,
                                         String ipAddress, String userAgent, String endpoint,
                                         String httpMethod, Integer responseStatus) {
        logAccess(
                SensitiveEndpointAccessLog.SeverityLevel.HIGH,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "ADMIN_MANAGEMENT",
                "Admin management operation accessed",
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTokenManagementAccess(Long userId, String role, String username,
                                         String ipAddress, String userAgent, String endpoint,
                                         String httpMethod, Integer responseStatus) {
        logAccess(
                SensitiveEndpointAccessLog.SeverityLevel.HIGH,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "TOKEN_MANAGEMENT",
                "Token management operation accessed",
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log2FASettingsAccess(Long userId, String role, String username,
                                     String ipAddress, String userAgent, String endpoint,
                                     String httpMethod, Integer responseStatus) {
        logAccess(
                SensitiveEndpointAccessLog.SeverityLevel.MEDIUM,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "2FA_SETTINGS",
                "2FA settings accessed",
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivityLogsAccess(Long userId, String role, String username,
                                      String ipAddress, String userAgent, String endpoint,
                                      String httpMethod, Integer responseStatus) {
        logAccess(
                SensitiveEndpointAccessLog.SeverityLevel.MEDIUM,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "ACTIVITY_LOGS",
                "Activity logs accessed",
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logErrorLogsAccess(Long userId, String role, String username,
                                   String ipAddress, String userAgent, String endpoint,
                                   String httpMethod, Integer responseStatus) {
        logAccess(
                SensitiveEndpointAccessLog.SeverityLevel.MEDIUM,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "ERROR_LOGS",
                "Error logs accessed",
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuspiciousFileAccess(Long userId, String role, String username,
                                        String ipAddress, String userAgent, String endpoint,
                                        String httpMethod, Integer responseStatus,
                                        SensitiveEndpointAccessLog.SeverityLevel severity) {
        String description = "SECURITY ALERT: Successful access to suspicious file/path - " + endpoint;
        logAccess(
                severity,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "SUSPICIOUS_FILE_ACCESS",
                description,
                responseStatus
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuspiciousPathProbe(Long userId, String role, String username,
                                       String ipAddress, String userAgent, String endpoint,
                                       String httpMethod, Integer responseStatus,
                                       SensitiveEndpointAccessLog.SeverityLevel severity) {
        String description = "SECURITY ALERT: Successful access to commonly probed path - " + endpoint;
        logAccess(
                severity,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "SUSPICIOUS_PATH_PROBE",
                description,
                responseStatus
        );
    }
    private void sendSecurityAlert(SensitiveEndpointAccessLog accessLog) {
        try {
            String subject = String.format("[%s] Sensitive Endpoint Access Alert - %s",
                    accessLog.getSeverity().name(), accessLog.getEndpointCategory());

            String body = buildAlertEmailBody(accessLog);

            emailService.sendSystemNotificationEmail(subject, body);
            log.info("Security alert email sent for sensitive endpoint access: {}", accessLog.getEndpoint());

        } catch (Exception e) {
            log.error("Failed to send security alert email: {}", e.getMessage());
        }
    }
    private String buildAlertEmailBody(SensitiveEndpointAccessLog accessLog) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>");
        sb.append("<style>");
        sb.append("body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }");
        sb.append(".container { max-width: 600px; margin: 0 auto; background-color: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        sb.append(".header { padding: 20px; text-align: center; }");
        sb.append(".header.critical { background-color: #dc3545; color: white; }");
        sb.append(".header.high { background-color: #fd7e14; color: white; }");
        sb.append(".content { padding: 20px; }");
        sb.append(".field { margin-bottom: 15px; }");
        sb.append(".field-label { font-weight: bold; color: #333; margin-bottom: 5px; }");
        sb.append(".field-value { color: #666; padding: 8px; background-color: #f9f9f9; border-radius: 4px; }");
        sb.append(".footer { padding: 15px; background-color: #f5f5f5; text-align: center; font-size: 12px; color: #666; }");
        sb.append("</style></head><body>");

        sb.append("<div class='container'>");
        String headerClass = accessLog.getSeverity() == SensitiveEndpointAccessLog.SeverityLevel.CRITICAL ? "critical" : "high";
        sb.append("<div class='header ").append(headerClass).append("'>");
        sb.append("<h2>🚨 Security Alert</h2>");
        sb.append("<p>").append(accessLog.getSeverity().getDescription()).append("</p>");
        sb.append("</div>");
        sb.append("<div class='content'>");

        sb.append("<div class='field'>");
        sb.append("<div class='field-label'>Timestamp</div>");
        sb.append("<div class='field-value'>").append(LocalDateTime.now().format(formatter)).append("</div>");
        sb.append("</div>");

        sb.append("<div class='field'>");
        sb.append("<div class='field-label'>Category</div>");
        sb.append("<div class='field-value'>").append(accessLog.getEndpointCategory()).append("</div>");
        sb.append("</div>");

        sb.append("<div class='field'>");
        sb.append("<div class='field-label'>Endpoint</div>");
        sb.append("<div class='field-value'>").append(accessLog.getHttpMethod()).append(" ").append(accessLog.getEndpoint()).append("</div>");
        sb.append("</div>");

        if (accessLog.getUserId() != null) {
            sb.append("<div class='field'>");
            sb.append("<div class='field-label'>User ID</div>");
            sb.append("<div class='field-value'>").append(accessLog.getUserId()).append("</div>");
            sb.append("</div>");
        }

        if (accessLog.getUsername() != null && !accessLog.getUsername().isEmpty()) {
            sb.append("<div class='field'>");
            sb.append("<div class='field-label'>Username</div>");
            sb.append("<div class='field-value'>").append(accessLog.getUsername()).append("</div>");
            sb.append("</div>");
        }

        if (accessLog.getRole() != null) {
            sb.append("<div class='field'>");
            sb.append("<div class='field-label'>Role</div>");
            sb.append("<div class='field-value'>").append(accessLog.getRole()).append("</div>");
            sb.append("</div>");
        }

        sb.append("<div class='field'>");
        sb.append("<div class='field-label'>IP Address</div>");
        sb.append("<div class='field-value'>").append(accessLog.getIpAddress() != null ? accessLog.getIpAddress() : "Unknown").append("</div>");
        sb.append("</div>");

        if (accessLog.getResponseStatus() != null) {
            sb.append("<div class='field'>");
            sb.append("<div class='field-label'>Response Status</div>");
            sb.append("<div class='field-value'>").append(accessLog.getResponseStatus()).append("</div>");
            sb.append("</div>");
        }

        if (accessLog.getDescription() != null) {
            sb.append("<div class='field'>");
            sb.append("<div class='field-label'>Description</div>");
            sb.append("<div class='field-value'>").append(accessLog.getDescription()).append("</div>");
            sb.append("</div>");
        }

        sb.append("</div>");
        sb.append("<div class='footer'>");
        sb.append("<p>This is an automated security alert from your application.</p>");
        sb.append("<p>Please review this access immediately if unexpected.</p>");
        sb.append("</div>");

        sb.append("</div></body></html>");

        return sb.toString();
    }
    private void logToConsole(
            SensitiveEndpointAccessLog.SeverityLevel severity,
            Long userId,
            String username,
            String ipAddress,
            String endpoint,
            String category,
            String description,
            boolean emailSent
    ) {
        String message = "Sensitive access severity={} category={} userId={} ip={} endpoint={} alertSent={}";
        if (severity == SensitiveEndpointAccessLog.SeverityLevel.CRITICAL) {
            log.error(message, severity, category, userId, ipAddress, endpoint, emailSent);
        } else if (severity == SensitiveEndpointAccessLog.SeverityLevel.HIGH) {
            log.warn(message, severity, category, userId, ipAddress, endpoint, emailSent);
        } else {
            log.info(message, severity, category, userId, ipAddress, endpoint, emailSent);
        }
    }
}
