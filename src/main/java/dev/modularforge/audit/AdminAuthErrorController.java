package dev.modularforge.audit;

import dev.modularforge.security.AdminLevelAuthorizationService;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import dev.modularforge.audit.dto.AuthErrorLogListResponse;
import dev.modularforge.audit.dto.AuthErrorLogResponse;
import dev.modularforge.audit.dto.AuthErrorStatisticsResponse;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.audit.AdminAuthErrorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
@RestController
@AuditModule
@RequestMapping("/api/v1/admin/auth-error-logs")
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminAuthErrorController {

    @Autowired
    private AdminAuthErrorService adminAuthErrorService;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private JwtUtils jwtUtils;
    @GetMapping
    public ResponseEntity<?> getAllLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String errorType,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting auth error logs", adminId);

            AuthErrorLogListResponse response = adminAuthErrorService.getAllLogs(
                    adminId, page, size, sortBy, sortDirection, userId, role, errorType, ipAddress, startDate, request
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching auth error logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error logs"
            ));
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getLogById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting auth error log: {}", adminId, id);

            AuthErrorLogResponse response = adminAuthErrorService.getLogById(adminId, id, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error fetching auth error log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error log"
            ));
        }
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getLogsByUserId(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting auth error logs for user: {}", adminId, userId);

            AuthErrorLogListResponse response = adminAuthErrorService.getLogsByUserId(
                    adminId, userId, page, size, request
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching auth error logs by user", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error logs"
            ));
        }
    }
    @GetMapping("/ip/{ipAddress}")
    public ResponseEntity<?> getLogsByIpAddress(
            @RequestHeader("Authorization") String token,
            @PathVariable String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting auth error logs for IP: {}", adminId, ipAddress);

            AuthErrorLogListResponse response = adminAuthErrorService.getLogsByIpAddress(
                    adminId, ipAddress, page, size, request
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching auth error logs by IP", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error logs"
            ));
        }
    }
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(
            @RequestHeader("Authorization") String token,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting auth error statistics", adminId);

            AuthErrorStatisticsResponse response = adminAuthErrorService.getStatistics(adminId, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching auth error statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch authentication error statistics"
            ));
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLog(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can delete authentication error logs."
                ));
            }

            log.info("Admin {} deleting auth error log: {}", adminId, id);

            adminAuthErrorService.deleteLog(adminId, id, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Authentication error log deleted successfully"
            ));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error deleting auth error log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete authentication error log"
            ));
        }
    }
}
