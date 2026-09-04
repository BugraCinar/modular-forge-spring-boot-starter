package dev.modularforge.auth.token.dto;

import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RefreshTokenResponse {
    private Long id;
    private String tokenPreview; // Only show first 8 chars + "..." for security
    private Long userId;
    private String role;
    private String username; // Resolved from user tables
    private LocalDateTime expiryDate;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private Boolean isRevoked;
    private Boolean isExpired;
    private String deviceInfo;
    private String ipAddress;
}
