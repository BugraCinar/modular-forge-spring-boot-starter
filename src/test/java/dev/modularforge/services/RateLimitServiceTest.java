package dev.modularforge.services;

import dev.modularforge.ratelimit.RateLimitService;
import dev.modularforge.ratelimit.RateLimitStore;

import dev.modularforge.ratelimit.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService")
class RateLimitServiceTest {

    @Mock
    private RateLimitConfig rateLimitConfig;

    @Mock
    private RateLimitStore rateLimitStore;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(rateLimitConfig, rateLimitStore, false);
    }

    @Test
    void usesSharedAtomicCounterForLoginLimit() {
        when(rateLimitConfig.getLoginAttempts()).thenReturn(3);
        when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L);
        when(rateLimitStore.incrementWithTtl("login_rate_limit:1.2.3.4", 60_000L)).thenReturn(1L, 2L, 3L, 4L);

        assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
        assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
        assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
        assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isTrue();

        verify(rateLimitStore, times(4)).incrementWithTtl("login_rate_limit:1.2.3.4", 60_000L);
    }

    @Test
    void keepsLimitKeysIndependentAcrossCategories() {
        when(rateLimitConfig.getApiCalls()).thenReturn(1);
        when(rateLimitConfig.getApiWindow()).thenReturn(1_000L);
        when(rateLimitConfig.getGlobalRequests()).thenReturn(1);
        when(rateLimitConfig.getGlobalWindow()).thenReturn(2_000L);
        when(rateLimitStore.incrementWithTtl("api_rate_limit:user-42", 1_000L)).thenReturn(2L);
        when(rateLimitStore.incrementWithTtl("global_rate_limit:1.2.3.4", 2_000L)).thenReturn(1L);

        assertThat(rateLimitService.isApiRateLimitExceeded("user-42")).isTrue();
        assertThat(rateLimitService.isGlobalRateLimitExceeded("1.2.3.4")).isFalse();
    }

    @Test
    void exposesRemainingCountAndTtlFromSharedStore() {
        when(rateLimitStore.getCount("login_rate_limit:1.2.3.4")).thenReturn(2L);
        when(rateLimitStore.getTtlMillis("login_rate_limit:1.2.3.4")).thenReturn(45_000L);

        assertThat(rateLimitService.getRemainingRequests("login_rate_limit:1.2.3.4", 5)).isEqualTo(3);
        assertThat(rateLimitService.getTTL("login_rate_limit:1.2.3.4")).isEqualTo(45_000L);
    }

    @Test
    void resetsOnlyTheRequestedSharedKey() {
        rateLimitService.resetRateLimit("login_rate_limit:1.2.3.4");

        verify(rateLimitStore).delete("login_rate_limit:1.2.3.4");
    }

    @Test
    void failsClosedWhenTheSharedStoreIsUnavailable() {
        when(rateLimitConfig.getLoginAttempts()).thenReturn(3);
        when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(rateLimitStore).incrementWithTtl(eq("login_rate_limit:1.2.3.4"), anyLong());

        assertThat(rateLimitService.isLoginRateLimitExceeded("1.2.3.4")).isTrue();
    }

    @Test
    void canFailOpenWhenExplicitlyConfiguredForLocalDevelopment() {
        RateLimitService failOpenService = new RateLimitService(rateLimitConfig, rateLimitStore, true);
        when(rateLimitConfig.getLoginAttempts()).thenReturn(3);
        when(rateLimitConfig.getLoginWindow()).thenReturn(60_000L);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(rateLimitStore).incrementWithTtl(eq("login_rate_limit:1.2.3.4"), anyLong());

        assertThat(failOpenService.isLoginRateLimitExceeded("1.2.3.4")).isFalse();
    }

    @Test
    void appliesEmailVerificationLimit() {
        when(rateLimitConfig.getEmailVerificationAttempts()).thenReturn(2);
        when(rateLimitConfig.getEmailVerificationWindow()).thenReturn(30_000L);
        when(rateLimitStore.incrementWithTtl("email_verification_rate_limit:user@example.com", 30_000L))
                .thenReturn(3L);

        assertThat(rateLimitService.isEmailVerificationRateLimitExceeded("user@example.com")).isTrue();
    }

    @Test
    void reportsFullAllowanceWhenCounterDoesNotExist() {
        when(rateLimitStore.getCount("new-key")).thenReturn(null);

        assertThat(rateLimitService.getRemainingRequests("new-key", 5)).isEqualTo(5);
    }

    @Test
    void neverReportsNegativeRemainingRequests() {
        when(rateLimitStore.getCount("busy-key")).thenReturn(12L);

        assertThat(rateLimitService.getRemainingRequests("busy-key", 5)).isZero();
    }

    @Test
    void remainingRequestFallbackFollowsFailurePolicy() {
        when(rateLimitStore.getCount("broken-key")).thenThrow(new IllegalStateException("redis unavailable"));
        RateLimitService failOpenService = new RateLimitService(rateLimitConfig, rateLimitStore, true);

        assertThat(rateLimitService.getRemainingRequests("broken-key", 5)).isZero();
        assertThat(failOpenService.getRemainingRequests("broken-key", 5)).isEqualTo(5);
    }

    @Test
    void unavailableOrMissingTtlUsesUnknownSentinel() {
        when(rateLimitStore.getTtlMillis("missing-key")).thenReturn(null);
        when(rateLimitStore.getTtlMillis("persistent-key")).thenReturn(-1L);
        when(rateLimitStore.getTtlMillis("broken-key")).thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(rateLimitService.getTTL("missing-key")).isEqualTo(-1);
        assertThat(rateLimitService.getTTL("persistent-key")).isEqualTo(-1);
        assertThat(rateLimitService.getTTL("broken-key")).isEqualTo(-1);
    }
}
