package dev.modularforge.auth.token.dto;

import dev.modularforge.auth.token.RefreshToken;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
