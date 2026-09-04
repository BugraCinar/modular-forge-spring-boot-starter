package dev.modulithforge.auth.dto;

public record SecondFactorRequiredResponse(
        String message,
        String username,
        boolean requiresTwoFactor,
        String twoFactorChallengeToken) {
}
