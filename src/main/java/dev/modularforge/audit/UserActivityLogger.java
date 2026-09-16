package dev.modularforge.audit;

import dev.modularforge.identity.model.User;
import dev.modularforge.shared.audit.UserActivityAudit;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.audit.UserActivityLog;
import dev.modularforge.audit.UserActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
@Service
@AuditModule
@RequiredArgsConstructor
@Slf4j
public class UserActivityLogger implements UserActivityAudit {

    private final UserActivityLogRepository userActivityLogRepository;
    private final ObjectMapper objectMapper;
    @Async("loggingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(Long userId, String role, String action,
                           String resourceType, String resourceId,
                           Map<String, Object> details, boolean success,
                           String failureReason, HttpServletRequest request) {
        try {
            UserActivityLog activityLog = new UserActivityLog();
            activityLog.setUserId(userId);
            activityLog.setRole(role != null ? role.toLowerCase() : "unknown");
            activityLog.setAction(action);
            activityLog.setResourceType(resourceType);
            activityLog.setResourceId(resourceId);
            activityLog.setSuccess(success);
            activityLog.setFailureReason(failureReason);
            if (details != null && !details.isEmpty()) {
                activityLog.setDetails(objectMapper.writeValueAsString(details));
            }
            if (request != null) {
                activityLog.setIpAddress(getClientIpAddress(request));
                activityLog.setUserAgent(request.getHeader("User-Agent"));
            }

            userActivityLogRepository.save(activityLog);
            log.debug("Logged user activity: userId={}, role={}, action={}, success={}",
                    userId, role, action, success);

        } catch (Exception e) {
            log.error("Failed to log user activity for userId: {}, action: {}", userId, action, e);
        }
    }
    public void logActivity(Long userId, String role, String action,
                           String resourceType, String resourceId,
                           Map<String, Object> details, HttpServletRequest request) {
        logActivity(userId, role, action, resourceType, resourceId, details, true, null, request);
    }
    public void logActivity(Long userId, String role, String action, HttpServletRequest request) {
        logActivity(userId, role, action, null, null, null, true, null, request);
    }
    public void logLogin(Long userId, String role, boolean success, String failureReason, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "login_attempt");
        if (!success && failureReason != null) {
            details.put("reason", failureReason);
        }
        logActivity(userId, role, "LOGIN", "Authentication", null, details, success, failureReason, request);
    }
    public void logLoginSuccess(Long userId, String role, HttpServletRequest request) {
        logLogin(userId, role, true, null, request);
    }
    public void logLoginFailure(Long userId, String role, String reason, HttpServletRequest request) {
        logLogin(userId, role, false, reason, request);
    }
    public void logRegister(Long userId, String role, boolean success, String failureReason, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "registration");
        if (!success && failureReason != null) {
            details.put("reason", failureReason);
        }
        logActivity(userId, role, "REGISTER", "User", userId != null ? userId.toString() : null, details, success, failureReason, request);
    }
    public void logLogout(Long userId, String role, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "logout");
        logActivity(userId, role, "LOGOUT", "Authentication", null, details, true, null, request);
    }
    public void logPasswordResetRequest(Long userId, String role, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "password_reset_requested");
        logActivity(userId, role, "PASSWORD_RESET_REQUEST", "Security", null, details, true, null, request);
    }
    public void logPasswordResetComplete(Long userId, String role, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "password_reset_completed");
        logActivity(userId, role, "PASSWORD_RESET_COMPLETE", "Security", null, details, success, null, request);
    }
    public void logPasswordChange(Long userId, String role, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "password_changed");
        logActivity(userId, role, "PASSWORD_CHANGE", "Security", null, details, success, null, request);
    }
    public void logEmailVerification(Long userId, String role, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "email_verified");
        logActivity(userId, role, "EMAIL_VERIFICATION", "Security", null, details, success, null, request);
    }
    public void logProfileUpdate(Long userId, String role, Map<String, Object> changedFields, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "profile_updated");
        if (changedFields != null && !changedFields.isEmpty()) {
            details.put("changed_fields", changedFields.keySet());
        }
        logActivity(userId, role, "PROFILE_UPDATE", "Profile", userId.toString(), details, true, null, request);
    }
    public void logProfilePictureUpload(Long userId, String role, boolean success, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "profile_picture_uploaded");
        logActivity(userId, role, "PROFILE_PICTURE_UPLOAD", "Profile", userId.toString(), details, success, null, request);
    }
    public void logProfilePictureDelete(Long userId, String role, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "profile_picture_deleted");
        logActivity(userId, role, "PROFILE_PICTURE_DELETE", "Profile", userId.toString(), details, true, null, request);
    }
    public void logAccountDeactivated(Long userId, String role, Long deactivatedBy, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "account_deactivated");
        details.put("deactivated_by", deactivatedBy);
        logActivity(userId, role, "ACCOUNT_DEACTIVATED", "Account", userId.toString(), details, true, null, request);
    }
    public void logAccountReactivated(Long userId, String role, Long reactivatedBy, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "account_reactivated");
        details.put("reactivated_by", reactivatedBy);
        logActivity(userId, role, "ACCOUNT_REACTIVATED", "Account", userId.toString(), details, true, null, request);
    }
    public void logVerificationEmailResent(Long userId, String role, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "verification_email_resent");
        logActivity(userId, role, "VERIFICATION_EMAIL_RESENT", "Email", null, details, true, null, request);
    }
    public void logSessionRefresh(Long userId, String role, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "session_refreshed");
        logActivity(userId, role, "SESSION_REFRESH", "Authentication", null, details, true, null, request);
    }
    public void logRead(Long userId, String role, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_viewed");
        logActivity(userId, role, "READ", resourceType, resourceId, details, true, null, request);
    }
    public void logCreate(Long userId, String role, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_created");
        logActivity(userId, role, "CREATE", resourceType, resourceId, details, true, null, request);
    }
    public void logUpdate(Long userId, String role, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_updated");
        logActivity(userId, role, "UPDATE", resourceType, resourceId, details, true, null, request);
    }
    public void logDelete(Long userId, String role, String resourceType, String resourceId, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("event", "resource_deleted");
        logActivity(userId, role, "DELETE", resourceType, resourceId, details, true, null, request);
    }
    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
