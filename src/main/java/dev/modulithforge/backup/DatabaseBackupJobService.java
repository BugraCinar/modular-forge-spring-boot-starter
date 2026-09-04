package dev.modulithforge.backup;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;
@Service
@ConditionalOnProperty(prefix = "app.modules.database-backup", name = "enabled", havingValue = "true")
@Slf4j
public class DatabaseBackupJobService {

    private final DatabaseBackupService databaseBackupService;
    private final Executor backupExecutor;
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);

    public DatabaseBackupJobService(DatabaseBackupService databaseBackupService,
                                    @Qualifier("backupExecutor") Executor backupExecutor) {
        this.databaseBackupService = databaseBackupService;
        this.backupExecutor = backupExecutor;
    }
    public boolean requestBackup() {
        if (!backupRunning.compareAndSet(false, true)) {
            return false;
        }

        try {
            backupExecutor.execute(() -> {
                try {
                    databaseBackupService.createAndEmailBackup();
                } finally {
                    backupRunning.set(false);
                }
            });
            return true;
        } catch (RuntimeException e) {
            backupRunning.set(false);
            throw e;
        }
    }

    public boolean isBackupRunning() {
        return backupRunning.get();
    }
}
