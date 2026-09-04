package dev.modulithforge.auth.dto;

import dev.modulithforge.auth.token.RefreshToken;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.identity.model.UserType;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long expiresIn; // seconds
    private UserInfo user;
    private String message;
    private boolean success;
    private boolean requiresTwoFactor; // Indicates 2FA is required for admin login
    private String twoFactorChallengeToken; // Challenge token to validate 2FA submission

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String profilePicture;
        private boolean isActive;
        private boolean emailVerified;
        private String role; // "user" or "admin" — auth-level role
        private String userType;
        private Integer level; // For admins only
        private LocalDateTime lastLoginAt;
    }
}
