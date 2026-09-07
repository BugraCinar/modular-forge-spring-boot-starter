package dev.modularforge.auth;

import dev.modularforge.auth.dto.AuthResponse;
import dev.modularforge.auth.dto.ForgotPasswordRequest;
import dev.modularforge.auth.dto.LoginRequest;
import dev.modularforge.auth.dto.ResendVerificationRequest;
import dev.modularforge.auth.dto.ResetPasswordRequest;
import dev.modularforge.auth.token.RefreshTokenCookieService;
import dev.modularforge.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerCoverageTest {

    @Mock AuthService authService;
    @Mock CaptchaService captchaService;
    @Mock RateLimitService rateLimits;
    @Mock RefreshTokenCookieService cookies;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock CsrfToken csrfToken;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "captchaService", captchaService);
        ReflectionTestUtils.setField(controller, "rateLimitService", rateLimits);
        ReflectionTestUtils.setField(controller, "refreshTokenCookieService", cookies);
        ReflectionTestUtils.setField(controller, "captchaEnabled", false);
    }

    @Test
    void exposesCsrfHeaderAndToken() {
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");
        when(csrfToken.getToken()).thenReturn("value");
        assertThat(controller.csrf(csrfToken))
                .containsEntry("headerName", "X-CSRF-TOKEN")
                .containsEntry("token", "value");
    }

    @Test
    void loginRateLimitReturnsTooManyRequestsBeforeAuthentication() {
        LoginRequest login = new LoginRequest("alice", "pass", "user", null);
        when(request.getRemoteAddr()).thenReturn("192.0.2.1");
        when(rateLimits.isLoginRateLimitExceeded("192.0.2.1")).thenReturn(true);
        assertThat(controller.login(login, request, response).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void cookieMovementSkipsDisabledModeAndNullRefreshToken() {
        AuthResponse auth = AuthResponse.builder().success(true).refreshToken("refresh").build();
        when(rateLimits.isLoginRateLimitExceeded(null)).thenReturn(false);
        when(authService.login(anyLogin(), org.mockito.ArgumentMatchers.eq(request))).thenReturn(auth);
        when(cookies.useCookies()).thenReturn(false);
        assertThat(controller.login(new LoginRequest("alice", "pass", "user", null), request, response).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(auth.getRefreshToken()).isEqualTo("refresh");

        auth.setRefreshToken(null);
        when(cookies.useCookies()).thenReturn(true);
        assertThat(controller.login(new LoginRequest("alice", "pass", "user", null), request, response).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void captchaAcceptsAValidAdminTokenAndRejectsAnEmptyOne() {
        ReflectionTestUtils.setField(controller, "captchaEnabled", true);
        when(rateLimits.isLoginRateLimitExceeded(null)).thenReturn(false);

        LoginRequest emptyCaptcha = new LoginRequest("root", "pass", "admin", "");
        assertThat(controller.login(emptyCaptcha, request, response).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        LoginRequest validCaptcha = new LoginRequest("root", "pass", "admin", "captcha");
        when(captchaService.verifyCaptcha("captcha", null)).thenReturn(true);
        when(authService.login(validCaptcha, request)).thenReturn(AuthResponse.builder().success(false).build());
        assertThat(controller.login(validCaptcha, request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgotPasswordAlwaysReturnsEnumerationSafeMessageForBothServiceOutcomes() {
        ForgotPasswordRequest command = new ForgotPasswordRequest("alice@example.com", "user");
        when(authService.forgotPassword(command, request)).thenReturn(true, false);
        assertThat(controller.forgotPassword(command, request).getBody()).containsEntry("success", true);
        assertThat(controller.forgotPassword(command, request).getBody()).containsEntry("success", false);
    }

    @Test
    void resetPasswordReturnsMessagesForSuccessAndFailure() {
        ResetPasswordRequest command = new ResetPasswordRequest("token", "Password1!");
        when(authService.resetPassword(command, request)).thenReturn(true, false);
        var success = controller.resetPassword(command, request);
        var failure = controller.resetPassword(command, request);
        assertThat(success.getBody()).containsEntry("success", true);
        assertThat(success.getBody().get("message").toString()).contains("successfully");
        assertThat(failure.getBody()).containsEntry("success", false);
        assertThat(failure.getBody().get("message").toString()).contains("Invalid or expired");
    }

    @Test
    void verifyEmailBuildsViewsForBothResults() {
        when(authService.verifyEmail("token")).thenReturn(true, false);
        var success = controller.verifyEmail("token");
        var failure = controller.verifyEmail("token");
        assertThat(success.getViewName()).isEqualTo("verify-email-result");
        assertThat(success.getModel()).containsEntry("success", true);
        assertThat(success.getModel().get("message").toString()).contains("successfully");
        assertThat(failure.getModel()).containsEntry("success", false);
        assertThat(failure.getModel().get("message").toString()).contains("Invalid or expired");
    }

    @Test
    void resendVerificationReturnsServiceOutcome() {
        ResendVerificationRequest command = new ResendVerificationRequest("alice@example.com", "user");
        when(authService.resendVerificationEmail(command)).thenReturn(true, false);
        assertThat(controller.resendVerification(command).getBody()).containsEntry("success", true);
        assertThat(controller.resendVerification(command).getBody()).containsEntry("success", false);
    }

    @Test
    void buildsEmailChangeAndResetPasswordViews() {
        when(authService.verifyEmailChange("change-token"))
                .thenReturn(Map.of("success", true, "message", "changed"));
        var change = controller.verifyEmailChange("change-token");
        assertThat(change.getViewName()).isEqualTo("verify-email-result");
        assertThat(change.getModel()).containsEntry("success", true).containsEntry("message", "changed");

        var reset = controller.showResetPasswordPage("reset-token");
        assertThat(reset.getViewName()).isEqualTo("reset-password-page");
        assertThat(reset.getModel()).containsEntry("token", "reset-token");
    }

    private LoginRequest anyLogin() {
        return org.mockito.ArgumentMatchers.any(LoginRequest.class);
    }
}
