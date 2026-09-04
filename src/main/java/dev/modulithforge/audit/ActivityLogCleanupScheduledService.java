package dev.modulithforge.audit;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.audit.UserActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
@Service
@AuditModule
@RequiredArgsConstructor
@Slf4j
public class ActivityLogCleanupScheduledService {

    private final UserActivityLogRepository userActivityLogRepository;

    @Value("${app.activity-log.retention-days:30}")
    private int retentionDays;
    @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void cleanupOldActivityLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("Starting scheduled activity log cleanup. Deleting logs older than {} days (before {})",
                retentionDays, cutoff);

        int deleted = userActivityLogRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Activity log cleanup completed successfully. Deleted {} user activity logs", deleted);
    }
}
