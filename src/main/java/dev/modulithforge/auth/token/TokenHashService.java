package dev.modulithforge.auth.token;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenHashService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;
    private static final int PREVIEW_PREFIX_LENGTH = 8;
    private static final int PREVIEW_SUFFIX_LENGTH = 6;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.token-hash-secret:}")
    private String tokenHashSecret;

    @PostConstruct
    void validateSecret() {
        if (tokenHashSecret == null || tokenHashSecret.isBlank()) {
            throw new IllegalStateException("app.security.token-hash-secret must be configured");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(tokenHashSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.security.token-hash-secret must be base64 encoded", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("app.security.token-hash-secret must contain at least 32 random bytes");
        }
    }

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(tokenHashSecret), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    public boolean matchesHash(String rawToken, String storedHash) {
        if (rawToken == null || rawToken.isBlank() || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                hashToken(rawToken).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String preview(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }

        if (token.length() <= PREVIEW_PREFIX_LENGTH + PREVIEW_SUFFIX_LENGTH) {
            return token;
        }

        return token.substring(0, PREVIEW_PREFIX_LENGTH)
                + "..."
                + token.substring(token.length() - PREVIEW_SUFFIX_LENGTH);
    }
}
