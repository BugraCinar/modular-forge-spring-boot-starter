package dev.modulithforge.audit;

import dev.modulithforge.security.AdminLevelAuthorizationService;

import dev.modulithforge.audit.dto.AdminActivityLogDTO;
import dev.modulithforge.audit.dto.AdminActivityLogListResponse;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.security.JwtUtils;
import dev.modulithforge.audit.AdminActivityLogService;
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
@RequestMapping("/api/v1/admin/activity-logs")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminActivityLogController {

    private final AdminActivityLogService activityLogService;
    private final AdminRepository adminRepository;
    private final JwtUtils jwtUtils;
    @GetMapping
    public ResponseEntity<?> getAllActivityLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
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
                        "message", "Access denied. Only Level 0 Super Admins can view activity logs."
                ));
            }

            AdminActivityLogListResponse response = activityLogService.getAllActivityLogs(
                    adminId, action, resourceType, startDate,
                    page, size, sortBy, sortDirection,
                    currentAdminId, httpRequest
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching activity logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity logs"
            ));
        }
    }
    @GetMapping("/{logId}")
    public ResponseEntity<?> getActivityLogById(
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
                        "message", "Access denied. Only Level 0 Super Admins can view activity logs."
                ));
            }

            AdminActivityLogDTO logDTO = activityLogService.getActivityLogById(logId, currentAdminId, httpRequest);

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
            log.error("Error fetching activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity log"
            ));
        } catch (Exception e) {
            log.error("Error fetching activity log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch activity log"
            ));
        }
    }
}
