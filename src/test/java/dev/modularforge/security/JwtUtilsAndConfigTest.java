package dev.modularforge.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsAndConfigTest {

    private static final byte[] SECRET_BYTES =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private JwtConfig config;
    private JwtUtils jwt;

    @BeforeEach
    void setUp() {
        config = config(60_000L, 2_592_000_000L, "modular-forge");
        jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "jwtConfig", config);
    }

    @Test
    void createsRefreshTokenAndExposesBothExpirationUnits() {
        String token = jwt.generateRefreshToken("alice");
        assertThat(jwt.validateToken(token)).isTrue();
        assertThat(jwt.extractUsername(token)).isEqualTo("alice");
        assertThat(jwt.extractExpiration(token)).isAfter(new Date());
        assertThat(jwt.getAccessTokenExpiration()).isEqualTo(60L);
        assertThat(jwt.getRefreshTokenExpiration()).isEqualTo(2_592_000L);
        assertThat(jwt.getRefreshTokenExpirationDays()).isEqualTo(30L);
    }

    @Test
    void extractsEveryUserAndAdminClaim() {
        String userToken = jwt.generateUserToken("alice", 7L, "app_user", 3L);
        assertThat(jwt.extractUserId(userToken)).isEqualTo(7);
        assertThat(jwt.extractUserIdAsLong(userToken)).isEqualTo(7L);
        assertThat(jwt.extractRole(userToken)).isEqualTo("user");
        assertThat(jwt.extractUserType(userToken)).isEqualTo("app_user");
        assertThat(jwt.extractAuthVersion(userToken)).isEqualTo(3L);
        assertThat(jwt.extractAdminLevel(userToken)).isNull();

        long large = (long) Integer.MAX_VALUE + 100L;
        String adminToken = jwt.generateAdminToken("root", large, null, large);
        assertThat(jwt.extractUserIdAsLong(adminToken)).isEqualTo(large);
        assertThat(jwt.extractAuthVersion(adminToken)).isEqualTo(large);
        assertThat(jwt.extractUserType(adminToken)).isNull();

        String levelToken = jwt.generateAdminToken("root", 8L, 2, 0L);
        assertThat(jwt.extractAdminLevel(levelToken)).isEqualTo(2);
    }

    @Test
    void returnsNullForClaimsWithUnsupportedNumericTypes() {
        String token = Jwts.builder()
                .subject("subject")
                .issuer(config.getIssuer())
                .claim("userId", "seven")
                .claim("authVersion", "three")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET_BYTES), Jwts.SIG.HS256)
                .compact();
        assertThat(jwt.extractUserIdAsLong(token)).isNull();
        assertThat(jwt.extractAuthVersion(token)).isNull();
    }

    @Test
    void rejectsMalformedWrongIssuerExpiredAndUnexpectedAlgorithmTokens() {
        assertThat(jwt.validateToken("not-a-token")).isFalse();
        String wrongIssuer = Jwts.builder().subject("alice").issuer("other")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET_BYTES), Jwts.SIG.HS256).compact();
        assertThat(jwt.validateToken(wrongIssuer)).isFalse();
        String expired = Jwts.builder().subject("alice").issuer(config.getIssuer())
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(Keys.hmacShaKeyFor(SECRET_BYTES), Jwts.SIG.HS256).compact();
        assertThat(jwt.validateToken(expired)).isFalse();
        String hs512 = Jwts.builder().subject("alice").issuer(config.getIssuer())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET_BYTES), Jwts.SIG.HS512).compact();
        assertThat(jwt.validateToken(hs512)).isFalse();
        assertThatThrownBy(() -> jwt.extractUsername(hs512)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jwtConfigValidatesEncodingLengthIssuerAndLifetimeBounds() {
        config.validate();
        assertThat(config.getSecret()).isEqualTo(Base64.getEncoder().encodeToString(SECRET_BYTES));
        assertThat(config.getDecodedSecret()).containsExactly(SECRET_BYTES);

        JwtConfig invalidBase64 = config(1L, 2L, "issuer");
        ReflectionTestUtils.setField(invalidBase64, "secret", "not base64!");
        assertThatThrownBy(invalidBase64::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");

        JwtConfig shortSecret = config(1L, 2L, "issuer");
        ReflectionTestUtils.setField(shortSecret, "secret", Base64.getEncoder().encodeToString(new byte[31]));
        assertThatThrownBy(shortSecret::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");

        for (String issuer : new String[]{null, " "}) {
            assertThatThrownBy(() -> config(1L, 2L, issuer).validate()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("issuer");
        }
        for (long expiration : new long[]{0L, 86_400_001L}) {
            assertThatThrownBy(() -> config(expiration, 100_000_000L, "issuer").validate())
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("access-token");
        }
        assertThatThrownBy(() -> config(10L, 10L, "issuer").validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refresh-token");
        assertThatThrownBy(() -> config(10L, 15_552_000_001L, "issuer").validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refresh-token");
    }

    private JwtConfig config(long expiration, long refreshExpiration, String issuer) {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "secret", Base64.getEncoder().encodeToString(SECRET_BYTES));
        ReflectionTestUtils.setField(config, "expiration", expiration);
        ReflectionTestUtils.setField(config, "refreshExpiration", refreshExpiration);
        ReflectionTestUtils.setField(config, "issuer", issuer);
        return config;
    }
}
