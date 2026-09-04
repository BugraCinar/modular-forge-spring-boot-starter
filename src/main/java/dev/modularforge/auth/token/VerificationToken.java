package dev.modularforge.auth.token;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_tokens", indexes = {
    @Index(name = "idx_verification_token", columnList = "token"),
    @Index(name = "idx_verification_token_hash", columnList = "token_hash"),
    @Index(name = "idx_verification_user", columnList = "user_id, role"),
    @Index(name = "idx_verification_role", columnList = "role"),
    @Index(name = "idx_verification_expiry_date", columnList = "expiry_date"),
    @Index(name = "idx_verification_created_date", columnList = "created_date")
})
@Data
public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "token_hash", unique = true, length = 128)
    private String tokenHash;

    @Column(name = "token_preview", length = 32)
    private String tokenPreview;

    @Transient
    private String plaintextToken;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false)
    private String role; // "user" or "admin"

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
    public VerificationToken() {
    }

    public VerificationToken(Long userId, String role) {
        this.token = java.util.UUID.randomUUID().toString();
        this.userId = userId;
        this.role = role;
        this.createdDate = LocalDateTime.now();
        this.expiryDate = this.createdDate.plusHours(24);
    }

    public String getToken() {
        return plaintextToken != null ? plaintextToken : token;
    }

    public String getStoredToken() {
        return token;
    }

    public void storeTokenMetadata(String plaintextToken, String tokenHash, String tokenPreview) {
        this.plaintextToken = plaintextToken;
        this.tokenHash = tokenHash;
        this.tokenPreview = tokenPreview;
        this.token = tokenPreview;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
