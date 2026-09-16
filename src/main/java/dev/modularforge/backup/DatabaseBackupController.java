package dev.modularforge.backup;


import dev.modularforge.backup.DatabaseBackupJobService;
import dev.modularforge.backup.DatabaseBackupService;
import dev.modularforge.shared.web.ApiRoutes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "app.modules.database-backup", name = "enabled", havingValue = "true")
@RequestMapping(ApiRoutes.DATABASE_BACKUP)
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
@Slf4j
public class DatabaseBackupController {

    @Autowired
    private DatabaseBackupService databaseBackupService;

    @Autowired
    private DatabaseBackupJobService databaseBackupJobService;
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup() {
        Map<String, Object> response = new HashMap<>();

        try {
            if (!databaseBackupJobService.requestBackup()) {
                response.put("success", false);
                response.put("message", "A database backup is already running or queued");
                return ResponseEntity.status(409).body(response);
            }

            response.put("success", true);
            response.put("message", "Database backup process started successfully");
            response.put("note", "The backup will be created and emailed in the background");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to queue database backup", e);
            response.put("success", false);
            response.put("message", "Failed to start database backup process");

            return ResponseEntity.status(500).body(response);
        }
    }
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBackupStatus() {
        Map<String, Object> response = new HashMap<>();

        response.put("success", true);
        response.put("status", databaseBackupService.getBackupStatus());
        response.put("scheduledTime", "Daily at 3:00 AM");
        response.put("manualTrigger", "Available via /create endpoint");
        response.put("running", databaseBackupJobService.isBackupRunning());

        return ResponseEntity.ok(response);
    }
}
