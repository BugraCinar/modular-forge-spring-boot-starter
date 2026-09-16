package dev.modularforge.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SecurityFixIntegrationTest extends BaseIntegrationTest {
    @Test void resetPageInlineCodeHasMatchingResponseNonce() {
        var page = restTemplate.getForEntity("/api/v1/auth/reset-password?token=local-test-token", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        String policy = page.getHeaders().getFirst("Content-Security-Policy");
        assertThat(policy).contains("script-src 'self' 'nonce-").doesNotContain("unsafe-inline");
        String nonce = policy.split("'nonce-")[1].split("'")[0];
        assertThat(page.getBody()).contains("nonce=\"" + nonce + "\"");
        var second = restTemplate.getForEntity("/api/v1/auth/reset-password?token=other", String.class);
        assertThat(second.getHeaders().getFirst("Content-Security-Policy")).doesNotContain(nonce);
    }
    @Test void logoutAllRejectsPreviouslyIssuedAccessAndRefreshTokens() {
        createVerifiedUser("session-user", "session@example.com", "Password1!");
        var login = login("session-user", "Password1!", "user");
        String access = extractAccessToken(login); String refresh = extractRefreshToken(login);
        assertThat(access).isNotBlank();
        var result = restTemplate.exchange("/api/v1/auth/logout-all", HttpMethod.POST,
                new HttpEntity<>(bearerHeaders(access)), MAP_TYPE_REF);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authenticatedGet("/api/v1/auth/me", access).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var rotation = restTemplate.exchange("/api/v1/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", refresh)), MAP_TYPE_REF);
        assertThat(rotation.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
