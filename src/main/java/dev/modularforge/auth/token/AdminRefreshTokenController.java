package dev.modularforge.auth.token;

import dev.modularforge.auth.token.dto.RefreshTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@RestController
@RequestMapping("/api/v1/admin/refresh-tokens")
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
@RequiredArgsConstructor
@Slf4j
public class AdminRefreshTokenController {

    private final RefreshTokenService refreshTokenService;
    @GetMapping
    public ResponseEntity<?> getAllTokens(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean isRevoked,
            @RequestParam(required = false) String ipAddress) {
        try {
            List<RefreshTokenResponse> tokens = refreshTokenService.getFilteredTokens(
                    role, userId, isRevoked, ipAddress);

            return ResponseEntity.ok(tokens);

        } catch (Exception e) {
            log.error("Failed to retrieve refresh tokens", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve refresh tokens");
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> getTokenById(
            @PathVariable Long id) {
        try {
            Optional<RefreshTokenResponse> token = refreshTokenService.getTokenById(id);
            if (token.isPresent()) {
                return ResponseEntity.ok(token.get());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Refresh token not found with ID: " + id);

        } catch (Exception e) {
            log.error("Failed to retrieve refresh token id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve refresh token");
        }
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getActiveTokensForUser(
            @PathVariable Long userId,
            @RequestParam String role) {
        try {
            List<RefreshTokenResponse> tokens = refreshTokenService.getActiveTokensForUser(userId, role);
            return ResponseEntity.ok(tokens);

        } catch (Exception e) {
            log.error("Failed to retrieve active refresh tokens for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve user tokens");
        }
    }
    @GetMapping("/stats")
    public ResponseEntity<?> getTokenStatistics() {
        try {
            Map<String, Object> stats = refreshTokenService.getTokenStatistics();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to retrieve refresh token statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to retrieve token statistics");
        }
    }
    @PutMapping("/{id}/revoke")
    public ResponseEntity<?> revokeToken(
            @PathVariable Long id) {
        try {
            boolean revoked = refreshTokenService.revokeTokenById(id);
            if (revoked) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Refresh token revoked successfully");
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Refresh token not found with ID: " + id);

        } catch (Exception e) {
            log.error("Failed to revoke refresh token id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to revoke refresh token");
        }
    }
    @PutMapping("/revoke-all")
    public ResponseEntity<?> revokeAllUserTokens(
            @RequestParam Long userId,
            @RequestParam String role) {
        try {
            int count = refreshTokenService.revokeAllUserTokens(userId, role);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "All tokens revoked for user");
            response.put("revokedCount", count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to revoke refresh tokens for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to revoke user tokens");
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteToken(
            @PathVariable Long id) {
        try {
            boolean deleted = refreshTokenService.deleteTokenById(id);
            if (deleted) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Refresh token deleted successfully");
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Refresh token not found with ID: " + id);

        } catch (Exception e) {
            log.error("Failed to delete refresh token id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete refresh token");
        }
    }
    @PostMapping("/cleanup")
    public ResponseEntity<?> triggerCleanup() {
        try {
            int cleaned = refreshTokenService.cleanupExpiredTokens();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Token cleanup completed");
            response.put("tokensRemoved", cleaned);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to clean up expired refresh tokens", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to clean up tokens");
        }
    }
}
