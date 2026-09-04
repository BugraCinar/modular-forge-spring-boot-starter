package dev.modularforge.admin.dto;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDTO {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private String phone;
    private String bio;
    private String userType;
    private Boolean isActive;
    private Boolean emailVerified;
    private Boolean adminDeactivated;
    private Integer loginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime deactivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
