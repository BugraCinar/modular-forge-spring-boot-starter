package dev.modulithforge.integration;

import dev.modulithforge.auth.token.VerificationToken;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.identity.model.UserType;
import dev.modulithforge.identity.UserRepository;
import dev.modulithforge.notification.EmailService;

import dev.modulithforge.auth.token.VerificationTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
@DisplayName("Auth Flow Integration")
class AuthFlowIntegrationTest extends BaseIntegrationTest {

        @Autowired
        private VerificationTokenRepository verificationTokenRepository;

        @Test
        @DisplayName("register user, verify email, then login returns 200 with accessToken")
        void register_verifyEmail_login_returnsToken() {
                Map<String, Object> registerBody = Map.of(
                                "username", "integclient",
                                "email", "integclient@test.com",
                                "password", "StrongPassw0rd!",
                                "userType", "app_user");

                ResponseEntity<Map<String, Object>> registerResp = restTemplate.exchange(
                                "/api/v1/auth/register", HttpMethod.POST, new HttpEntity<>(registerBody), MAP_TYPE_REF);

                assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(registerResp.getBody()).containsKey("success");
                assertThat(registerResp.getBody().get("success")).isEqualTo(true);
                Long userId = userRepository.findByUsername("integclient")
                                .orElseThrow(() -> new AssertionError("User not found after registration"))
                                .getId();

                ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
                verify(emailService).sendVerificationEmail(eq("integclient@test.com"), tokenCaptor.capture(), anyString());
                String verificationToken = tokenCaptor.getValue();

                assertThat(verificationTokenRepository.findByUserIdAndRole(userId, "user"))
                                .hasValueSatisfying(token -> {
                                        assertThat(token.getTokenHash()).isNotBlank();
                                        assertThat(token.getTokenPreview()).isNotBlank();
                                        assertThat(token.getStoredToken()).isEqualTo(token.getTokenPreview());
                                        assertThat(token.getStoredToken()).isNotEqualTo(verificationToken);
                                });
                ResponseEntity<String> verifyResp = restTemplate.exchange(
                                "/api/v1/auth/verify-email?token=" + verificationToken, HttpMethod.GET, null,
                                String.class);

                assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                ResponseEntity<Map<String, Object>> loginResp = login("integclient", "StrongPassw0rd!", "user");

                assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(loginResp.getBody().get("success")).isEqualTo(true);

                String accessToken = extractAccessToken(loginResp);
                assertThat(accessToken).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("login with wrong password returns 401")
        void login_wrongPassword_returns401() {
                createVerifiedUser("locktest", "locktest@test.com", "Correct1!");

                ResponseEntity<Map<String, Object>> response = login("locktest", "WrongPass9!", "user");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(response.getBody().get("success")).isEqualTo(false);
        }

        @Test
        @DisplayName("/me with valid bearer token returns 200 with correct username")
        void getCurrentUser_withValidToken_returns200() {
                createVerifiedUser("meuser", "meuser@test.com", "Passw0rd!");

                ResponseEntity<Map<String, Object>> loginResp = login("meuser", "Passw0rd!", "user");
                assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

                String token = extractAccessToken(loginResp);
                assertThat(token).isNotNull();

                ResponseEntity<Map<String, Object>> meResp = authenticatedGet("/api/v1/auth/me", token);

                assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(meResp.getBody()).containsKey("role");
                assertThat(meResp.getBody().get("role")).isEqualTo("USER");
        }
}
