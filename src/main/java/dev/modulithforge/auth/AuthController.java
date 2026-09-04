package dev.modulithforge.auth;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.shared.error.ErrorResponse;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.security.web.csrf.CsrfToken;

import dev.modulithforge.auth.dto.AuthResponse;
import dev.modulithforge.auth.dto.ForgotPasswordRequest;
import dev.modulithforge.auth.dto.LoginRequest;
import dev.modulithforge.auth.dto.RegisterRequest;
import dev.modulithforge.auth.dto.ResendVerificationRequest;
import dev.modulithforge.auth.dto.ResetPasswordRequest;
import dev.modulithforge.auth.dto.UserSessionDTO;
import dev.modulithforge.auth.dto.SecondFactorRequiredResponse;
import dev.modulithforge.auth.AuthService;
import dev.modulithforge.auth.CaptchaService;
import dev.modulithforge.ratelimit.RateLimitService;
import dev.modulithforge.auth.token.RefreshTokenCookieService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RefreshTokenCookieService refreshTokenCookieService;

    @Value("${recaptcha.enabled:false}")
    private boolean captchaEnabled;

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        log.info("Registration attempt for username: {}", request.getUsername());

        AuthResponse response = authService.register(request, httpRequest);

        if (response.isSuccess()) {
            log.info("Registration successful for username: {}", request.getUsername());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Registration failed for username: {} - {}", request.getUsername(), response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        log.info("Login attempt for username: {}", request.getUsername());

        String clientIp = httpRequest.getRemoteAddr();
        if (rateLimitService.isLoginRateLimitExceeded(clientIp)) {
            log.warn("Login rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many login attempts. Please try again later.",
                    "message", "Rate limit exceeded for login endpoint"));
        }
        boolean isAdminLogin = "admin".equalsIgnoreCase(request.getRole());

        if (captchaEnabled && isAdminLogin) {
            if (request.getCaptchaToken() == null || request.getCaptchaToken().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "CAPTCHA is required for admin login");
                errorResponse.put("captchaRequired", true);
                log.warn("Admin login attempt without CAPTCHA for username: {}", request.getUsername());
                return ResponseEntity.badRequest().body(errorResponse);
            }

            boolean captchaValid = captchaService.verifyCaptcha(
                    request.getCaptchaToken(),
                    httpRequest.getRemoteAddr()
            );

            if (!captchaValid) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "CAPTCHA verification failed. Please try again.");
                errorResponse.put("captchaRequired", true);
                log.warn("CAPTCHA verification failed for username: {}", request.getUsername());
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }

        AuthResponse response = authService.login(request, httpRequest);

        if (response.isSuccess()) {
            moveRefreshTokenToCookie(response, httpResponse);
            log.info("Login successful for username: {}", request.getUsername());
            return ResponseEntity.ok(response);
        } else if (response.isRequiresTwoFactor()) {
            log.info("2FA required for username: {}", request.getUsername());
            SecondFactorRequiredResponse twoFactorResponse = new SecondFactorRequiredResponse(
                "Two-factor authentication required. Please enter your verification code.",
                request.getUsername(),
                true,
                response.getTwoFactorChallengeToken() // Pass the challenge token from AuthResponse
            );
            return ResponseEntity.status(202).body(twoFactorResponse);
        } else {
            log.warn("Login failed for username: {} - {}", request.getUsername(), response.getMessage());
            return ResponseEntity.status(401).body(response);
        }
    }

    private void moveRefreshTokenToCookie(AuthResponse response, HttpServletResponse httpResponse) {
        if (!refreshTokenCookieService.useCookies() || response.getRefreshToken() == null) {
            return;
        }

        refreshTokenCookieService.setRefreshTokenCookie(httpResponse, response.getRefreshToken());
        response.setRefreshToken(null);
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        log.info("Password reset request for email: {}", request.getEmail());

        boolean success = authService.forgotPassword(request, httpRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", "If the email exists in our system, a password reset link has been sent.");

        return ResponseEntity.ok(response);
    }
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        log.info("Password reset attempt with token");

        boolean success = authService.resetPassword(request, httpRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message",
                success ? "Password has been reset successfully. You can now login with your new password."
                        : "Invalid or expired reset token. Please request a new password reset.");

        if (success) {
            log.info("Password reset successful");
        } else {
            log.warn("Password reset failed - invalid or expired token");
        }

        return ResponseEntity.ok(response);
    }
    @GetMapping("/verify-email")
    public ModelAndView verifyEmail(@RequestParam String token) {

        log.info("Email verification attempt with token");

        boolean success = authService.verifyEmail(token);

        ModelAndView mav = new ModelAndView("verify-email-result");
        mav.addObject("success", success);
        mav.addObject("message", success ? "Email verified successfully. You can now login to your account."
                : "Invalid or expired verification token.");

        if (success) {
            log.info("Email verification successful");
        } else {
            log.warn("Email verification failed - invalid or expired token");
        }

        return mav;
    }
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, Object>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {

        log.info("Resend verification email request for email: {}", request.getEmail());

        boolean success = authService.resendVerificationEmail(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", "If the email exists and is not verified, a new verification link has been sent.");

        return ResponseEntity.ok(response);
    }
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Authentication Service");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }
    @GetMapping("/me")
    public ResponseEntity<UserSessionDTO> getCurrentUser(HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        UserSessionDTO session = authService.getCurrentUserSession(authHeader.substring(7));

        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(session);
    }
    @GetMapping("/verify-email-change")
    public ModelAndView verifyEmailChange(@RequestParam String token) {
        java.util.Map<String, Object> result = authService.verifyEmailChange(token);

        ModelAndView mav = new ModelAndView("verify-email-result");
        mav.addObject("success", result.get("success"));
        mav.addObject("message", result.get("message"));
        return mav;
    }
    @GetMapping("/reset-password")
    public ModelAndView showResetPasswordPage(@RequestParam String token) {
        ModelAndView mav = new ModelAndView("reset-password-page");
        mav.addObject("token", token);
        return mav;
    }
}
