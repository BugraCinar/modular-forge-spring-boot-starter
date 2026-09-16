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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Temporary review probes: passing assertions reproduce current unsafe behavior, not fixes. */
class SecurityReviewProbeTest {
    @Test
    void logoutAllLeavesExistingAccessTokenUsable() throws Exception {
        var users = mock(UserRepository.class);
        var admins = mock(AdminRepository.class);
        var refresh = mock(RefreshTokenService.class);
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
        verify(refresh).revokeAllUserTokens(1L, "user");
        var nextRequest = new MockHttpServletRequest("GET", "/api/v1/profile");
        nextRequest.addHeader("Authorization", "Bearer " + token);
        try {
            new dev.modularforge.security.JwtAuthFilter(jwt, users, admins).doFilter(
                    nextRequest, new MockHttpServletResponse(), (request, response) -> {});
            assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication().isAuthenticated()).isTrue();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void authenticatedResetPageWritesRawTokenToAuditDetails() throws Exception {
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
            assertThat(saved.getValue().getDetails()).contains("review-only-secret-token");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void userProfileChangesVerifiedEmailWithoutPasswordOrVerification() {
        var users = mock(UserRepository.class);
        var admins = mock(AdminRepository.class);
        var passwords = mock(PasswordService.class);
        var notifications = mock(NotificationGateway.class);
        var verification = mock(VerificationTokenRepository.class);
        var service = new UserProfileService(users, admins, passwords, mock(RefreshTokenService.class),
                verification, notifications, mock(TokenHashService.class));
        User user = new User();
        user.setId(1L);
        user.setEmail("owner@example.test");
        user.setEmailVerified(true);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        var request = new UpdateUserProfileRequest();
        request.setEmail("replacement@example.test");
        var result = service.updateProfile(1L, request);
        assertThat(result.getEmail()).isEqualTo("replacement@example.test");
        assertThat(result.getEmailVerified()).isTrue();
        verifyNoInteractions(passwords, verification, notifications);
    }

    @Test
    void omittedRoleAuthenticatesAdminWithoutEnabledCaptcha() {
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
        when(refresh.createRefreshToken(eq(7L), eq("admin"), any())).thenReturn(new RefreshToken(7L, "admin", 30));
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
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(((AuthResponse) result.getBody()).getUser().getRole()).isEqualTo("admin");
        verifyNoInteractions(captcha);
    }

    @Test
    void repeatingSetupDisablesExistingTwoFactorWithoutOldCode() {
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
        service.generateSecret(7L);
        assertThat(credential.isEnabled()).isFalse();
        assertThat(credential.getEncryptedSecret()).isEqualTo("new-encrypted-secret");
        verify(cipher, never()).decrypt(anyString());
    }

    @Test
    void levelTwoAdminCanDeactivateAnotherLevelTwoAdmin() {
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
        service.deactivateAccount(2L, 1L);
        assertThat(target.getIsActive()).isFalse();
        verify(refresh).revokeAllUserTokens(2L, "admin");
    }

    @Test
    void adminCanClaimAnotherAccountsImageUrlAndReplaceRecoveryEmail() {
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
        service.updateAdminProfile(7L, request);
        assertThat(admin.getEmail()).isEqualTo("replacement@example.test");
        assertThat(service.verifyProfileImageOwnership(7L, otherImage)).isTrue();
        verifyNoInteractions(passwords);
    }
}
