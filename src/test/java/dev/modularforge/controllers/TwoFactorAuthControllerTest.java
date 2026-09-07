package dev.modularforge.controllers;

import dev.modularforge.identity.model.User;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.twofactor.TwoFactorAuthController;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.twofactor.dto.TwoFactorSetupResponse;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.RefreshTokenCookieService;
import dev.modularforge.twofactor.TwoFactorAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(value = TwoFactorAuthController.class, properties = "app.modules.two-factor.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
class TwoFactorAuthControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TwoFactorAuthService twoFactorAuthService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private RefreshTokenCookieService refreshTokenCookieService;

    @Autowired
    private ObjectMapper objectMapper;
    private static final String AUTH_HEADER = "Bearer fake-token";

    @BeforeEach
    void stubJwt() {
        when(jwtUtils.extractUserIdAsLong("fake-token")).thenReturn(1L);
    }

    @Test
    void setup_validAdmin_returns200WithSetupResponse() throws Exception {
        TwoFactorSetupResponse setupResponse = new TwoFactorSetupResponse();
        setupResponse.setSecret("BASE32SECRET");
        setupResponse.setQrCodeUrl("otpauth://totp/...");
        setupResponse.setManualEntryKey("MANU-AL-KEY");

        when(twoFactorAuthService.generateSecret(1L)).thenReturn(setupResponse);

        mockMvc.perform(post("/api/v1/admin/2fa/setup")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("BASE32SECRET"));
    }

    @Test
    void verify_serviceReturnsTrue_successTrue() throws Exception {
        when(twoFactorAuthService.verifyAndEnable(eq(1L), eq("123456"))).thenReturn(true);

        String body = objectMapper.writeValueAsString(Map.of("code", "123456"));

        mockMvc.perform(post("/api/v1/admin/2fa/verify")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("2FA has been enabled successfully"));
    }

    @Test
    void verify_serviceReturnsFalse_successFalse() throws Exception {
        when(twoFactorAuthService.verifyAndEnable(eq(1L), eq("999999"))).thenReturn(false);

        String body = objectMapper.writeValueAsString(Map.of("code", "999999"));

        mockMvc.perform(post("/api/v1/admin/2fa/verify")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid verification code"));
    }

    @Test
    void disable_serviceSucceeds_returns200WithSuccessTrue() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("code", "123456"));

        mockMvc.perform(post("/api/v1/admin/2fa/disable")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("2FA has been disabled successfully"));
    }

    @Test
    void disable_serviceThrowsIllegalArgument_returns400WithSuccessFalse() throws Exception {
        String errorMsg = "Invalid 2FA code";
        org.mockito.Mockito.doThrow(new IllegalArgumentException(errorMsg))
                .when(twoFactorAuthService).disable(eq(1L), eq("000000"));

        String body = objectMapper.writeValueAsString(Map.of("code", "000000"));

        mockMvc.perform(post("/api/v1/admin/2fa/disable")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(errorMsg));
    }

    @Test
    void status_returns200WithEnabledFlag() throws Exception {
        when(twoFactorAuthService.isTwoFactorEnabled(1L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/admin/2fa/status")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void verifyLogin_invalidCode_returns401() throws Exception {
        when(twoFactorAuthService.verifyCodeByUsername(eq("adminUser"), eq("111111"), anyString()))
                .thenReturn(false);

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "adminUser",
                "code", "111111",
                "challengeToken", "some-challenge-token"
        ));

        mockMvc.perform(post("/api/v1/admin/2fa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void verifyLogin_serviceThrowsException_returns401() throws Exception {
        when(twoFactorAuthService.verifyCodeByUsername(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("challenge token expired"));

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "adminUser",
                "code", "222222",
                "challengeToken", "bad-token"
        ));

        mockMvc.perform(post("/api/v1/admin/2fa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void verifyLogin_validCode_returns200WithAccessToken() throws Exception {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setUsername("adminUser");
        admin.setEmail("admin@test.com");
        admin.setFirstName("Test");
        admin.setLastName("Admin");
        admin.setProfilePicture(null);
        admin.setIsActive(true);
        admin.setLevel(0);
        admin.setLastLoginAt(LocalDateTime.now());
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token-12345678");
        refreshToken.setUserId(1L);

        when(twoFactorAuthService.verifyCodeByUsername(eq("adminUser"), eq("123456"), eq("valid-challenge")))
                .thenReturn(true);
        when(twoFactorAuthService.markLoginSuccessful("adminUser")).thenReturn(admin);
        when(jwtUtils.generateAdminToken(eq("adminUser"), eq(1L), eq(0), eq(0L)))
                .thenReturn("access-token-value");
        when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(eq(1L), eq("admin"), any()))
                .thenReturn(refreshToken);

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "adminUser",
                "code", "123456",
                "challengeToken", "valid-challenge"
        ));

        mockMvc.perform(post("/api/v1/admin/2fa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.user.username").value("adminUser"));
    }

    @Test
    void verifyLoginMovesRefreshTokenIntoCookieWhenEnabled() throws Exception {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setUsername("adminUser");
        admin.setEmail("admin@test.com");
        admin.setIsActive(true);
        admin.setLevel(0);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token-cookie-value");
        when(twoFactorAuthService.verifyCodeByUsername("adminUser", "123456", "valid-challenge"))
                .thenReturn(true);
        when(twoFactorAuthService.markLoginSuccessful("adminUser")).thenReturn(admin);
        when(jwtUtils.generateAdminToken("adminUser", 1L, 0, 0L)).thenReturn("access-token-value");
        when(refreshTokenService.createRefreshToken(eq(1L), eq("admin"), any())).thenReturn(refreshToken);
        when(refreshTokenCookieService.useCookies()).thenReturn(true);
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "adminUser", "code", "123456", "challengeToken", "valid-challenge"));

        mockMvc.perform(post("/api/v1/admin/2fa/verify-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"));

        verify(refreshTokenCookieService).setRefreshTokenCookie(any(), eq("refresh-token-cookie-value"));
    }
}
