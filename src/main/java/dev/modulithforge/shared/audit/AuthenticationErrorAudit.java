package dev.modulithforge.shared.audit;

public interface AuthenticationErrorAudit {

    void log401(String ipAddress, String userAgent, String endpoint, String httpMethod, String errorMessage);

    void log403(Long userId, String role, String username, String ipAddress, String userAgent,
                String endpoint, String httpMethod, String errorMessage, String attemptedAction);

    void log404(Long userId, String role, String username, String ipAddress, String userAgent,
                String endpoint, String httpMethod, String resourceType);

    void log400(Long userId, String role, String username, String ipAddress, String userAgent,
                String endpoint, String httpMethod, String errorMessage);

    void log500(Long userId, String role, String username, String ipAddress, String userAgent,
                String endpoint, String httpMethod, String errorMessage);

    void logAccessDenied(Long userId, String role, String username, String ipAddress, String userAgent,
                         String endpoint, String httpMethod, String reason);
}
