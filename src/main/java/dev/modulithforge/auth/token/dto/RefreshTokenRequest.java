package dev.modulithforge.auth.token.dto;

import dev.modulithforge.auth.token.RefreshToken;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
