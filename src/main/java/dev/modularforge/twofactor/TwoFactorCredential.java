package dev.modularforge.twofactor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "two_factor_credentials", indexes = {
        @Index(name = "idx_two_factor_admin", columnList = "admin_id", unique = true),
        @Index(name = "idx_two_factor_challenge_hash", columnList = "challenge_hash")
})
@Getter
@Setter
public class TwoFactorCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false, unique = true)
    private Long adminId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "encrypted_secret", nullable = false, length = 512)
    private String encryptedSecret;

    @Column(name = "challenge_hash", length = 64)
    private String challengeHash;

    @Column(name = "challenge_expires_at")
    private LocalDateTime challengeExpiresAt;

    @Column(name = "challenge_attempts", nullable = false)
    private int challengeAttempts;

    @Column(name = "last_code_hash", length = 64)
    private String lastCodeHash;

    @Column(name = "last_code_accepted_at")
    private LocalDateTime lastCodeAcceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void updated() {
        updatedAt = LocalDateTime.now();
    }
}
