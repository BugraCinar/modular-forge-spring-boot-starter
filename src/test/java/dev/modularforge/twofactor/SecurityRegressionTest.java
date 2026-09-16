package dev.modularforge.twofactor;

import dev.modularforge.admin.AdminProfileService;
import dev.modularforge.admin.dto.UpdateAdminProfileRequest;
import dev.modularforge.auth.*;
import dev.modularforge.auth.dto.*;
import dev.modularforge.auth.token.*;
import dev.modularforge.identity.*;
import dev.modularforge.identity.model.*;
import dev.modularforge.profile.UserProfileService;
import dev.modularforge.profile.dto.UpdateUserProfileRequest;
import dev.modularforge.ratelimit.RateLimitService;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.shared.notification.NotificationGateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression tests for the vulnerabilities reproduced in the initial review. */
class SecurityRegressionTest {
    @Test
    void logoutAllInvalidatesExistingAccessToken() throws Exception {
        var users = mock(UserRepository.class);
        var admins = mock(AdminRepository.class);
        var refresh = new RefreshTokenService();
        ReflectionTestUtils.setField(refresh, "userRepository", users);
        ReflectionTestUtils.setField(refresh, "adminRepository", admins);
        ReflectionTestUtils.setField(refresh, "refreshTokenRepository", mock(RefreshTokenRepository.class));
        var config = new dev.modularforge.security.JwtConfig();
        ReflectionTestUtils.setField(config, "secret", java.util.Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(config, "issuer", "review");
        ReflectionTestUtils.setField(config, "expiration", 900000L);
        var jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "jwtConfig", config);
        User user = new User();
        user.setId(1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        String token = jwt.generateUserToken("review-user", 1L, "app_user", user.currentAuthVersion());
        var controller = new RefreshTokenController();
        ReflectionTestUtils.setField(controller, "jwtUtils", jwt);
        ReflectionTestUtils.setField(controller, "refreshTokenService", refresh);
        ReflectionTestUtils.setField(controller, "refreshTokenCookieService", mock(RefreshTokenCookieService.class));
        var logout = new MockHttpServletRequest("POST", "/api/v1/auth/logout-all");
        logout.addHeader("Authorization", "Bearer " + token);
        assertThat(controller.logoutAll(logout, new MockHttpServletResponse()).getStatusCode().value()).isEqualTo(200);
        assertThat(user.currentAuthVersion()).isEqualTo(1L);
        var nextRequest = new MockHttpServletRequest("GET", "/api/v1/profile");
        nextRequest.addHeader("Authorization", "Bearer " + token);
        try {
            new dev.modularforge.security.JwtAuthFilter(jwt, users, admins).doFilter(
                    nextRequest, new MockHttpServletResponse(), (request, response) -> {});
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication()).isNull();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void authenticatedResetPageOmitsTokenFromAuditDetails() throws Exception {
        var repository = mock(dev.modularforge.audit.UserActivityLogRepository.class);
        var logger = new dev.modularforge.audit.UserActivityLogger(repository, new tools.jackson.databind.ObjectMapper());
        var interceptor = new dev.modularforge.audit.UserActivityLoggingInterceptor(logger);
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/reset-password");
        request.setQueryString("token=review-only-secret-token");
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "review-user", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            var handler = new org.springframework.web.method.HandlerMethod(new AuthController(),
                    AuthController.class.getMethod("showResetPasswordPage", String.class));
            interceptor.afterCompletion(request, new MockHttpServletResponse(), handler, null);
            var saved = org.mockito.ArgumentCaptor.forClass(dev.modularforge.audit.UserActivityLog.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getDetails()).doesNotContain("review-only-secret-token");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void userProfileRejectsUnverifiedEmailChange() {
        var users = mock(UserRepository.class);
        var admins = mock(AdminRepository.class);
        var passwords = mock(PasswordService.class);
        var notifications = mock(NotificationGateway.class);
        var verification = mock(VerificationTokenRepository.class);
        var service = new UserProfileService(users, passwords, mock(RefreshTokenService.class), mock(EmailChangeService.class));
        User user = new User();
        user.setId(1L);
        user.setEmail("owner@example.test");
        user.setEmailVerified(true);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = new UpdateUserProfileRequest();
        request.setEmail("replacement@example.test");
        assertThatThrownBy(() -> service.updateProfile(1L, request)).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        assertThat(user.getEmail()).isEqualTo("owner@example.test");
        verify(users, never()).save(any());
        verifyNoInteractions(passwords, verification, notifications);
    }

    @Test
    void omittedRoleCannotBypassAdminCaptcha() {
        var users = mock(UserRepository.class);
        var admins = mock(AdminRepository.class);
        var passwords = mock(PasswordService.class);
        var refresh = mock(RefreshTokenService.class);
        var jwt = mock(JwtUtils.class);
        var captcha = mock(CaptchaService.class);
        Admin admin = new Admin();
        admin.setId(7L);
        admin.setUsername("review-admin");
        admin.setSalt("test-salt");
        admin.setPasswordHash("test-hash");
        when(admins.findByUsernameOrEmail("review-admin", "review-admin")).thenReturn(Optional.of(admin));
        when(passwords.verifyPassword("test-password", "test-salt", "test-hash")).thenReturn(true);
        when(refresh.createRefreshToken(eq(7L), eq("admin"), anyLong(), any())).thenReturn(new RefreshToken(7L, "admin", 30));
        AuthService service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "passwordService", passwords);
        ReflectionTestUtils.setField(service, "refreshTokenService", refresh);
        ReflectionTestUtils.setField(service, "jwtUtils", jwt);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", service);
        ReflectionTestUtils.setField(controller, "captchaService", captcha);
        ReflectionTestUtils.setField(controller, "captchaEnabled", true);
        ReflectionTestUtils.setField(controller, "rateLimitService", mock(RateLimitService.class));
        ReflectionTestUtils.setField(controller, "refreshTokenCookieService", mock(RefreshTokenCookieService.class));
        LoginRequest request = new LoginRequest("review-admin", "test-password", null, null);
        var result = controller.login(request, new MockHttpServletRequest(), new MockHttpServletResponse());
        assertThat(result.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(admins);
        verifyNoInteractions(captcha);
    }

    @Test
    void repeatingSetupPreservesEnabledAuthenticator() {
        var admins = mock(AdminRepository.class);
        var credentials = mock(TwoFactorCredentialRepository.class);
        var cipher = mock(TwoFactorSecretCipher.class);
        Admin admin = new Admin();
        admin.setId(7L);
        admin.setUsername("review-admin");
        TwoFactorCredential credential = new TwoFactorCredential();
        credential.setAdminId(7L);
        credential.setEnabled(true);
        credential.setEncryptedSecret("old-encrypted-secret");
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(credentials.findByAdminId(7L)).thenReturn(Optional.of(credential));
        when(cipher.encrypt(anyString())).thenReturn("new-encrypted-secret");
        var service = new TwoFactorAuthService(admins, credentials, cipher);
        ReflectionTestUtils.setField(service, "appName", "Review");
        assertThatThrownBy(() -> service.generateSecret(7L)).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        assertThat(credential.isEnabled()).isTrue();
        assertThat(credential.getEncryptedSecret()).isEqualTo("old-encrypted-secret");
        verify(cipher, never()).decrypt(anyString());
    }

    @Test
    void levelTwoAdminCannotDeactivatePeer() {
        var admins = mock(AdminRepository.class);
        var refresh = mock(RefreshTokenService.class);
        Admin actor = new Admin();
        actor.setId(1L);
        actor.setLevel(2);
        Admin target = new Admin();
        target.setId(2L);
        target.setLevel(2);
        when(admins.findById(1L)).thenReturn(Optional.of(actor));
        when(admins.findById(2L)).thenReturn(Optional.of(target));
        var service = new AdminProfileService();
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "refreshTokenService", refresh);
        assertThatThrownBy(() -> service.deactivateAccount(2L, 1L)).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        assertThat(target.getIsActive()).isTrue();
        verifyNoInteractions(refresh);
    }

    @Test
    void adminCannotClaimForeignImageOrReplaceRecoveryEmail() {
        var admins = mock(AdminRepository.class);
        var users = mock(UserRepository.class);
        var passwords = mock(PasswordService.class);
        Admin admin = new Admin();
        admin.setId(7L);
        admin.setEmail("owner@example.test");
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(admins.save(any())).thenAnswer(i -> i.getArgument(0));
        var service = new AdminProfileService();
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordService", passwords);
        String otherImage = "https://images.example.test/profiles/user/profile_user_42_known.png";
        var request = new UpdateAdminProfileRequest();
        request.setEmail("replacement@example.test");
        request.setProfilePicture(otherImage);
        assertThatThrownBy(() -> service.updateAdminProfile(7L, request)).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        assertThat(admin.getEmail()).isEqualTo("owner@example.test");
        request.setEmail(null);
        assertThatThrownBy(() -> service.updateAdminProfile(7L, request)).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        assertThat(service.verifyProfileImageOwnership(7L, otherImage)).isFalse();
        verifyNoInteractions(passwords);
    }
}
