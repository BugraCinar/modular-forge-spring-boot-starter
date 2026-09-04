package dev.modulithforge.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class JwtConfig {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${app.jwt.issuer}")
    private String issuer;
    public String getSecret() { return secret; }
    public long getExpiration() { return expiration; }
    public long getRefreshExpiration() { return refreshExpiration; }
    public String getIssuer() { return issuer; }

    public byte[] getDecodedSecret() {
        return Base64.getDecoder().decode(secret);
    }
    public long getExpirationInSeconds() { return expiration / 1000; }
    public long getRefreshExpirationInSeconds() { return refreshExpiration / 1000; }

    @PostConstruct
    void validate() {
        byte[] decoded;
        try {
            decoded = getDecodedSecret();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.jwt.secret must be base64 encoded", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("app.jwt.secret must contain at least 32 random bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.jwt.issuer must not be blank");
        }
        if (expiration <= 0 || expiration > 86_400_000L) {
            throw new IllegalStateException("access-token lifetime must be between one millisecond and 24 hours");
        }
        if (refreshExpiration <= expiration || refreshExpiration > 15_552_000_000L) {
            throw new IllegalStateException("refresh-token lifetime must exceed access-token lifetime and stay under 180 days");
        }
    }
}
