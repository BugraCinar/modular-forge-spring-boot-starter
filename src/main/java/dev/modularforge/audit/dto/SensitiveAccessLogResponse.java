package dev.modularforge.audit.dto;

import dev.modularforge.identity.model.Role;

import dev.modularforge.audit.SensitiveEndpointAccessLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveAccessLogResponse {
    private Long id;
    private String severity;
    private String severityDescription;
    private Long userId;
    private String role;
    private String username;
    private String ipAddress;
    private String userAgent;
    private String endpoint;
    private String httpMethod;
    private String endpointCategory;
    private String description;
    private Integer responseStatus;
    private Boolean emailAlertSent;
    private LocalDateTime createdAt;

    public static SensitiveAccessLogResponse from(SensitiveEndpointAccessLog log) {
        return SensitiveAccessLogResponse.builder()
                .id(log.getId())
                .severity(log.getSeverity().name())
                .severityDescription(log.getSeverity().getDescription())
                .userId(log.getUserId())
                .role(log.getRole())
                .username(log.getUsername())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .endpoint(log.getEndpoint())
                .httpMethod(log.getHttpMethod())
                .endpointCategory(log.getEndpointCategory())
                .description(log.getDescription())
                .responseStatus(log.getResponseStatus())
                .emailAlertSent(log.getEmailAlertSent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
