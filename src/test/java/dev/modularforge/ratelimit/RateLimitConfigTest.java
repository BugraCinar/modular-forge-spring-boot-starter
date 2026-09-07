package dev.modularforge.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    @Test
    void exposesConfiguredLimitsAndWindows() {
        RateLimitConfig config = new RateLimitConfig();
        ReflectionTestUtils.setField(config, "globalRequests", 100);
        ReflectionTestUtils.setField(config, "globalWindow", 1_000L);
        ReflectionTestUtils.setField(config, "apiCalls", 80);
        ReflectionTestUtils.setField(config, "apiWindow", 2_000L);
        ReflectionTestUtils.setField(config, "chatRequests", 40);
        ReflectionTestUtils.setField(config, "chatWindow", 3_000L);
        ReflectionTestUtils.setField(config, "loginAttempts", 5);
        ReflectionTestUtils.setField(config, "loginWindow", 4_000L);
        ReflectionTestUtils.setField(config, "emailVerificationAttempts", 3);
        ReflectionTestUtils.setField(config, "emailVerificationWindow", 5_000L);

        assertThat(config.getGlobalRequests()).isEqualTo(100);
        assertThat(config.getGlobalWindow()).isEqualTo(1_000L);
        assertThat(config.getApiCalls()).isEqualTo(80);
        assertThat(config.getApiWindow()).isEqualTo(2_000L);
        assertThat(config.getChatRequests()).isEqualTo(40);
        assertThat(config.getChatWindow()).isEqualTo(3_000L);
        assertThat(config.getLoginAttempts()).isEqualTo(5);
        assertThat(config.getLoginWindow()).isEqualTo(4_000L);
        assertThat(config.getEmailVerificationAttempts()).isEqualTo(3);
        assertThat(config.getEmailVerificationWindow()).isEqualTo(5_000L);
    }
}
