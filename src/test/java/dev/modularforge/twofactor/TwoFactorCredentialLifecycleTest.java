package dev.modularforge.twofactor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwoFactorCredentialLifecycleTest {

    @Test
    void persistenceCallbacksMaintainTimestamps() throws Exception {
        TwoFactorCredential credential = new TwoFactorCredential();

        credential.created();

        assertThat(credential.getCreatedAt()).isNotNull();
        assertThat(credential.getUpdatedAt()).isEqualTo(credential.getCreatedAt());
        var createdAt = credential.getCreatedAt();
        Thread.sleep(2);

        credential.updated();

        assertThat(credential.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
