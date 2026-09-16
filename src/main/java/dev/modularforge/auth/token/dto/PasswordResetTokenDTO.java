package dev.modularforge.auth.token.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetTokenDTO {
    private Long id;
    private String token;
    private Long userId;
    private String role;
    private String username;
    private String email;
    private LocalDateTime expiryDate;
    private LocalDateTime createdDate;
    private Integer attemptCount;
    private String requestingIp;
    private boolean expired;
}
