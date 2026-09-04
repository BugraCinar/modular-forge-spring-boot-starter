package dev.modulithforge.identity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "admins", indexes = {
    @Index(name = "idx_admin_email", columnList = "email"),
    @Index(name = "idx_admin_username", columnList = "username"),
    @Index(name = "idx_admin_level", columnList = "level"),
    @Index(name = "idx_admin_is_active", columnList = "is_active"),
    @Index(name = "idx_admin_level_active", columnList = "level, is_active"),
    @Index(name = "idx_admin_created_by", columnList = "created_by")
})
public class Admin {

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

    @Column(name = "level", nullable = false)
    private Integer level = 2; // Default level for moderator

    @ElementCollection
    @CollectionTable(name = "admin_permissions", joinColumns = @JoinColumn(name = "admin_id"))
    @Column(name = "permission")
    private List<String> permissions;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    @Column(name = "auth_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long authVersion = 0L;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

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
