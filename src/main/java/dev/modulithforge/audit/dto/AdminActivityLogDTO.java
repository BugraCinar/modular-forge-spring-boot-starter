package dev.modulithforge.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminActivityLogDTO {
    private Long id;
    private Long adminId;
    private String adminUsername;
    private String adminEmail;
    private String action;
    private String resourceType;
    private String resourceId;
    private String details;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
