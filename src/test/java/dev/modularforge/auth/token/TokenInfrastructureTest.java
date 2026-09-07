package dev.modularforge.auth.token;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenInfrastructureTest {

    @Test
    void tokenHashingValidatesGeneratesHashesMatchesAndPreviews() {
        TokenHashService service = new TokenHashService();
        String secret = Base64.getEncoder().encodeToString("a".repeat(32).getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(service, "tokenHashSecret", secret);
        service.validateSecret();

        String token = service.generateToken();
        String hash = service.hashToken(token);

        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(service.matchesHash(token, hash)).isTrue();
        assertThat(service.matchesHash(token + "x", hash)).isFalse();
        assertThat(service.matchesHash(null, hash)).isFalse();
        assertThat(service.matchesHash("", hash)).isFalse();
        assertThat(service.matchesHash(token, null)).isFalse();
        assertThat(service.matchesHash(token, " ")).isFalse();
        assertThat(service.preview(null)).isEmpty();
        assertThat(service.preview(" ")).isEmpty();
        assertThat(service.preview("short-token")).isEqualTo("short-token");
        assertThat(service.preview("12345678901234567890")).isEqualTo("12345678...567890");
        assertThatThrownBy(() -> service.hashToken(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.hashToken(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenHashSecretRejectsEveryInvalidConfigurationAndWrapsHashFailures() {
        TokenHashService service = new TokenHashService();
        ReflectionTestUtils.setField(service, "tokenHashSecret", null);
        assertThatThrownBy(service::validateSecret).isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(service, "tokenHashSecret", " ");
        assertThatThrownBy(service::validateSecret).isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(service, "tokenHashSecret", "not-base64!");
        assertThatThrownBy(service::validateSecret).isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
        ReflectionTestUtils.setField(service, "tokenHashSecret", Base64.getEncoder().encodeToString(new byte[8]));
        assertThatThrownBy(service::validateSecret).isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
        ReflectionTestUtils.setField(service, "tokenHashSecret", "invalid-after-validation");
        assertThatThrownBy(() -> service.hashToken("token")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cookieServiceCreatesAndClearsStrictHttpOnlyCookies() {
        RefreshTokenCookieService service = new RefreshTokenCookieService();
        ReflectionTestUtils.setField(service, "cookieName", "refresh");
        ReflectionTestUtils.setField(service, "cookieMaxAge", 3600);
        ReflectionTestUtils.setField(service, "useCookies", true);
        ReflectionTestUtils.setField(service, "cookieSecure", true);
        ReflectionTestUtils.setField(service, "cookiePath", "/api/v1/auth");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(service.useCookies()).isTrue();
        service.setRefreshTokenCookie(response, "raw-token");
        service.clearRefreshTokenCookie(response);

        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").getFirst())
                .contains("refresh=raw-token", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=3600");
        assertThat(response.getHeaders("Set-Cookie").get(1)).contains("refresh=", "Max-Age=0");
    }

    @Test
    void cleanupSchedulerContainsServiceFailures() {
        RefreshTokenService tokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
        RefreshTokenCleanupScheduledService scheduled = new RefreshTokenCleanupScheduledService();
        ReflectionTestUtils.setField(scheduled, "refreshTokenService", tokenService);
        org.mockito.Mockito.when(tokenService.cleanupExpiredTokens()).thenReturn(4)
                .thenThrow(new IllegalStateException("database down"));

        assertThatCode(scheduled::cleanupExpiredRefreshTokens).doesNotThrowAnyException();
        assertThatCode(scheduled::cleanupExpiredRefreshTokens).doesNotThrowAnyException();
    }
}
