package dev.modularforge.audit;

import dev.modularforge.security.AdminLevelAuthorizationService;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import dev.modularforge.audit.dto.UserActivityLogDTO;
import dev.modularforge.audit.dto.UserActivityLogListResponse;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.audit.UserActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
@RestController
@AuditModule
@RequestMapping("/api/v1/admin/user-activity-logs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminUserActivityLogController {

    private final UserActivityLogService userActivityLogService;
    private final AdminRepository adminRepository;
    private final JwtUtils jwtUtils;
    @GetMapping
    public ResponseEntity<?> getAllUserActivityLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view user activity logs."
                ));
            }

            UserActivityLogListResponse response = userActivityLogService.getAllUserActivityLogs(
                    userId, role, action, resourceType, success,
                    startDate, endDate, ipAddress,
                    page, size, sortBy, sortDirection,
                    currentAdminId, httpRequest
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching user activity logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity logs"
            ));
        }
    }
    @GetMapping("/{logId}")
    public ResponseEntity<?> getUserActivityLogById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long logId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view user activity logs."
                ));
            }

            UserActivityLogDTO logDTO = userActivityLogService.getUserActivityLogById(logId, currentAdminId, httpRequest);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", logDTO
            ));

        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error fetching user activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity log"
            ));
        } catch (Exception e) {
            log.error("Error fetching user activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity log"
            ));
        }
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserActivityLogsByUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "user") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view user activity logs."
                ));
            }

            UserActivityLogListResponse response = userActivityLogService.getUserActivityLogsByUser(
                    userId, role,
                    page, size, sortBy, sortDirection,
                    currentAdminId, httpRequest
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching user activity logs for user: {}", userId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch user activity logs"
            ));
        }
    }
    @GetMapping("/statistics")
    public ResponseEntity<?> getActivityStatistics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view activity statistics."
                ));
            }
            if (since == null) {
                since = LocalDateTime.now().minusDays(30);
            }

            Map<String, Object> stats = userActivityLogService.getActivityStatistics(since);
            stats.put("periodStart", since);
            stats.put("periodEnd", LocalDateTime.now());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", stats
            ));

        } catch (Exception e) {
            log.error("Error fetching activity statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity statistics"
            ));
        }
    }
}
