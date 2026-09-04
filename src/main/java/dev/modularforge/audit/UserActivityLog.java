package dev.modularforge.audit;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_activity_log", indexes = {
    @Index(name = "idx_user_activity_user", columnList = "user_id, role"),
    @Index(name = "idx_user_activity_action", columnList = "action"),
    @Index(name = "idx_user_activity_created_at", columnList = "created_at")
})
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // "user" or "admin"

    @Column(name = "action", nullable = false, length = 100)
    private String action; // LOGIN, LOGOUT, REGISTER, PASSWORD_RESET, PROFILE_UPDATE, etc.

    @Column(name = "resource_type", length = 50)
    private String resourceType; // Optional: type of resource affected

    @Column(name = "resource_id", length = 100)
    private String resourceId; // Optional: ID of resource affected

    @Column(name = "details", columnDefinition = "JSON")
    private String details; // JSON string with additional action details

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Lob
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "success", nullable = false)
    private Boolean success = true; // Whether the action was successful

    @Column(name = "failure_reason", length = 500)
    private String failureReason; // If success=false, reason for failure

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
