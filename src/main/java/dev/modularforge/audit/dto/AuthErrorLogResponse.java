package dev.modularforge.audit.dto;

import dev.modularforge.identity.model.Role;

import dev.modularforge.audit.AuthenticationErrorLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthErrorLogResponse {
    private Long id;
    private String errorType;
    private String errorDescription;
    private Long userId;
    private String role;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String endpoint;
    private String httpMethod;
    private String errorMessage;
    private String attemptedAction;
    private LocalDateTime createdAt;

    public static AuthErrorLogResponse from(AuthenticationErrorLog log) {
        return AuthErrorLogResponse.builder()
                .id(log.getId())
                .errorType(log.getErrorType().name())
                .errorDescription(log.getErrorType().getDescription())
                .userId(log.getUserId())
                .role(log.getRole())
                .username(log.getUsername())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .endpoint(log.getEndpoint())
                .httpMethod(log.getHttpMethod())
                .errorMessage(log.getErrorMessage())
                .attemptedAction(log.getAttemptedAction())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
