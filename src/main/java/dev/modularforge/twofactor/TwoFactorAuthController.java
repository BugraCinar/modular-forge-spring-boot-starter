package dev.modularforge.twofactor;

import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;
import dev.modularforge.shared.error.ErrorResponse;

import dev.modularforge.twofactor.dto.TwoFactorLoginRequest;
import dev.modularforge.twofactor.dto.TwoFactorSetupResponse;
import dev.modularforge.twofactor.dto.TwoFactorVerifyRequest;
import dev.modularforge.auth.dto.AuthResponse;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.RefreshTokenCookieService;
import dev.modularforge.twofactor.TwoFactorAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/admin/2fa")
@Slf4j
@ConditionalOnProperty(prefix = "app.modules.two-factor", name = "enabled", havingValue = "true")
public class TwoFactorAuthController {

    @Autowired
    private TwoFactorAuthService twoFactorAuthService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenCookieService refreshTokenCookieService;
    @PostMapping("/setup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TwoFactorSetupResponse> setup(@RequestHeader("Authorization") String token) {
        Long adminId = extractAdminIdFromToken(token);
        log.info("2FA setup requested for admin ID: {}", adminId);

        TwoFactorSetupResponse response = twoFactorAuthService.generateSecret(adminId);

        log.info("2FA setup completed for admin ID: {}", adminId);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        Long adminId = extractAdminIdFromToken(token);
        log.info("2FA verification requested for admin ID: {}", adminId);

        boolean isValid = twoFactorAuthService.verifyAndEnable(adminId, request.getCode());

        Map<String, Object> response = new HashMap<>();
        if (isValid) {
            response.put("success", true);
            response.put("message", "2FA has been enabled successfully");
            log.info("2FA enabled successfully for admin ID: {}", adminId);
        } else {
            response.put("success", false);
            response.put("message", "Invalid verification code");
            log.warn("Invalid 2FA verification code for admin ID: {}", adminId);
        }

        return ResponseEntity.ok(response);
    }
    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> disable(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody TwoFactorVerifyRequest request) {

        Long adminId = extractAdminIdFromToken(token);
        log.info("2FA disable requested for admin ID: {}", adminId);

        try {
            twoFactorAuthService.disable(adminId, request.getCode());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "2FA has been disabled successfully");
            log.info("2FA disabled successfully for admin ID: {}", adminId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            log.warn("Failed to disable 2FA for admin ID: {} - {}", adminId, e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> status(@RequestHeader("Authorization") String token) {
        Long adminId = extractAdminIdFromToken(token);
        boolean enabled = twoFactorAuthService.isTwoFactorEnabled(adminId);

        Map<String, Object> response = new HashMap<>();
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/verify-login")
    public ResponseEntity<?> verifyLogin(@Valid @RequestBody TwoFactorLoginRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        log.info("2FA login verification for username: {}", request.getUsername());

        try {
            boolean isValid = twoFactorAuthService.verifyCodeByUsername(
                request.getUsername(),
                request.getCode(),
                request.getChallengeToken()
            );

            if (!isValid) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Invalid verification code or challenge token. Please try logging in again.");
                log.warn("Invalid 2FA code or challenge token for username: {}", request.getUsername());
                return ResponseEntity.status(401).body(errorResponse);
            }
            Admin admin = twoFactorAuthService.markLoginSuccessful(request.getUsername());
            String accessToken = jwtUtils.generateAdminToken(
                    admin.getUsername(), admin.getId(), admin.getLevel(), admin.currentAuthVersion());
            RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(
                admin.getId(), "admin", httpRequest);

            AuthResponse authResponse = AuthResponse.builder()
                .success(true)
                .message("Admin login successful")
                .accessToken(accessToken)
                .refreshToken(refreshTokenEntity.getToken())
                .expiresIn(jwtUtils.getAccessTokenExpiration())
                .user(AuthResponse.UserInfo.builder()
                    .id(admin.getId())
                    .username(admin.getUsername())
                    .email(admin.getEmail())
                    .firstName(admin.getFirstName())
                    .lastName(admin.getLastName())
                    .profilePicture(admin.getProfilePicture())
                    .isActive(admin.getIsActive())
                    .emailVerified(true)
                    .role("admin")
                    .level(admin.getLevel())
                    .lastLoginAt(admin.getLastLoginAt())
                    .build())
                .build();

            if (refreshTokenCookieService.useCookies()) {
                refreshTokenCookieService.setRefreshTokenCookie(httpResponse, refreshTokenEntity.getToken());
                authResponse.setRefreshToken(null);
            }

            log.info("2FA login successful for username: {}", request.getUsername());
            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            log.error("2FA login failed for username: {} - {}", request.getUsername(), e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Invalid verification code or challenge token");
            return ResponseEntity.status(401).body(errorResponse);
        }
    }
    private Long extractAdminIdFromToken(String token) {
        String jwtToken = token.replace("Bearer ", "");
        return jwtUtils.extractUserIdAsLong(jwtToken);
    }
}
