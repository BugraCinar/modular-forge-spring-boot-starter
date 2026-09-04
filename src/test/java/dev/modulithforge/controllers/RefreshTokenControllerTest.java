package dev.modulithforge.controllers;

import dev.modulithforge.auth.token.RefreshTokenController;
import dev.modulithforge.security.JwtUtils;

import tools.jackson.databind.ObjectMapper;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.auth.token.RefreshToken;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.identity.UserRepository;
import dev.modulithforge.auth.token.RefreshTokenService;
import dev.modulithforge.auth.token.RefreshTokenCookieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(RefreshTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
class RefreshTokenControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private AdminRepository adminRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenCookieService refreshTokenCookieService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void refresh_noToken_returns401WithError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(refreshTokenService.verifyRefreshToken("bad-token")).thenReturn(Optional.empty());

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "bad-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void refresh_validAdminToken_returns200WithAccessToken() throws Exception {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken("old-refresh-token");
        oldToken.setUserId(1L);
        oldToken.setRole("admin");

        RefreshToken newToken = new RefreshToken();
        newToken.setToken("new-refresh-token");
        newToken.setUserId(1L);
        newToken.setRole("admin");

        Admin admin = new Admin();
        admin.setId(1L);
        admin.setLevel(0);
        admin.setIsActive(true);

        when(refreshTokenService.verifyRefreshToken("old-refresh-token")).thenReturn(Optional.of(oldToken));
        when(refreshTokenService.rotateRefreshToken(eq(oldToken), any())).thenReturn(Optional.of(newToken));
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(jwtUtils.generateAdminToken(anyString(), eq(1L), eq(0), eq(0L)))
                .thenReturn("new-access-token");
        when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "old-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void refresh_inactiveAdmin_revokesSessionsAndReturns401() throws Exception {
        RefreshToken oldToken = new RefreshToken();
        oldToken.setToken("old-refresh-token");
        oldToken.setUserId(1L);
        oldToken.setRole("admin");

        Admin inactiveAdmin = new Admin();
        inactiveAdmin.setId(1L);
        inactiveAdmin.setIsActive(false);

        when(refreshTokenService.verifyRefreshToken("old-refresh-token")).thenReturn(Optional.of(oldToken));
        when(adminRepository.findById(1L)).thenReturn(Optional.of(inactiveAdmin));

        String body = objectMapper.writeValueAsString(Map.of("refreshToken", "old-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        org.mockito.Mockito.verify(refreshTokenService).revokeAllUserTokens(1L, "admin");
        org.mockito.Mockito.verify(refreshTokenService, org.mockito.Mockito.never()).rotateRefreshToken(any(), any());
    }

    @Test
    void logout_noToken_stillReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logoutAll_noAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));
    }

    @Test
    void logoutAll_nullUserIdFromToken_returns401() throws Exception {
        when(jwtUtils.validateToken("some-access-token")).thenReturn(true);
        when(jwtUtils.extractUserIdAsLong("some-access-token")).thenReturn(null);
        when(jwtUtils.extractRole("some-access-token")).thenReturn("admin");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer some-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid access token"));
    }

    @Test
    void logoutAll_validToken_returns200WithRevokedCount() throws Exception {
        when(jwtUtils.validateToken("valid-access-token")).thenReturn(true);
        when(jwtUtils.extractUserIdAsLong("valid-access-token")).thenReturn(1L);
        when(jwtUtils.extractRole("valid-access-token")).thenReturn("admin");
        when(refreshTokenService.revokeAllUserTokens(1L, "admin")).thenReturn(3);

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer valid-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.revokedTokens").value(3));
    }
}
