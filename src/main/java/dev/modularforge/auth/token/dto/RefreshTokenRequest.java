package dev.modularforge.auth.token.dto;


import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
