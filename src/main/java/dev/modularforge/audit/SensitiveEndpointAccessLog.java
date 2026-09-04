package dev.modularforge.audit;

import dev.modularforge.identity.model.Role;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sensitive_endpoint_access_logs", indexes = {
    @Index(name = "idx_sensitive_access_user_id", columnList = "user_id"),
    @Index(name = "idx_sensitive_access_role", columnList = "role"),
    @Index(name = "idx_sensitive_access_endpoint", columnList = "endpoint"),
    @Index(name = "idx_sensitive_access_created_at", columnList = "created_at"),
    @Index(name = "idx_sensitive_access_ip_address", columnList = "ip_address"),
    @Index(name = "idx_sensitive_access_severity", columnList = "severity")
})
public class SensitiveEndpointAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private SeverityLevel severity;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Lob
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "endpoint_category", length = 100)
    private String endpointCategory;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "email_alert_sent")
    private Boolean emailAlertSent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (emailAlertSent == null) {
            emailAlertSent = false;
        }
    }
    public enum SeverityLevel {
        LOW("Low Priority - Informational"),
        MEDIUM("Medium Priority - Requires Attention"),
        HIGH("High Priority - Immediate Review Required"),
        CRITICAL("Critical - Security Alert");

        private final String description;

        SeverityLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
