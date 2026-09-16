package dev.modularforge.auth.token;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;

import dev.modularforge.auth.token.dto.RefreshTokenResponse;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.token.RefreshTokenRepository;
import dev.modularforge.security.JwtUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private TokenHashService tokenHashService;
    public RefreshToken createRefreshToken(Long userId, String role, HttpServletRequest request) {
        long authVersion = "admin".equals(role)
                ? adminRepository.findById(userId).orElseThrow().currentAuthVersion()
                : userRepository.findById(userId).orElseThrow().currentAuthVersion();
        return createRefreshToken(userId, role, authVersion, request);
    }

    public RefreshToken createRefreshToken(Long userId, String role, long authVersion, HttpServletRequest request) {
        long expirationDays = jwtUtils.getRefreshTokenExpirationDays();
        log.info("Creating refresh token for userId={}, role={}, expirationDays={}", userId, role, expirationDays);

        String rawToken = tokenHashService.generateToken();
        RefreshToken refreshToken = new RefreshToken(userId, role, expirationDays);
        refreshToken.setIssuedAuthVersion(authVersion);
        refreshToken.storeTokenMetadata(rawToken, tokenHashService.hashToken(rawToken), tokenHashService.preview(rawToken));
        refreshToken.setDeviceInfo(extractDeviceInfo(request));
        refreshToken.setIpAddress(getClientIpAddress(request));

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        saved.setPlaintextToken(rawToken);
        log.info("Saved refresh token - ID: {}, tokenPreview: {}, expiryDate: {}",
                saved.getId(), saved.getTokenPreview(), saved.getExpiryDate());
        return saved;
    }
    @Transactional
    public Optional<RefreshToken> verifyRefreshToken(String token) {
        if (token == null || token.isEmpty()) {
            log.warn("Refresh token is null or empty");
            return Optional.empty();
        }

        Optional<RefreshToken> refreshTokenOpt = findByPresentedToken(token);
        if (refreshTokenOpt.isEmpty()) {
            log.warn("Refresh token not found in database: {}", tokenHashService.preview(token));
            return Optional.empty();
        }

        RefreshToken refreshToken = refreshTokenOpt.get();
        log.info("Found refresh token - ID: {}, userId: {}, role: {}, isRevoked: {}, expiryDate: {}, now: {}",
                refreshToken.getId(), refreshToken.getUserId(), refreshToken.getRole(),
                refreshToken.getIsRevoked(), refreshToken.getExpiryDate(), LocalDateTime.now());
        if (refreshToken.getIsRevoked()) {
            log.error("WARNING: Revoked refresh token reuse detected for userId={} role={}",
                    refreshToken.getUserId(), refreshToken.getRole());
            revokeAllSessions(refreshToken.getUserId(), refreshToken.getRole());
            return Optional.empty();
        }
        if (refreshToken.isExpired()) {
            log.warn("Refresh token expired - expiryDate: {}, now: {}",
                    refreshToken.getExpiryDate(), LocalDateTime.now());
            return Optional.empty();
        }

        log.info("Refresh token is valid");
        return Optional.of(refreshToken);
    }
    @Transactional
    public Optional<RefreshToken> rotateRefreshToken(RefreshToken oldToken, HttpServletRequest request) {
        if (oldToken.getIssuedAuthVersion() == null) return Optional.empty();
        int consumed = refreshTokenRepository.revokeIfActive(oldToken.getId(), LocalDateTime.now());
        if (consumed != 1) {
            revokeAllSessions(oldToken.getUserId(), oldToken.getRole());
            log.warn("Refresh token rotation lost a consume race for userId={} role={}",
                    oldToken.getUserId(), oldToken.getRole());
            return Optional.empty();
        }

        return Optional.of(createRefreshToken(oldToken.getUserId(), oldToken.getRole(),
                oldToken.getIssuedAuthVersion(), request));
    }
    @Transactional
    public boolean revokeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        Optional<RefreshToken> refreshTokenOpt = findByPresentedToken(token);
        if (refreshTokenOpt.isPresent()) {
            RefreshToken refreshToken = refreshTokenOpt.get();
            refreshToken.setIsRevoked(true);
            refreshTokenRepository.save(refreshToken);
            return true;
        }
        return false;
    }
    @Transactional
    public int revokeAllUserTokens(Long userId, String role) {
        return refreshTokenRepository.revokeAllUserTokens(userId, role);
    }

    @Transactional
    public int revokeAllSessions(Long userId, String role) {
        if ("user".equals(role)) {
            userRepository.findById(userId).ifPresent(user -> {
                user.invalidateAccessTokens();
                userRepository.saveAndFlush(user);
            });
        } else if ("admin".equals(role)) {
            adminRepository.findById(userId).ifPresent(admin -> {
                admin.invalidateAccessTokens();
                adminRepository.saveAndFlush(admin);
            });
        } else {
            throw new IllegalArgumentException("Unsupported account role");
        }
        return refreshTokenRepository.revokeAllUserTokens(userId, role);
    }
    @Transactional
    public int cleanupExpiredTokens() {
        int deletedRevokedExpired = refreshTokenRepository.cleanupRevokedAndExpired(LocalDateTime.now());
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        refreshTokenRepository.deleteByExpiryDateBefore(sevenDaysAgo);

        return deletedRevokedExpired;
    }
    public List<RefreshTokenResponse> getFilteredTokens(String role, Long userId, Boolean isRevoked, String ipAddress) {
        List<RefreshToken> tokens = refreshTokenRepository.findWithFilters(role, userId, isRevoked, ipAddress);
        return tokens.stream()
                .map(this::toRefreshTokenResponse)
                .collect(Collectors.toList());
    }
    public Optional<RefreshTokenResponse> getTokenById(Long id) {
        return refreshTokenRepository.findById(id)
                .map(this::toRefreshTokenResponse);
    }
    public List<RefreshTokenResponse> getActiveTokensForUser(Long userId, String role) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRoleAndIsRevokedFalse(userId, role);
        return tokens.stream()
                .filter(t -> !t.isExpired())
                .map(this::toRefreshTokenResponse)
                .collect(Collectors.toList());
    }
    @Transactional
    public boolean revokeTokenById(Long id) {
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findById(id);
        if (tokenOpt.isPresent()) {
            RefreshToken token = tokenOpt.get();
            token.setIsRevoked(true);
            refreshTokenRepository.save(token);
            return true;
        }
        return false;
    }
    @Transactional
    public boolean deleteTokenById(Long id) {
        if (refreshTokenRepository.existsById(id)) {
            refreshTokenRepository.deleteById(id);
            return true;
        }
        return false;
    }
    public Map<String, Object> getTokenStatistics() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        stats.put("totalActiveTokens", refreshTokenRepository.countAllActiveTokens(now));
        stats.put("totalTokens", refreshTokenRepository.count());

        List<Object[]> byRole = refreshTokenRepository.countActiveTokensByRole(now);
        Map<String, Long> activeByRole = new HashMap<>();
        long userTokens = 0;
        long adminTokens = 0;

        for (Object[] row : byRole) {
            String role = (String) row[0];
            Long count = (Long) row[1];
            activeByRole.put(role, count);

            if ("user".equalsIgnoreCase(role)) {
                userTokens = count;
            } else if ("admin".equalsIgnoreCase(role)) {
                adminTokens = count;
            }
        }

        stats.put("activeTokensByRole", activeByRole);
        stats.put("userTokens", userTokens);
        stats.put("adminTokens", adminTokens);

        return stats;
    }

    private RefreshTokenResponse toRefreshTokenResponse(RefreshToken token) {
        String username = resolveUsername(token.getUserId(), token.getRole());
        String tokenPreview = token.getTokenPreview() != null && !token.getTokenPreview().isBlank()
                ? token.getTokenPreview()
                : tokenHashService.preview(token.getStoredToken());

        return new RefreshTokenResponse(
                token.getId(),
                tokenPreview,
                token.getUserId(),
                token.getRole(),
                username,
                token.getExpiryDate(),
                token.getCreatedAt(),
                token.getLastUsedAt(),
                token.getIsRevoked(),
                token.isExpired(),
                token.getDeviceInfo(),
                token.getIpAddress()
        );
    }

    private String resolveUsername(Long userId, String role) {
        try {
            switch (role.toLowerCase()) {
                case "user":
                    return userRepository.findById(userId)
                            .map(u -> u.getUsername())
                            .orElse("Unknown User #" + userId);
                case "admin":
                    return adminRepository.findById(userId)
                            .map(a -> a.getUsername())
                            .orElse("Unknown Admin #" + userId);
                default:
                    return "Unknown #" + userId;
            }
        } catch (Exception e) {
            return "Unknown #" + userId;
        }
    }

    private String extractDeviceInfo(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private Optional<RefreshToken> findByPresentedToken(String token) {
        String tokenHash = tokenHashService.hashToken(token);
        Optional<RefreshToken> hashedToken =
                Optional.ofNullable(refreshTokenRepository.findByTokenHash(tokenHash)).orElse(Optional.empty());
        if (hashedToken.isPresent()) {
            return hashedToken;
        }

        return refreshTokenRepository.findByToken(token)
                .filter(t -> t.getTokenHash() == null)
                .map(t -> migrateLegacyPlaintextToken(t, token));
    }

    private RefreshToken migrateLegacyPlaintextToken(RefreshToken tokenEntity, String rawToken) {
        tokenEntity.storeTokenMetadata(
                rawToken,
                tokenHashService.hashToken(rawToken),
                tokenHashService.preview(rawToken)
        );
        RefreshToken saved = refreshTokenRepository.save(tokenEntity);
        RefreshToken migrated = saved != null ? saved : tokenEntity;
        migrated.setPlaintextToken(rawToken);
        return migrated;
    }
}
