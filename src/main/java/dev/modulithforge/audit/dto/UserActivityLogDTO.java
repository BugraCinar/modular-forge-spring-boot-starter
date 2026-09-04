package dev.modulithforge.audit.dto;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogDTO {
    private Long id;
    private Long userId;
    private String role;
    private String username;
    private String email;
    private String action;
    private String resourceType;
    private String resourceId;
    private String details;
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String failureReason;
    private LocalDateTime createdAt;
}
