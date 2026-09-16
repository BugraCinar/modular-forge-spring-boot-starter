package dev.modularforge.audit;


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
@org.springframework.data.mongodb.core.mapping.Document(collection = "authentication_error_logs")
@Table(name = "authentication_error_logs", indexes = {
    @Index(name = "idx_auth_error_type", columnList = "error_type"),
    @Index(name = "idx_auth_error_user_id", columnList = "user_id"),
    @Index(name = "idx_auth_error_role", columnList = "role"),
    @Index(name = "idx_auth_error_created_at", columnList = "created_at"),
    @Index(name = "idx_auth_error_ip_address", columnList = "ip_address")
})
public class AuthenticationErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false, length = 30)
    private ErrorType errorType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempted_action", length = 200)
    private String attemptedAction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ErrorType {
        UNAUTHORIZED_401("Unauthorized - Authentication Required"),
        FORBIDDEN_403("Forbidden - Insufficient Permissions"),
        NOT_FOUND_404("Resource Not Found"),
        BAD_REQUEST_400("Bad Request"),
        INTERNAL_SERVER_ERROR_500("Internal Server Error"),
        INVALID_TOKEN("Invalid or Expired Token"),
        ACCESS_DENIED("Access Denied");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
