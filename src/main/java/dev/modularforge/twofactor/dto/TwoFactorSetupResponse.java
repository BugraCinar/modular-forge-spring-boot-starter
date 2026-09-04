package dev.modularforge.twofactor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TwoFactorSetupResponse {
    private String secret;
    private String qrCodeUrl;
    private String manualEntryKey;
}
