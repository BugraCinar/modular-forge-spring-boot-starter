package dev.modulithforge.audit;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.shared.audit.AdminActivityAudit;

import tools.jackson.databind.ObjectMapper;
import dev.modulithforge.audit.AdminActivityLog;
import dev.modulithforge.audit.AdminActivityLogRepository;
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
public class AdminActivityLogger implements AdminActivityAudit {

    private final AdminActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper;
    @Async("loggingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActivity(Long adminId, String action, String resourceType,
                           String resourceId, Map<String, Object> details,
                           HttpServletRequest request) {
        try {
            AdminActivityLog log = new AdminActivityLog();
            log.setAdminId(adminId);
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            if (details != null && !details.isEmpty()) {
                log.setDetails(objectMapper.writeValueAsString(details));
            }
            if (request != null) {
                log.setIpAddress(getClientIpAddress(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }

            activityLogRepository.save(log);
            AdminActivityLogger.log.debug("Logged admin activity: {} - {} - {} - {}", adminId, action, resourceType, resourceId);

        } catch (Exception e) {
            AdminActivityLogger.log.error("Failed to log admin activity for adminId: {}, action: {}", adminId, action, e);
        }
    }
    public void logActivity(Long adminId, String action, String resourceType,
                           String resourceId, HttpServletRequest request) {
        logActivity(adminId, action, resourceType, resourceId, null, request);
    }
    public void logCreate(Long adminId, String resourceType, String resourceId,
                         Object resourceData, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "created");
        if (resourceData != null) {
            details.put("data", resourceData);
        }
        logActivity(adminId, "CREATE", resourceType, resourceId, details, request);
    }
    public void logUpdate(Long adminId, String resourceType, String resourceId,
                         Map<String, Object> changes, HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "updated");
        if (changes != null && !changes.isEmpty()) {
            details.put("changes", changes);
        }
        logActivity(adminId, "UPDATE", resourceType, resourceId, details, request);
    }
    public void logDelete(Long adminId, String resourceType, String resourceId,
                         HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "deleted");
        logActivity(adminId, "DELETE", resourceType, resourceId, details, request);
    }
    public void logRead(Long adminId, String resourceType, String resourceId,
                       HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "viewed");
        logActivity(adminId, "READ", resourceType, resourceId, details, request);
    }
    public void logActivate(Long adminId, String resourceType, String resourceId,
                           HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "activated");
        logActivity(adminId, "ACTIVATE", resourceType, resourceId, details, request);
    }
    public void logDeactivate(Long adminId, String resourceType, String resourceId,
                             HttpServletRequest request) {
        Map<String, Object> details = new HashMap<>();
        details.put("action", "deactivated");
        logActivity(adminId, "DEACTIVATE", resourceType, resourceId, details, request);
    }
    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
