package dev.modularforge.audit;

import dev.modularforge.identity.model.Admin;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@org.springframework.data.mongodb.core.mapping.Document(collection = "admin_activity_log")
@Table(name = "admin_activity_log", indexes = {
    @Index(name = "idx_admin_activity_admin_id", columnList = "admin_id"),
    @Index(name = "idx_admin_activity_action", columnList = "action"),
    @Index(name = "idx_admin_activity_resource_type", columnList = "resource_type"),
    @Index(name = "idx_admin_activity_created_at", columnList = "created_at"),
    @Index(name = "idx_admin_activity_admin_date", columnList = "admin_id, created_at")
})
public class AdminActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @org.springframework.data.annotation.Transient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", insertable = false, updatable = false)
    private Admin admin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}