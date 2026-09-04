package dev.modulithforge.integration;

import dev.modulithforge.identity.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
@DisplayName("Account Lock Integration")
class AccountLockIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("5 failed logins lock account; correct password returns 401 locked")
    void fiveFailedLogins_thenCorrectPassword_returns401_accountLocked() {
        final String username = "bruteuser";
        final String correctPassword = "Correct1!";

        createVerifiedUser(username, "bruteuser@test.com", correctPassword);
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map<String, Object>> failResp = login(username, "WrongPass" + i + "!", "user");
            assertThat(failResp.getStatusCode())
                    .as("Attempt %d should return 401", i + 1)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<Map<String, Object>> lockedResp = login(username, correctPassword, "user");

        assertThat(lockedResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(lockedResp.getBody()).containsKey("message");
        assertThat(lockedResp.getBody().get("message").toString())
                .containsIgnoringCase("locked");
    }
}
