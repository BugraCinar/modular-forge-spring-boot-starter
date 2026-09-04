package dev.modularforge.admin.dto;

import dev.modularforge.identity.model.Admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminManagementDTO {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profilePicture;
    private Integer level;
    private List<String> permissions;
    private Boolean isActive;
    private Integer loginAttempts;
    private LocalDateTime lockedUntil;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
