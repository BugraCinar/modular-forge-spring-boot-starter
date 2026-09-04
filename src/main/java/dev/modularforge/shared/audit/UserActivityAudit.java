package dev.modularforge.shared.audit;

import jakarta.servlet.http.HttpServletRequest;

public interface UserActivityAudit {

    void logRegister(Long userId, String role, boolean success, String failureReason, HttpServletRequest request);

    void logLoginFailure(Long userId, String role, String reason, HttpServletRequest request);

    void logLoginSuccess(Long userId, String role, HttpServletRequest request);

    void logPasswordResetRequest(Long userId, String role, HttpServletRequest request);

    void logPasswordResetComplete(Long userId, String role, boolean success, HttpServletRequest request);

    void logEmailVerification(Long userId, String role, boolean success, HttpServletRequest request);
}
