package dev.modulithforge.audit;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.security.JwtAuthFilter;
import dev.modulithforge.security.JwtUtils;

import dev.modulithforge.audit.SensitiveEndpointAccessLogService;
import dev.modulithforge.shared.web.ApiRoutes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
@Component
@AuditModule
@Order(1)
@Slf4j
public class SensitiveEndpointAccessFilter extends OncePerRequestFilter {

    @Autowired
    private SensitiveEndpointAccessLogService accessLogService;

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${app.security.log-sensitive-access:true}")
    private boolean logSensitiveAccess;
    private static final Map<String, EndpointConfig> SENSITIVE_ENDPOINTS = Map.ofEntries(
            Map.entry(ApiRoutes.DATABASE_BACKUP, new EndpointConfig("DATABASE_BACKUP", "CRITICAL")),
            Map.entry("/api/v1/admin/admins", new EndpointConfig("ADMIN_MANAGEMENT", "HIGH")),
            Map.entry("/api/v1/admin/tokens", new EndpointConfig("TOKEN_MANAGEMENT", "HIGH")),
            Map.entry("/api/v1/admin/2fa/setup", new EndpointConfig("2FA_SETTINGS", "MEDIUM")),
            Map.entry("/api/v1/admin/2fa/enable", new EndpointConfig("2FA_SETTINGS", "MEDIUM")),
            Map.entry("/api/v1/admin/2fa/disable", new EndpointConfig("2FA_SETTINGS", "MEDIUM")),
            Map.entry("/api/v1/admin/activity-logs", new EndpointConfig("ACTIVITY_LOGS", "MEDIUM")),
            Map.entry("/api/v1/admin/auth-error-logs", new EndpointConfig("ERROR_LOGS", "MEDIUM")),
            Map.entry(ApiRoutes.ADMIN_IMAGE, new EndpointConfig("IMAGE_MANAGEMENT", "LOW"))
    );
    private static final List<PatternConfig> SENSITIVE_PATTERNS = List.of(
            new PatternConfig(Pattern.compile("/api/v1/admin/admins/\\d+.*"), "ADMIN_MANAGEMENT", "HIGH"),
            new PatternConfig(Pattern.compile("/api/v1/admin/tokens/.*"), "TOKEN_MANAGEMENT", "HIGH"),
            new PatternConfig(Pattern.compile(Pattern.quote(ApiRoutes.DATABASE_BACKUP) + "/.*"), "DATABASE_BACKUP", "CRITICAL"),
            new PatternConfig(Pattern.compile("/api/v1/admin/activity-logs/.*"), "ACTIVITY_LOGS", "MEDIUM"),
            new PatternConfig(Pattern.compile("/api/v1/admin/auth-error-logs/.*"), "ERROR_LOGS", "MEDIUM")
    );
    private static final List<PatternConfig> SUSPICIOUS_PATH_PATTERNS = List.of(
            new PatternConfig(Pattern.compile("(?i).*\\.env.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.env$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/env\\..*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.env\\..*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.git.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.git/.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/\\.gitignore"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/config\\..*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application\\.properties"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application\\.ya?ml"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application-.*\\.properties"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/application-.*\\.ya?ml"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/bootstrap\\.properties"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/bootstrap\\.ya?ml"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.aws/.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/credentials"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*aws.*credentials.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.ssh/.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/id_rsa.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/id_ed25519.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.pem$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.key$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.sql$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.db$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.sqlite.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*dump.*\\.sql.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*backup.*\\.sql.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.log$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/logs/.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.bak$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.backup$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.old$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.orig$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.copy$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*~$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.zip$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.tar.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.gz$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.rar$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.htaccess.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.htpasswd.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/wp-config\\.php.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/php\\.ini.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/web\\.config.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/Dockerfile.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/docker-compose.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.dockerignore.*"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*\\.travis\\.ya?ml.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.gitlab-ci\\.ya?ml.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/\\.github/.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*Jenkinsfile.*"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/package\\.json$"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/package-lock\\.json$"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/pom\\.xml$"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/build\\.gradle.*"), "SUSPICIOUS_FILE_ACCESS", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/admin/.*"), "SUSPICIOUS_PATH_PROBE", "MEDIUM"),
            new PatternConfig(Pattern.compile("(?i).*/phpmyadmin.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/adminer.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/wp-admin.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/wp-login.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*actuator(?!/(?:health|info)(?:/|$)).*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/debug.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/trace.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/console.*"), "SUSPICIOUS_PATH_PROBE", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*/shell.*"), "SUSPICIOUS_PATH_PROBE", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/cmd.*"), "SUSPICIOUS_PATH_PROBE", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*/exec.*"), "SUSPICIOUS_PATH_PROBE", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.java$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.class$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.jar$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*\\.war$"), "SUSPICIOUS_FILE_ACCESS", "HIGH"),
            new PatternConfig(Pattern.compile("(?i).*secrets.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*password.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*api[_-]?key.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*private[_-]?key.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.p12$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.pfx$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*\\.jks$"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL"),
            new PatternConfig(Pattern.compile("(?i).*keystore.*"), "SUSPICIOUS_FILE_ACCESS", "CRITICAL")
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!logSensitiveAccess) {
            return true;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/")) {
            return false;
        }
        for (PatternConfig patternConfig : SUSPICIOUS_PATH_PATTERNS) {
            if (patternConfig.pattern.matcher(path).matches()) {
                return false; // Don't skip filtering - we want to log this
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, responseWrapper);

        } finally {
            logSensitiveAccessIfNeeded(request, responseWrapper);
            responseWrapper.copyBodyToResponse();
        }
    }
    private void logSensitiveAccessIfNeeded(HttpServletRequest request, ContentCachingResponseWrapper response) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        int statusCode = response.getStatus();
        EndpointConfig config = findEndpointConfig(path);
        if (config == null) {
            return;
        }
        UserInfo userInfo = extractUserInfo(request);
        logAccessBasedOnCategory(
                config.category,
                config.severity,
                userInfo.userId,
                userInfo.role,
                userInfo.username,
                getClientIpAddress(request),
                request.getHeader("User-Agent"),
                path,
                method,
                statusCode
        );
    }
    private EndpointConfig findEndpointConfig(String path) {
        for (Map.Entry<String, EndpointConfig> entry : SENSITIVE_ENDPOINTS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (PatternConfig patternConfig : SENSITIVE_PATTERNS) {
            if (patternConfig.pattern.matcher(path).matches()) {
                return new EndpointConfig(patternConfig.category, patternConfig.severity);
            }
        }
        for (PatternConfig patternConfig : SUSPICIOUS_PATH_PATTERNS) {
            if (patternConfig.pattern.matcher(path).matches()) {
                return new EndpointConfig(patternConfig.category, patternConfig.severity);
            }
        }

        return null;
    }
    private void logAccessBasedOnCategory(String category, String severity, Long userId, String role,
                                          String username, String ipAddress, String userAgent,
                                          String endpoint, String httpMethod, Integer responseStatus) {
        switch (category) {
            case "DATABASE_BACKUP":
                accessLogService.logDatabaseBackupAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "ADMIN_MANAGEMENT":
                accessLogService.logAdminManagementAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "TOKEN_MANAGEMENT":
                accessLogService.logTokenManagementAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "2FA_SETTINGS":
                accessLogService.log2FASettingsAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "ACTIVITY_LOGS":
                accessLogService.logActivityLogsAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "ERROR_LOGS":
                accessLogService.logErrorLogsAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus);
                break;
            case "SUSPICIOUS_FILE_ACCESS":
                accessLogService.logSuspiciousFileAccess(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus, getSeverityLevel(severity));
                break;
            case "SUSPICIOUS_PATH_PROBE":
                accessLogService.logSuspiciousPathProbe(userId, role, username, ipAddress,
                        userAgent, endpoint, httpMethod, responseStatus, getSeverityLevel(severity));
                break;
            default:
                accessLogService.logAccess(
                        getSeverityLevel(severity),
                        userId, role, username, ipAddress, userAgent,
                        endpoint, httpMethod, category,
                        "Access to " + category + " endpoint", responseStatus
                );
                break;
        }
    }
    private dev.modulithforge.audit.SensitiveEndpointAccessLog.SeverityLevel getSeverityLevel(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> dev.modulithforge.audit.SensitiveEndpointAccessLog.SeverityLevel.CRITICAL;
            case "HIGH" -> dev.modulithforge.audit.SensitiveEndpointAccessLog.SeverityLevel.HIGH;
            case "MEDIUM" -> dev.modulithforge.audit.SensitiveEndpointAccessLog.SeverityLevel.MEDIUM;
            default -> dev.modulithforge.audit.SensitiveEndpointAccessLog.SeverityLevel.LOW;
        };
    }
    private UserInfo extractUserInfo(HttpServletRequest request) {
        UserInfo info = new UserInfo();
        Object userIdAttr = request.getAttribute("userId");
        Object roleAttr = request.getAttribute("role");

        if (userIdAttr != null) {
            info.userId = (Long) userIdAttr;
        }
        if (roleAttr != null) {
            info.role = (String) roleAttr;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            info.username = auth.getName();
            if (info.userId == null && auth.getDetails() instanceof Long) {
                info.userId = (Long) auth.getDetails();
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && info.userId == null) {
            try {
                String token = authHeader.substring(7);
                info.userId = jwtUtils.extractUserIdAsLong(token);
                info.role = jwtUtils.extractRole(token);
                info.username = jwtUtils.extractUsername(token);
            } catch (Exception e) {
                log.debug("Could not extract user info from token: {}", e.getMessage());
            }
        }

        return info;
    }
    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
    private static class EndpointConfig {
        String category;
        String severity;

        EndpointConfig(String category, String severity) {
            this.category = category;
            this.severity = severity;
        }
    }
    private static class PatternConfig {
        Pattern pattern;
        String category;
        String severity;

        PatternConfig(Pattern pattern, String category, String severity) {
            this.pattern = pattern;
            this.category = category;
            this.severity = severity;
        }
    }
    private static class UserInfo {
        Long userId;
        String role;
        String username;
    }
}
