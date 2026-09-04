package dev.modulithforge.ratelimit;

import dev.modulithforge.ratelimit.RateLimitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
@Slf4j
public class RateLimitService {

    private final RateLimitConfig rateLimitConfig;
    private final RateLimitStore rateLimitStore;
    private final boolean failOpen;

    public RateLimitService(RateLimitConfig rateLimitConfig,
                            RateLimitStore rateLimitStore,
                            @Value("${app.rate-limit.fail-open:false}") boolean failOpen) {
        this.rateLimitConfig = rateLimitConfig;
        this.rateLimitStore = rateLimitStore;
        this.failOpen = failOpen;
    }

    public boolean isApiRateLimitExceeded(String userId) {
        return isRateLimitExceeded("api_rate_limit:" + userId,
                rateLimitConfig.getApiCalls(), rateLimitConfig.getApiWindow());
    }

    public boolean isLoginRateLimitExceeded(String ipAddress) {
        return isRateLimitExceeded("login_rate_limit:" + ipAddress,
                rateLimitConfig.getLoginAttempts(), rateLimitConfig.getLoginWindow());
    }

    public boolean isEmailVerificationRateLimitExceeded(String email) {
        return isRateLimitExceeded("email_verification_rate_limit:" + email,
                rateLimitConfig.getEmailVerificationAttempts(), rateLimitConfig.getEmailVerificationWindow());
    }

    public boolean isGlobalRateLimitExceeded(String ipAddress) {
        return isRateLimitExceeded("global_rate_limit:" + ipAddress,
                rateLimitConfig.getGlobalRequests(), rateLimitConfig.getGlobalWindow());
    }

    private boolean isRateLimitExceeded(String key, int maxRequests, long windowMs) {
        try {
            long count = rateLimitStore.incrementWithTtl(key, windowMs);
            return count > maxRequests;
        } catch (RuntimeException e) {
            log.error("Rate-limit store unavailable for key {}. Applying {} policy: {}",
                    key, failOpen ? "fail-open" : "fail-closed", e.getMessage());
            return !failOpen;
        }
    }

    public void resetRateLimit(String key) {
        rateLimitStore.delete(key);
    }

    public int getRemainingRequests(String key, int maxRequests) {
        try {
            Long count = rateLimitStore.getCount(key);
            return count == null ? maxRequests : (int) Math.max(0, maxRequests - count);
        } catch (RuntimeException e) {
            return failOpen ? maxRequests : 0;
        }
    }

    public long getTTL(String key) {
        try {
            Long ttl = rateLimitStore.getTtlMillis(key);
            return ttl == null || ttl < 0 ? -1 : ttl;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
