package dev.modulithforge.audit;

import dev.modulithforge.auth.AuthService;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.audit.UserActivityLogger;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;
@Component
@AuditModule
@RequiredArgsConstructor
@Slf4j
public class UserActivityLoggingInterceptor implements HandlerInterceptor {

    private final UserActivityLogger userActivityLogger;

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return;
        }
        if (!(handler instanceof HandlerMethod)) {
            return;
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return;
            }
            boolean isUser = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_USER".equals(a.getAuthority()));
            if (!isUser) {
                return;
            }

            Object details = authentication.getDetails();
            if (!(details instanceof Long)) {
                return;
            }
            Long userId = (Long) details;

            String method = request.getMethod();
            String path   = request.getRequestURI();
            String action = method + " " + path;

            boolean success = response.getStatus() < 400;

            Map<String, Object> logDetails = new HashMap<>();
            logDetails.put("responseStatus", response.getStatus());
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isBlank()) {
                logDetails.put("query", queryString);
            }
            if (ex != null) {
                logDetails.put("error", ex.getMessage());
            }

            String failureReason = success ? null : "HTTP " + response.getStatus();

            userActivityLogger.logActivity(
                    userId, "user", action,
                    null, null,
                    logDetails, success, failureReason,
                    request
            );

        } catch (Exception e) {
            log.debug("UserActivityLoggingInterceptor: failed to log request", e);
        }
    }
}
