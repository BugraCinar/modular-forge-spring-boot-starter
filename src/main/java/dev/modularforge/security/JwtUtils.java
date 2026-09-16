package dev.modularforge.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import dev.modularforge.security.JwtConfig;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.UUID;

@Component
public class JwtUtils {

    @Autowired
    private JwtConfig jwtConfig;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getDecodedSecret());
    }
    public String generateUserToken(String username, Long userId, String userType, long authVersion) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("userId", userId);
        claimsMap.put("role", "user");
        claimsMap.put("userType", userType);
        claimsMap.put("authVersion", authVersion);

        return Jwts.builder()
                .claims(claimsMap)
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }
    public String generateAdminToken(String username, Long userId, Integer adminLevel, long authVersion) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("userId", userId);
        claimsMap.put("role", "admin");
        claimsMap.put("authVersion", authVersion);
        if (adminLevel != null) {
            claimsMap.put("adminLevel", adminLevel);
        }

        return Jwts.builder()
                .claims(claimsMap)
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuer(jwtConfig.getIssuer())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshExpiration()))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Long getAccessTokenExpiration() {
        return jwtConfig.getExpirationInSeconds();
    }

    public Long getRefreshTokenExpiration() {
        return jwtConfig.getRefreshExpirationInSeconds();
    }

    public Long getRefreshTokenExpirationDays() {
        return jwtConfig.getRefreshExpiration() / (1000 * 60 * 60 * 24);
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Integer extractUserId(String token) {
        return extractAllClaims(token).get("userId", Integer.class);
    }

    public Long extractUserIdAsLong(String token) {
        Object userId = extractAllClaims(token).get("userId");
        if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        } else if (userId instanceof Long) {
            return (Long) userId;
        }
        return null;
    }
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }
    public String extractUserType(String token) {
        return extractAllClaims(token).get("userType", String.class);
    }

    public Integer extractAdminLevel(String token) {
        return extractAllClaims(token).get("adminLevel", Integer.class);
    }

    public Long extractAuthVersion(String token) {
        Object authVersion = extractAllClaims(token).get("authVersion");
        if (authVersion instanceof Integer value) {
            return value.longValue();
        }
        if (authVersion instanceof Long value) {
            return value;
        }
        return null;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return parse(token).getPayload();
    }

    private Jws<Claims> parse(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtConfig.getIssuer())
                .build()
                .parseSignedClaims(token);
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("Unexpected JWT signing algorithm");
        }
        return parsed;
    }
}
