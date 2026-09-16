package dev.modularforge.audit;

import dev.modularforge.shared.audit.AuthenticationErrorAudit;

import dev.modularforge.audit.AuthenticationErrorLog;
import dev.modularforge.audit.AuthenticationErrorLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Service
@AuditModule
@Slf4j
public class AuthErrorLogService implements AuthenticationErrorAudit {

    @Autowired
    private AuthenticationErrorLogRepository authErrorLogRepository;

    @Value("${app.security.log-auth-errors:true}")
    private boolean logAuthErrors;
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuthError(
            AuthenticationErrorLog.ErrorType errorType,
            Long userId,
            String role,
            String username,
            String ipAddress,
            String userAgent,
            String endpoint,
            String httpMethod,
            String errorMessage,
            String attemptedAction
    ) {
        if (!logAuthErrors) {
            return;
        }

        try {
            AuthenticationErrorLog errorLog = AuthenticationErrorLog.builder()
                    .errorType(errorType)
                    .userId(userId)
                    .role(role)
                    .username(username)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .endpoint(endpoint)
                    .httpMethod(httpMethod)
                    .errorMessage(errorMessage)
                    .attemptedAction(attemptedAction)
                    .build();

            authErrorLogRepository.save(errorLog);
            logToConsole(errorType, userId, username, ipAddress, endpoint, errorMessage);

        } catch (Exception e) {
            log.error("Failed to log authentication error: {}", e.getMessage());
        }
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log401(String ipAddress, String userAgent, String endpoint, String httpMethod, String errorMessage) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.UNAUTHORIZED_401,
                null,
                null,
                null,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                "Authentication required"
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log403(Long userId, String role, String username, String ipAddress, String userAgent,
                       String endpoint, String httpMethod, String errorMessage, String attemptedAction) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.FORBIDDEN_403,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                attemptedAction
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log404(Long userId, String role, String username, String ipAddress, String userAgent,
                       String endpoint, String httpMethod, String resourceType) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.NOT_FOUND_404,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                "Resource not found: " + resourceType,
                "Attempted to access non-existent resource"
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log400(Long userId, String role, String username, String ipAddress, String userAgent,
                       String endpoint, String httpMethod, String errorMessage) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.BAD_REQUEST_400,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                "Bad request"
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log500(Long userId, String role, String username, String ipAddress, String userAgent,
                       String endpoint, String httpMethod, String errorMessage) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.INTERNAL_SERVER_ERROR_500,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                errorMessage,
                "Internal server error"
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInvalidToken(String ipAddress, String userAgent, String endpoint, String httpMethod, String tokenError) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.INVALID_TOKEN,
                null,
                null,
                null,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                tokenError,
                "Invalid or expired authentication token"
        );
    }
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccessDenied(Long userId, String role, String username, String ipAddress, String userAgent,
                                String endpoint, String httpMethod, String reason) {
        logAuthError(
                AuthenticationErrorLog.ErrorType.ACCESS_DENIED,
                userId,
                role,
                username,
                ipAddress,
                userAgent,
                endpoint,
                httpMethod,
                reason,
                "Access denied to protected resource"
        );
    }
    private void logToConsole(
            AuthenticationErrorLog.ErrorType errorType,
            Long userId,
            String username,
            String ipAddress,
            String endpoint,
            String errorMessage
    ) {
        log.warn("Authentication error type={} userId={} ip={} endpoint={} message={}",
                errorType, userId, ipAddress, endpoint, errorMessage);
    }
}
