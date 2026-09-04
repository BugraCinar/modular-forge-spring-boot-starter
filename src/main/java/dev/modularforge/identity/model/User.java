package dev.modularforge.identity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.modularforge.identity.model.UserType;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_username", columnList = "username"),
        @Index(name = "idx_user_is_active", columnList = "is_active"),
        @Index(name = "idx_user_email_verified", columnList = "email_verified"),
        @Index(name = "idx_user_user_type", columnList = "user_type"),
        @Index(name = "idx_user_active_verified", columnList = "is_active, email_verified"),
        @Index(name = "idx_user_cleanup_candidates", columnList = "is_active, admin_deactivated, anonymised_at, deactivated_at")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @JsonIgnore
    @Column(name = "salt", nullable = false, length = 64)
    private String salt;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "profile_picture", length = 500)
    private String profilePicture;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 50)
    private UserType userType = UserType.APP_USER;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "auth_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long authVersion = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
    @Column(name = "anonymised_at")
    private LocalDateTime anonymisedAt;
    @Column(name = "admin_deactivated", nullable = false)
    private Boolean adminDeactivated = false;
    @Column(name = "pending_email", length = 255)
    private String pendingEmail;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public long currentAuthVersion() {
        return authVersion == null ? 0L : authVersion;
    }

    public void invalidateAccessTokens() {
        authVersion = Math.addExact(currentAuthVersion(), 1L);
    }
}
