package dev.modularforge.auth.token;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEntityCoverageTest {

    @Test
    void passwordResetAttemptCounterReachesLimit() {
        PasswordResetToken token = new PasswordResetToken();
        token.setAttemptCount(0);

        assertThat(token.hasTooManyAttempts()).isFalse();
        token.incrementAttemptCount();
        token.incrementAttemptCount();
        token.incrementAttemptCount();

        assertThat(token.getAttemptCount()).isEqualTo(3);
        assertThat(token.hasTooManyAttempts()).isTrue();
    }

    @Test
    void refreshTokenValidityRequiresFutureExpiryAndNonRevokedState() {
        RefreshToken token = new RefreshToken();
        token.setIssuedAuthVersion(0L);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(1));
        token.setIsRevoked(false);
        assertThat(token.isValid()).isTrue();

        token.setIsRevoked(true);
        assertThat(token.isValid()).isFalse();

        token.setIsRevoked(false);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        assertThat(token.isValid()).isFalse();
    }
}
