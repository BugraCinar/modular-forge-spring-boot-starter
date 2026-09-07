package dev.modularforge.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveEndpointAccessLogLifecycleTest {

    @Test
    void persistenceDefaultsTimestampAndEmailAlertFlag() {
        SensitiveEndpointAccessLog log = new SensitiveEndpointAccessLog();

        log.onCreate();

        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getEmailAlertSent()).isFalse();
    }

    @Test
    void persistencePreservesExistingEmailAlertFlag() {
        SensitiveEndpointAccessLog log = new SensitiveEndpointAccessLog();
        log.setEmailAlertSent(true);

        log.onCreate();

        assertThat(log.getEmailAlertSent()).isTrue();
    }
}
