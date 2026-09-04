package dev.modularforge.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import dev.modularforge.backup.DatabaseBackupJobService;
@Service
@ConditionalOnProperty(prefix = "app.modules.database-backup", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DatabaseBackupScheduledService {

    @Autowired
    private DatabaseBackupJobService databaseBackupJobService;
    @Scheduled(cron = "0 0 3 * * *")
    public void createDatabaseBackup() {
        log.info("Starting scheduled database backup");
        if (!databaseBackupJobService.requestBackup()) {
            log.warn("Skipped scheduled database backup because another backup is active or queued");
        }
    }
}
