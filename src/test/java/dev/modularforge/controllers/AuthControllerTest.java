package dev.modularforge.controllers;

import dev.modularforge.auth.AuthController;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.auth.dto.AuthResponse;
import dev.modularforge.auth.dto.LoginRequest;
import dev.modularforge.auth.dto.RegisterRequest;
import dev.modularforge.auth.dto.UserSessionDTO;
import dev.modularforge.auth.AuthService;
import dev.modularforge.auth.CaptchaService;
import dev.modularforge.ratelimit.RateLimitService;
import dev.modularforge.auth.token.RefreshTokenCookieService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest extends BaseControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AuthController authController;

    @MockitoBean
    AuthService authService;
    @MockitoBean
    CaptchaService captchaService;
    @MockitoBean
    RateLimitService rateLimitService;
    @MockitoBean
    RefreshTokenCookieService refreshTokenCookieService;

    @AfterEach
    void resetCaptchaFlag() {
        ReflectionTestUtils.setField(authController, "captchaEnabled", false);
    }

    @Test
    @DisplayName("register - service succeeds → 200 OK")
    void register_success_returns200() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@test.com");
        req.setPassword("StrongPassword1!");

        AuthResponse response = AuthResponse.builder().success(true).message("Check your email").build();
        when(authService.register(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("register - service returns failure → 400 Bad Request")
    void register_failure_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@test.com");
        req.setPassword("StrongPassword1!");

        AuthResponse response = AuthResponse.builder().success(false).message("Username already taken").build();
        when(authService.register(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("login - captcha disabled, valid login → 200 OK")
    void login_captchaDisabled_success_returns200() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .success(true).accessToken("jwt-token").build();
        when(authService.login(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "pass", "admin", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("login - cookie mode puts refresh token in HttpOnly cookie only")
    void login_cookieMode_removesRefreshTokenFromJson() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .success(true).accessToken("jwt-token").refreshToken("long-lived-token").build();
        when(authService.login(any(), any())).thenReturn(resp);
        when(refreshTokenCookieService.useCookies()).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "pass", "admin", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(refreshTokenCookieService).setRefreshTokenCookie(any(), eq("long-lived-token"));
    }

    @Test
    @DisplayName("login - 2FA required → 202 Accepted with requiresTwoFactor response")
    void login_requiresTwoFactor_returns202() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .success(false)
                .requiresTwoFactor(true)
                .twoFactorChallengeToken("challenge-abc")
                .build();
        when(authService.login(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "pass", "admin", null)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requiresTwoFactor").value(true));
    }

    @Test
    @DisplayName("login - service fails → 401 Unauthorized")
    void login_failure_returns401() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .success(false).requiresTwoFactor(false).message("Invalid credentials").build();
        when(authService.login(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "wrongpass", "user", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login - captcha enabled, admin login, no captchaToken → 400")
    void login_captchaEnabled_adminLogin_noToken_returns400() throws Exception {
        ReflectionTestUtils.setField(authController, "captchaEnabled", true);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "pass", "admin", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.captchaRequired").value(true));
    }

    @Test
    @DisplayName("login - captcha enabled, admin login, invalid captcha → 400")
    void login_captchaEnabled_adminLogin_invalidCaptcha_returns400() throws Exception {
        ReflectionTestUtils.setField(authController, "captchaEnabled", true);
        when(captchaService.verifyCaptcha(any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("admin", "pass", "admin", "bad-captcha")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.captchaRequired").value(true));
    }

    @Test
    @DisplayName("login - captcha enabled, NON-admin login, captcha check skipped → delegates to service")
    void login_captchaEnabled_nonAdminLogin_captchaSkipped() throws Exception {
        ReflectionTestUtils.setField(authController, "captchaEnabled", true);
        AuthResponse resp = AuthResponse.builder().success(true).build();
        when(authService.login(any(), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", "pass", "user", null)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /me - missing Authorization header → 401")
    void me_missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me - non-Bearer token → 401")
    void me_nonBearerHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Token something"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me - service returns null session → 401")
    void me_nullSession_returns401() throws Exception {
        when(authService.getCurrentUserSession(any())).thenReturn(null);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer fake.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /me - valid session → 200 with session data")
    void me_validSession_returns200() throws Exception {
        UserSessionDTO session = new UserSessionDTO(1L, "Alice", "Smith", null, "ADMIN", null, 0);
        when(authService.getCurrentUserSession(any())).thenReturn(session);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer fake.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("GET /health → 200 with status=UP")
    void healthCheck_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/auth/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String loginJson(String username, String password, String role, String captchaToken) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setRole(role);
        req.setCaptchaToken(captchaToken);
        return objectMapper.writeValueAsString(req);
    }
}
