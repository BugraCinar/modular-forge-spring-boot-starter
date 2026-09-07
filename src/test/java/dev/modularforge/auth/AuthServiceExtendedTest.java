package dev.modularforge.auth;

import dev.modularforge.auth.dto.ForgotPasswordRequest;
import dev.modularforge.auth.dto.LoginRequest;
import dev.modularforge.auth.dto.RegisterRequest;
import dev.modularforge.auth.dto.ResendVerificationRequest;
import dev.modularforge.auth.dto.ResetPasswordRequest;
import dev.modularforge.auth.dto.VerifyPasswordRequest;
import dev.modularforge.auth.token.PasswordResetToken;
import dev.modularforge.auth.token.PasswordResetTokenRepository;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.auth.token.VerificationToken;
import dev.modularforge.auth.token.VerificationTokenRepository;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.ratelimit.RateLimitService;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.shared.audit.UserActivityAudit;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.shared.notification.NotificationGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceExtendedTest {

    @Mock UserRepository users;
    @Mock AdminRepository admins;
    @Mock PasswordService passwords;
    @Mock JwtUtils jwtUtils;
    @Mock NotificationGateway notifications;
    @Mock VerificationTokenRepository verificationTokens;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock RateLimitService rateLimits;
    @Mock UserActivityAudit activity;
    @Mock RefreshTokenService refreshTokens;
    @Mock TokenHashService tokenHashes;
    @Mock SecondFactorGateway secondFactor;
    @Mock HttpServletRequest request;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "passwordService", passwords);
        ReflectionTestUtils.setField(service, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(service, "emailService", notifications);
        ReflectionTestUtils.setField(service, "verificationTokenRepository", verificationTokens);
        ReflectionTestUtils.setField(service, "passwordResetTokenRepository", resetTokens);
        ReflectionTestUtils.setField(service, "rateLimitService", rateLimits);
        ReflectionTestUtils.setField(service, "userActivityLogger", activity);
        ReflectionTestUtils.setField(service, "refreshTokenService", refreshTokens);
        ReflectionTestUtils.setField(service, "tokenHashService", tokenHashes);
        ReflectionTestUtils.setField(service, "secondFactorGateway", secondFactor);
        when(tokenHashes.generateToken()).thenReturn("raw-token");
        when(tokenHashes.hashToken(anyString())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        when(tokenHashes.preview(anyString())).thenReturn("preview");
        when(request.getRemoteAddr()).thenReturn("192.0.2.10");
    }

    @Test
    void registrationChecksAdminCollisionsUsesUsernameFallbackAndHidesFailures() {
        RegisterRequest registration = registration();
        when(admins.existsByUsername("alice")).thenReturn(true);
        assertThat(service.register(registration, request).getMessage()).contains("Username already exists");

        reset(users, admins);
        when(admins.existsByEmail("alice@example.com")).thenReturn(true);
        assertThat(service.register(registration, request).getMessage()).contains("Email already exists");

        reset(users, admins);
        registration.setFirstName(null);
        when(passwords.generateSalt()).thenReturn("salt");
        when(passwords.hashPassword(registration.getPassword(), "salt")).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        assertThat(service.register(registration, request).isSuccess()).isTrue();
        verify(notifications).sendVerificationEmail("alice@example.com", "raw-token", "alice");

        doThrow(new IllegalStateException("db")).when(users).existsByUsername("alice");
        assertThat(service.register(registration, request).getMessage()).contains("Registration failed");
    }

    @Test
    void registrationUsesTheProvidedFirstNameInItsNotification() {
        RegisterRequest registration = registration();
        when(passwords.generateSalt()).thenReturn("salt");
        when(passwords.hashPassword(registration.getPassword(), "salt")).thenReturn("hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        assertThat(service.register(registration, request).isSuccess()).isTrue();

        verify(notifications).sendVerificationEmail("alice@example.com", "raw-token", registration.getFirstName());
    }

    @Test
    void loginAutoDetectsAdminAndHidesRepositoryFailures() {
        Admin admin = admin(20L);
        when(users.findByUsernameOrEmail("root", "root")).thenReturn(Optional.empty());
        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        when(passwords.verifyPassword("correct", "salt", "hash")).thenReturn(true);
        when(secondFactor.beginChallenge(20L)).thenReturn(Optional.empty());
        when(jwtUtils.generateAdminToken("root", 20L, 0, 0L)).thenReturn("jwt");
        when(refreshTokens.createRefreshToken(20L, "admin", request)).thenReturn(new RefreshToken(20L, "admin", 30L));
        LoginRequest login = new LoginRequest("root", "correct", null, null);
        assertThat(service.login(login, request).isSuccess()).isTrue();

        doThrow(new IllegalStateException("db")).when(users).findByUsernameOrEmail("broken", "broken");
        assertThat(service.login(new LoginRequest("broken", "pass", null, null), request).getMessage())
                .contains("Login failed");
    }

    @Test
    void inactiveUserSecurityAndGracePeriodReactivationAreEnforced() {
        User user = user(10L);
        user.setIsActive(false);
        user.setAdminDeactivated(true);
        when(users.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        assertThat(service.login(new LoginRequest("alice", "pass", "USER", null), request).getMessage()).contains("suspended");

        user.setAdminDeactivated(false);
        user.setDeactivatedAt(null);
        assertThat(service.login(new LoginRequest("alice", "pass", "user", null), request).getMessage()).contains("deactivated");

        user.setDeactivatedAt(LocalDateTime.now().minusDays(31));
        assertThat(service.login(new LoginRequest("alice", "pass", "user", null), request).getMessage()).contains("deactivated");

        user.setDeactivatedAt(LocalDateTime.now().minusDays(2));
        when(passwords.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
        assertThat(service.login(new LoginRequest("alice", "wrong", "user", null), request).getMessage()).contains("Invalid");

        when(passwords.verifyPassword("correct", "salt", "hash")).thenReturn(true);
        when(jwtUtils.generateUserToken("alice", 10L, "app_user", 0L)).thenReturn("jwt");
        when(refreshTokens.createRefreshToken(10L, "user", request)).thenReturn(new RefreshToken(10L, "user", 30L));
        assertThat(service.login(new LoginRequest("alice", "correct", "user", null), request).getMessage()).contains("reactivated");
        assertThat(user.getIsActive()).isTrue();
    }

    @Test
    void adminLoginWorksWhenTwoFactorModuleIsAbsent() {
        ReflectionTestUtils.setField(service, "secondFactorGateway", null);
        Admin admin = admin(20L);
        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        when(passwords.verifyPassword("pass", "salt", "hash")).thenReturn(true);
        when(jwtUtils.generateAdminToken("root", 20L, 0, 0L)).thenReturn("jwt");
        when(refreshTokens.createRefreshToken(20L, "admin", request)).thenReturn(new RefreshToken(20L, "admin", 30L));

        assertThat(service.login(new LoginRequest("root", "pass", "ADMIN", null), request).isSuccess()).isTrue();
    }

    @Test
    void expiredUserAndAdminLocksDoNotBlockLogin() {
        User user = user(10L);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(users.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(passwords.verifyPassword("pass", "salt", "hash")).thenReturn(true);
        when(jwtUtils.generateUserToken("alice", 10L, "app_user", 0L)).thenReturn("jwt");
        when(refreshTokens.createRefreshToken(10L, "user", request)).thenReturn(new RefreshToken(10L, "user", 30L));
        assertThat(service.login(new LoginRequest("alice", "pass", "user", null), request).isSuccess()).isTrue();

        Admin admin = admin(20L);
        admin.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        when(secondFactor.beginChallenge(20L)).thenReturn(Optional.empty());
        when(jwtUtils.generateAdminToken("root", 20L, 0, 0L)).thenReturn("admin-jwt");
        when(refreshTokens.createRefreshToken(20L, "admin", request)).thenReturn(new RefreshToken(20L, "admin", 30L));
        assertThat(service.login(new LoginRequest("root", "pass", "admin", null), request).isSuccess()).isTrue();
    }

    @Test
    void verifiesPasswordsForEveryRoleAndFailurePath() {
        User user = user(10L);
        Admin admin = admin(20L);
        when(passwords.verifyPassword("pass", "salt", "hash")).thenReturn(true);
        when(users.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        assertThat(service.verifyPassword(new VerifyPasswordRequest("alice", "pass", "USER"), request)).isTrue();
        when(users.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());
        assertThat(service.verifyPassword(new VerifyPasswordRequest("missing", "pass", "user"), request)).isFalse();

        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        assertThat(service.verifyPassword(new VerifyPasswordRequest("root", "pass", "ADMIN"), request)).isTrue();
        when(admins.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());
        assertThat(service.verifyPassword(new VerifyPasswordRequest("missing", "pass", "admin"), request)).isFalse();

        assertThat(service.verifyPassword(new VerifyPasswordRequest("alice", "pass", null), request)).isTrue();
        when(users.findByUsernameOrEmail("root", "root")).thenReturn(Optional.empty());
        assertThat(service.verifyPassword(new VerifyPasswordRequest("root", "pass", null), request)).isTrue();
        assertThat(service.verifyPassword(new VerifyPasswordRequest("missing", "pass", null), request)).isFalse();

        doThrow(new IllegalStateException("db")).when(users).findByUsernameOrEmail("broken", "broken");
        assertThat(service.verifyPassword(new VerifyPasswordRequest("broken", "pass", null), request)).isFalse();
    }

    @Test
    void forgotPasswordCoversUserAdminAutodetectionEnumerationAndFailure() {
        User user = user(10L);
        Admin admin = admin(20L);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        assertThat(service.forgotPassword(new ForgotPasswordRequest("alice@example.com", "USER"), request)).isTrue();
        verify(activity).logPasswordResetRequest(10L, "user", null);

        when(admins.findByEmail("root@example.com")).thenReturn(Optional.of(admin));
        assertThat(service.forgotPassword(new ForgotPasswordRequest("root@example.com", "ADMIN"), request)).isTrue();

        assertThat(service.forgotPassword(new ForgotPasswordRequest("alice@example.com", null), request)).isTrue();
        when(users.findByEmail("root@example.com")).thenReturn(Optional.empty());
        assertThat(service.forgotPassword(new ForgotPasswordRequest("root@example.com", null), request)).isTrue();
        when(users.findByEmail("none@example.com")).thenReturn(Optional.empty());
        when(admins.findByEmail("none@example.com")).thenReturn(Optional.empty());
        assertThat(service.forgotPassword(new ForgotPasswordRequest("none@example.com", null), request)).isTrue();

        doThrow(new IllegalStateException("db")).when(users).findByEmail("broken@example.com");
        assertThat(service.forgotPassword(new ForgotPasswordRequest("broken@example.com", "user"), request)).isFalse();
    }

    @Test
    void forgotPasswordUsesAllDisplayNameFallbacks() {
        User user = user(10L);
        user.setFirstName(null);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        service.forgotPassword(new ForgotPasswordRequest("alice@example.com", "user"), request);
        verify(notifications).sendPasswordResetEmail("alice@example.com", "raw-token", "alice");

        Admin admin = admin(20L);
        admin.setFirstName(null);
        when(admins.findByEmail("root@example.com")).thenReturn(Optional.of(admin));
        service.forgotPassword(new ForgotPasswordRequest("root@example.com", "admin"), request);
        verify(notifications).sendPasswordResetEmail("root@example.com", "raw-token", "root");

        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "resolveDisplayName", "missing", "user"))
                .isEqualTo("User");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "resolveDisplayName", "missing", "admin"))
                .isEqualTo("Admin");
        assertThat(ReflectionTestUtils.<String>invokeMethod(service, "resolveDisplayName", "missing", "other"))
                .isEqualTo("User");
    }

    @Test
    void resetPasswordRejectsMissingExpiredAndOverusedTokens() {
        assertThat(service.resetPassword(new ResetPasswordRequest(null, "new"), request)).isFalse();
        assertThat(service.resetPassword(new ResetPasswordRequest(" ", "new"), request)).isFalse();
        when(resetTokens.findByTokenHash("hash-missing")).thenReturn(Optional.empty());
        when(resetTokens.findByToken("missing")).thenReturn(Optional.empty());
        assertThat(service.resetPassword(new ResetPasswordRequest("missing", "new"), request)).isFalse();

        PasswordResetToken malformedLegacy = resetToken(10L, "user");
        malformedLegacy.setTokenHash("already-hashed");
        when(resetTokens.findByTokenHash("hash-malformed")).thenReturn(Optional.empty());
        when(resetTokens.findByToken("malformed")).thenReturn(Optional.of(malformedLegacy));
        assertThat(service.resetPassword(new ResetPasswordRequest("malformed", "new"), request)).isFalse();

        PasswordResetToken expired = resetToken(10L, "user");
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(resetTokens.findByTokenHash("hash-expired")).thenReturn(Optional.of(expired));
        assertThat(service.resetPassword(new ResetPasswordRequest("expired", "new"), request)).isFalse();
        verify(resetTokens).delete(expired);

        PasswordResetToken attempted = resetToken(10L, "user");
        attempted.setAttemptCount(3);
        when(resetTokens.findByTokenHash("hash-attempted")).thenReturn(Optional.of(attempted));
        assertThat(service.resetPassword(new ResetPasswordRequest("attempted", "new"), request)).isFalse();
        verify(resetTokens).delete(attempted);
    }

    @Test
    void resetsUserAndAdminPasswordsAndSupportsLegacyTokenLookup() {
        when(passwords.generateSalt()).thenReturn("new-salt");
        when(passwords.hashPassword("new-password", "new-salt")).thenReturn("new-hash");
        User user = user(10L);
        user.setLoginAttempts(4);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        PasswordResetToken userToken = resetToken(10L, "user");
        when(resetTokens.findByTokenHash("hash-user-token")).thenReturn(Optional.of(userToken));
        when(users.findById(10L)).thenReturn(Optional.of(user));
        assertThat(service.resetPassword(new ResetPasswordRequest("user-token", "new-password"), request)).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getAuthVersion()).isEqualTo(1L);

        Admin admin = admin(20L);
        PasswordResetToken adminToken = resetToken(20L, "admin");
        when(resetTokens.findByTokenHash("hash-admin-token")).thenReturn(Optional.of(adminToken));
        when(admins.findById(20L)).thenReturn(Optional.of(admin));
        assertThat(service.resetPassword(new ResetPasswordRequest("admin-token", "new-password"), request)).isTrue();
        assertThat(admin.getAuthVersion()).isEqualTo(1L);

        PasswordResetToken legacy = resetToken(30L, "other");
        legacy.setTokenHash(null);
        when(resetTokens.findByTokenHash("hash-legacy")).thenReturn(null);
        when(resetTokens.findByToken("legacy")).thenReturn(Optional.of(legacy));
        assertThat(service.resetPassword(new ResetPasswordRequest("legacy", "new-password"), request)).isTrue();
    }

    @Test
    void resetPasswordReturnsFalseWhenPersistenceFails() {
        PasswordResetToken token = resetToken(10L, "user");
        when(resetTokens.findByTokenHash("hash-token")).thenReturn(Optional.of(token));
        when(passwords.generateSalt()).thenThrow(new IllegalStateException("crypto"));
        assertThat(service.resetPassword(new ResetPasswordRequest("token", "new"), request)).isFalse();
    }

    @Test
    void verifiesEmailAcrossCurrentLegacyExpiredAndFailurePaths() {
        assertThat(service.verifyEmail(null)).isFalse();
        assertThat(service.verifyEmail(" ")).isFalse();
        when(verificationTokens.findByTokenHash("hash-missing")).thenReturn(Optional.empty());
        when(verificationTokens.findByToken("missing")).thenReturn(Optional.empty());
        assertThat(service.verifyEmail("missing")).isFalse();

        VerificationToken malformedLegacy = verificationToken(10L, "user");
        malformedLegacy.setTokenHash("already-hashed");
        when(verificationTokens.findByTokenHash("hash-malformed")).thenReturn(Optional.empty());
        when(verificationTokens.findByToken("malformed")).thenReturn(Optional.of(malformedLegacy));
        assertThat(service.verifyEmail("malformed")).isFalse();

        VerificationToken expired = verificationToken(10L, "user");
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(verificationTokens.findByTokenHash("hash-expired")).thenReturn(Optional.of(expired));
        assertThat(service.verifyEmail("expired")).isFalse();

        User user = user(10L);
        user.setEmailVerified(false);
        VerificationToken current = verificationToken(10L, "user");
        when(verificationTokens.findByTokenHash("hash-current")).thenReturn(Optional.of(current));
        when(users.findById(10L)).thenReturn(Optional.of(user));
        assertThat(service.verifyEmail("current")).isTrue();
        assertThat(user.getEmailVerified()).isTrue();

        VerificationToken legacy = verificationToken(20L, "admin");
        legacy.setTokenHash(null);
        when(verificationTokens.findByTokenHash("hash-legacy")).thenReturn(null);
        when(verificationTokens.findByToken("legacy")).thenReturn(Optional.of(legacy));
        assertThat(service.verifyEmail("legacy")).isTrue();

        when(tokenHashes.hashToken("broken")).thenThrow(new IllegalStateException("crypto"));
        assertThat(service.verifyEmail("broken")).isFalse();
    }

    @Test
    void resendsVerificationWithoutAccountEnumeration() {
        ResendVerificationRequest command = new ResendVerificationRequest("alice@example.com", "user");
        when(rateLimits.isEmailVerificationRateLimitExceeded(command.getEmail())).thenReturn(true);
        assertThat(service.resendVerificationEmail(command)).isTrue();

        when(rateLimits.isEmailVerificationRateLimitExceeded(command.getEmail())).thenReturn(false);
        User verified = user(10L);
        when(users.findByEmail(command.getEmail())).thenReturn(Optional.of(verified));
        assertThat(service.resendVerificationEmail(command)).isTrue();

        verified.setEmailVerified(false);
        assertThat(service.resendVerificationEmail(command)).isTrue();
        verified.setFirstName(null);
        assertThat(service.resendVerificationEmail(command)).isTrue();
        verify(notifications).sendVerificationEmail(command.getEmail(), "raw-token", "alice");

        when(users.findByEmail(command.getEmail())).thenReturn(Optional.empty());
        assertThat(service.resendVerificationEmail(command)).isTrue();

        doThrow(new IllegalStateException("rate store")).when(rateLimits)
                .isEmailVerificationRateLimitExceeded("broken@example.com");
        assertThat(service.resendVerificationEmail(new ResendVerificationRequest("broken@example.com", null))).isFalse();
    }

    @Test
    void resolvesCurrentSessionsForUsersAdminsAndInvalidTokens() {
        when(jwtUtils.validateToken("invalid")).thenReturn(false);
        assertThat(service.getCurrentUserSession("invalid")).isNull();

        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.extractUserIdAsLong("token")).thenReturn(null, 10L, 10L, 10L, 10L, 10L, 10L);
        when(jwtUtils.extractRole("token")).thenReturn("user", null, "user", "user", "admin", "admin", "other");
        assertThat(service.getCurrentUserSession("token")).isNull();
        assertThat(service.getCurrentUserSession("token")).isNull();

        User user = user(10L);
        when(users.findById(10L)).thenReturn(Optional.of(user), Optional.empty());
        assertThat(service.getCurrentUserSession("token").getRole()).isEqualTo("USER");
        assertThat(service.getCurrentUserSession("token")).isNull();

        Admin admin = admin(10L);
        when(admins.findById(10L)).thenReturn(Optional.of(admin), Optional.empty());
        assertThat(service.getCurrentUserSession("token").getRole()).isEqualTo("ADMIN");
        assertThat(service.getCurrentUserSession("token")).isNull();
        assertThat(service.getCurrentUserSession("token")).isNull();

        when(jwtUtils.validateToken("broken")).thenThrow(new IllegalStateException("jwt"));
        assertThat(service.getCurrentUserSession("broken")).isNull();
    }

    @Test
    void verifiesEmailChangeAndCleansInvalidState() {
        assertThatThrownBy(() -> service.verifyEmailChange("missing")).isInstanceOf(BadRequestException.class);

        VerificationToken wrongType = verificationToken(10L, "user");
        when(verificationTokens.findByTokenHash("hash-wrong")).thenReturn(Optional.of(wrongType));
        assertThatThrownBy(() -> service.verifyEmailChange("wrong")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("type");

        VerificationToken expired = verificationToken(10L, "email_change");
        expired.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(verificationTokens.findByTokenHash("hash-expired-change")).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.verifyEmailChange("expired-change")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");

        VerificationToken missingUser = verificationToken(99L, "email_change");
        when(verificationTokens.findByTokenHash("hash-missing-user")).thenReturn(Optional.of(missingUser));
        when(users.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyEmailChange("missing-user")).isInstanceOf(ResourceNotFoundException.class);

        User user = user(10L);
        VerificationToken change = verificationToken(10L, "email_change");
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(verificationTokens.findByTokenHash("hash-change")).thenReturn(Optional.of(change));
        user.setPendingEmail(null);
        assertThatThrownBy(() -> service.verifyEmailChange("change")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pending");
        user.setPendingEmail(" ");
        assertThatThrownBy(() -> service.verifyEmailChange("change")).isInstanceOf(BadRequestException.class);

        user.setPendingEmail("taken@example.com");
        when(users.existsByEmail("taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.verifyEmailChange("change")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in use");
        assertThat(user.getPendingEmail()).isNull();

        user.setPendingEmail("admin-taken@example.com");
        when(users.existsByEmail("admin-taken@example.com")).thenReturn(false);
        when(admins.existsByEmail("admin-taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.verifyEmailChange("change")).isInstanceOf(BadRequestException.class);

        user.setPendingEmail("new@example.com");
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(admins.existsByEmail("new@example.com")).thenReturn(false);
        assertThat(service.verifyEmailChange("change")).containsEntry("success", true);
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getPendingEmail()).isNull();
    }

    private RegisterRequest registration() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("Password1!");
        request.setFirstName("Alice");
        request.setLastName("Doe");
        request.setPhone("123");
        request.setBio("bio");
        return request;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setSalt("salt");
        user.setFirstName("Alice");
        user.setLastName("Doe");
        user.setProfilePicture("picture");
        user.setUserType(UserType.APP_USER);
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setLoginAttempts(0);
        user.setAdminDeactivated(false);
        user.setAuthVersion(0L);
        return user;
    }

    private Admin admin(Long id) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername("root");
        admin.setEmail("root@example.com");
        admin.setPasswordHash("hash");
        admin.setSalt("salt");
        admin.setFirstName("Root");
        admin.setLastName("Admin");
        admin.setProfilePicture("picture");
        admin.setIsActive(true);
        admin.setLoginAttempts(0);
        admin.setLevel(0);
        admin.setAuthVersion(0L);
        return admin;
    }

    private PasswordResetToken resetToken(Long userId, String role) {
        PasswordResetToken token = new PasswordResetToken(userId, role, "ip");
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5));
        return token;
    }

    private VerificationToken verificationToken(Long userId, String role) {
        VerificationToken token = new VerificationToken(userId, role);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(5));
        return token;
    }
}
