package dev.modularforge.auth.token;

import dev.modularforge.identity.model.User;

import dev.modularforge.auth.token.dto.RefreshTokenRequest;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.RefreshTokenCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class RefreshTokenController {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.refresh-token.cookie-name:refreshToken}")
    private String cookieName;

    @Autowired
    private RefreshTokenCookieService refreshTokenCookieService;
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            boolean refreshTokenFromRequestBody = isRefreshTokenFromRequestBody(httpRequest, request);
            String refreshTokenStr = extractRefreshToken(httpRequest, request);

            if (refreshTokenStr == null || refreshTokenStr.isEmpty()) {
                log.warn("Refresh token missing from request");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token is required"));
            }
            Optional<RefreshToken> refreshTokenOpt = refreshTokenService.verifyRefreshToken(refreshTokenStr);
            if (refreshTokenOpt.isEmpty()) {
                log.warn("Invalid or expired refresh token");
                clearRefreshTokenCookie(httpResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired refresh token. Please login again."));
            }

            RefreshToken oldRefreshToken = refreshTokenOpt.get();
            log.info("Refreshing token for userId={}, role={}",
                    oldRefreshToken.getUserId(), oldRefreshToken.getRole());

            Integer adminLevel = null;
            String userType = null;
            long authVersion;

            if ("admin".equals(oldRefreshToken.getRole())) {
                Optional<Admin> adminOpt = adminRepository.findById(oldRefreshToken.getUserId());
                if (adminOpt.isEmpty() || !Boolean.TRUE.equals(adminOpt.get().getIsActive())
                        || isLocked(adminOpt.get().getLockedUntil())) {
                    refreshTokenService.revokeAllUserTokens(oldRefreshToken.getUserId(), "admin");
                    clearRefreshTokenCookie(httpResponse);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Account is inactive or locked. Please login again."));
                }
                adminLevel = adminOpt.get().getLevel();
                authVersion = adminOpt.get().currentAuthVersion();
            } else {
                var userOpt = userRepository.findById(oldRefreshToken.getUserId());
                if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())
                        || isLocked(userOpt.get().getLockedUntil())) {
                    refreshTokenService.revokeAllUserTokens(oldRefreshToken.getUserId(), "user");
                    clearRefreshTokenCookie(httpResponse);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Account is inactive or locked. Please login again."));
                }
                userType = userOpt.get().getUserType().name().toLowerCase(Locale.ROOT);
                authVersion = userOpt.get().currentAuthVersion();
            }
            if (oldRefreshToken.getIssuedAuthVersion() == null || oldRefreshToken.getIssuedAuthVersion() != authVersion) {
                refreshTokenService.revokeRefreshToken(refreshTokenStr);
                clearRefreshTokenCookie(httpResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Session has been invalidated. Please login again."));
            }
            Optional<RefreshToken> newRefreshTokenOpt = refreshTokenService.rotateRefreshToken(oldRefreshToken, httpRequest);
            if (newRefreshTokenOpt.isEmpty()) {
                clearRefreshTokenCookie(httpResponse);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Refresh token reuse detected. Please login again."));
            }
            RefreshToken newRefreshToken = newRefreshTokenOpt.get();

            String username = resolveUsername(oldRefreshToken.getUserId(), oldRefreshToken.getRole());
            String accessToken;
            if ("admin".equals(oldRefreshToken.getRole())) {
                accessToken = jwtUtils.generateAdminToken(
                        username, oldRefreshToken.getUserId(), adminLevel, authVersion);
                log.debug("Generated admin access token with adminLevel: {}", adminLevel);
            } else {
                accessToken = jwtUtils.generateUserToken(
                        username, oldRefreshToken.getUserId(), userType, authVersion);
                log.debug("Generated user access token with userType: {}", userType);
            }
            if (refreshTokenCookieService.useCookies()) {
                setRefreshTokenCookie(httpResponse, newRefreshToken.getToken());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", accessToken);
            response.put("tokenType", "Bearer");
            response.put("expiresIn", jwtUtils.getAccessTokenExpiration());
            if (!refreshTokenCookieService.useCookies() || refreshTokenFromRequestBody) {
                response.put("refreshToken", newRefreshToken.getToken());
            }

            log.info("Token refresh successful for userId={}", oldRefreshToken.getUserId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during token refresh: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to refresh token"));
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Valid @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            String refreshTokenStr = extractRefreshToken(httpRequest, request);

            if (refreshTokenStr != null && !refreshTokenStr.isEmpty()) {
                boolean revoked = refreshTokenService.revokeRefreshToken(refreshTokenStr);
                if (revoked) {
                    log.info("Refresh token revoked successfully");
                } else {
                    log.warn("Refresh token not found for revocation");
                }
            }
            clearRefreshTokenCookie(httpResponse);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Logged out successfully");
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage(), e);
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully", "success", true));
        }
    }
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Authentication required"));
            }

            String accessToken = authHeader.substring(7);
            if (!jwtUtils.validateToken(accessToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid or expired access token"));
            }

            Long userId = jwtUtils.extractUserIdAsLong(accessToken);
            String role = jwtUtils.extractRole(accessToken);

            if (userId == null || role == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid access token"));
            }
            int revokedCount = refreshTokenService.revokeAllSessions(userId, role);
            clearRefreshTokenCookie(httpResponse);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Logged out from all devices successfully");
            response.put("revokedTokens", revokedCount);
            response.put("success", true);

            log.info("User userId={}, role={} logged out from all devices. {} tokens revoked.",
                    userId, role, revokedCount);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during logout-all: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to logout from all devices"));
        }
    }
    private String extractRefreshToken(HttpServletRequest httpRequest, RefreshTokenRequest request) {
        if (httpRequest.getCookies() != null) {
            for (Cookie cookie : httpRequest.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        if (request != null && request.getRefreshToken() != null) {
            return request.getRefreshToken();
        }

        return null;
    }
    private boolean isRefreshTokenFromRequestBody(HttpServletRequest httpRequest, RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return false;
        }

        if (httpRequest.getCookies() == null) {
            return true;
        }

        for (Cookie cookie : httpRequest.getCookies()) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return false;
            }
        }

        return true;
    }
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        refreshTokenCookieService.setRefreshTokenCookie(response, refreshToken);
    }
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        refreshTokenCookieService.clearRefreshTokenCookie(response);
    }

    private boolean isLocked(java.time.LocalDateTime lockedUntil) {
        return lockedUntil != null && lockedUntil.isAfter(java.time.LocalDateTime.now());
    }
    private String resolveUsername(Long userId, String role) {
        try {
            if ("admin".equalsIgnoreCase(role)) {
                return adminRepository.findById(userId)
                        .map(admin -> admin.getUsername())
                        .orElse("unknown_admin_" + userId);
            } else {
                return userRepository.findById(userId)
                        .map(user -> user.getUsername())
                        .orElse("unknown_user_" + userId);
            }
        } catch (Exception e) {
            log.error("Failed to resolve username for userId={}, role={}: {}", userId, role, e.getMessage());
            return "unknown_" + userId;
        }
    }
}
