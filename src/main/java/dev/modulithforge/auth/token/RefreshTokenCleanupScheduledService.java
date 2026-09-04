package dev.modulithforge.auth.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import dev.modulithforge.auth.token.RefreshTokenService;
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduledService {

    @Autowired
    private RefreshTokenService refreshTokenService;
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredRefreshTokens() {
        log.info("Starting scheduled refresh token cleanup task...");

        try {
            int deleted = refreshTokenService.cleanupExpiredTokens();
            log.info("Scheduled refresh token cleanup completed. Cleaned up {} tokens.", deleted);
        } catch (Exception e) {
            log.error("Error during scheduled refresh token cleanup", e);
        }
    }
}