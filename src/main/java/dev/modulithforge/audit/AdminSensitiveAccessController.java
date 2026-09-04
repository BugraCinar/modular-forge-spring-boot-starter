package dev.modulithforge.audit;

import dev.modulithforge.security.AdminLevelAuthorizationService;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.audit.dto.SensitiveAccessLogListResponse;
import dev.modulithforge.audit.dto.SensitiveAccessLogResponse;
import dev.modulithforge.audit.dto.SensitiveAccessStatisticsResponse;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.security.JwtUtils;
import dev.modulithforge.audit.AdminSensitiveAccessService;
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
@RequestMapping("/api/v1/admin/sensitive-access-logs")
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminSensitiveAccessController {

    @Autowired
    private AdminSensitiveAccessService adminSensitiveAccessService;

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
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting sensitive access logs", adminId);

            SensitiveAccessLogListResponse response = adminSensitiveAccessService.getAllLogs(
                    adminId, page, size, sortBy, sortDirection, userId, role, severity, category, ipAddress, startDate, request
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching sensitive access logs", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch sensitive access logs"
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

            log.info("Admin {} requesting sensitive access log: {}", adminId, id);

            SensitiveAccessLogResponse response = adminSensitiveAccessService.getLogById(adminId, id, request);

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
            log.error("Error fetching sensitive access log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch sensitive access log"
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

            log.info("Admin {} requesting sensitive access logs for user: {}", adminId, userId);

            SensitiveAccessLogListResponse response = adminSensitiveAccessService.getLogsByUserId(
                    adminId, userId, page, size, request
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching sensitive access logs by user", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch sensitive access logs"
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

            log.info("Admin {} requesting sensitive access logs for IP: {}", adminId, ipAddress);

            SensitiveAccessLogListResponse response = adminSensitiveAccessService.getLogsByIpAddress(
                    adminId, ipAddress, page, size, request
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching sensitive access logs by IP", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch sensitive access logs"
            ));
        }
    }
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(
            @RequestHeader("Authorization") String token,
            HttpServletRequest request) {

        try {
            Long adminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));

            log.info("Admin {} requesting sensitive access statistics", adminId);

            SensitiveAccessStatisticsResponse response = adminSensitiveAccessService.getStatistics(adminId, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching sensitive access statistics", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch sensitive access statistics"
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
                        "message", "Access denied. Only Level 0 Super Admins can delete sensitive access logs."
                ));
            }

            log.info("Admin {} deleting sensitive access log: {}", adminId, id);

            adminSensitiveAccessService.deleteLog(adminId, id, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sensitive access log deleted successfully"
            ));

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
            }
            log.error("Error deleting sensitive access log", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete sensitive access log"
            ));
        }
    }
}
