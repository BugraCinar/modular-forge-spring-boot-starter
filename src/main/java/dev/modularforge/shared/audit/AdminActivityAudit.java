package dev.modularforge.shared.audit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface AdminActivityAudit {

    void logActivity(Long adminId, String action, String resourceType, String resourceId,
                     Map<String, Object> details, HttpServletRequest request);

    void logCreate(Long adminId, String resourceType, String resourceId,
                   Object resourceData, HttpServletRequest request);

    void logUpdate(Long adminId, String resourceType, String resourceId,
                   Map<String, Object> changes, HttpServletRequest request);

    void logDelete(Long adminId, String resourceType, String resourceId, HttpServletRequest request);

    void logActivate(Long adminId, String resourceType, String resourceId, HttpServletRequest request);

    void logDeactivate(Long adminId, String resourceType, String resourceId, HttpServletRequest request);
}
