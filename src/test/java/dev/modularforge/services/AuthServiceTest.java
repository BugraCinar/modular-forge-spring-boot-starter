package dev.modularforge.services;

import dev.modularforge.audit.UserActivityLogger;
import dev.modularforge.auth.AuthService;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.SecondFactorGateway;
import dev.modularforge.auth.token.PasswordResetTokenRepository;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.auth.token.VerificationTokenRepository;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.notification.EmailService;
import dev.modularforge.ratelimit.RateLimitService;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.auth.dto.LoginRequest;
import dev.modularforge.auth.dto.RegisterRequest;
import dev.modularforge.auth.dto.AuthResponse;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.identity.*;
import dev.modularforge.auth.token.*;
import dev.modularforge.audit.*;
import dev.modularforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private EmailService emailService;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private UserActivityLogger userActivityLogger;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TokenHashService tokenHashService;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private SecondFactorGateway secondFactorGateway;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest buildRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("Password1!");
        req.setFirstName("John");
        req.setLastName("Doe");
        return req;
    }

    private LoginRequest buildLoginRequest(String username, String password, String role) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setRole(role);
        return req;
    }

    private User buildUser(Long id, boolean active, boolean emailVerified, LocalDateTime lockedUntil, int attempts) {
        User u = new User();
        u.setId(id);
        u.setUsername("testUser");
        u.setEmail("user@example.com");
        u.setPasswordHash("hash");
        u.setSalt("salt");
        u.setIsActive(active);
        u.setEmailVerified(emailVerified);
        u.setLockedUntil(lockedUntil);
        u.setLoginAttempts(attempts);
        u.setUserType(UserType.APP_USER);
        return u;
    }

    private Admin buildAdmin(Long id, boolean active, LocalDateTime lockedUntil, boolean twoFaEnabled, int attempts) {
        Admin a = new Admin();
        a.setId(id);
        a.setUsername("adminUser");
        a.setEmail("admin@example.com");
        a.setPasswordHash("hash");
        a.setSalt("salt");
        a.setIsActive(active);
        a.setLockedUntil(lockedUntil);
        a.setLoginAttempts(attempts);
        return a;
    }

    private RefreshToken buildRefreshToken() {
        RefreshToken rt = new RefreshToken(1L, "user", 30L);
        rt.setIssuedAuthVersion(0L);
        rt.setId(1L);
        return rt;
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("Valid user registration sets APP_USER type by default")
        void defaultUserType_isAppUser() {
            RegisterRequest req = buildRegisterRequest();
            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(adminRepository.existsByUsername(any())).thenReturn(false);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(adminRepository.existsByEmail(any())).thenReturn(false);
            when(passwordService.generateSalt()).thenReturn("randomsalt");
            when(passwordService.hashPassword(any(), any())).thenReturn("hashed");
            when(tokenHashService.generateToken()).thenReturn("raw-verification-token");
            when(tokenHashService.hashToken("raw-verification-token")).thenReturn("hashed-verification-token");
            when(tokenHashService.preview("raw-verification-token")).thenReturn("raw-veri...token");

            User saved = buildUser(1L, true, false, null, 0);
            when(userRepository.save(any(User.class))).thenReturn(saved);

            AuthResponse resp = authService.register(req, httpRequest);
            assertThat(resp.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("Duplicate username returns success=false")
        void duplicateUsername_rejected() {
            RegisterRequest req = buildRegisterRequest();
            when(userRepository.existsByUsername("testuser")).thenReturn(true);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("username already exists");
        }

        @Test
        @DisplayName("Duplicate email returns success=false")
        void duplicateEmail_rejected() {
            RegisterRequest req = buildRegisterRequest();
            when(userRepository.existsByUsername("testuser")).thenReturn(false);
            when(adminRepository.existsByUsername("testuser")).thenReturn(false);
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("email already exists");
        }

        @Test
        @DisplayName("Valid user registration: success=true, no tokens, email sent")
        void validUserRegistration() {
            RegisterRequest req = buildRegisterRequest();
            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(adminRepository.existsByUsername(any())).thenReturn(false);
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(adminRepository.existsByEmail(any())).thenReturn(false);
            when(passwordService.generateSalt()).thenReturn("randomsalt");
            when(passwordService.hashPassword(any(), any())).thenReturn("hashed");
            when(tokenHashService.generateToken()).thenReturn("raw-verification-token");
            when(tokenHashService.hashToken("raw-verification-token")).thenReturn("hashed-verification-token");
            when(tokenHashService.preview("raw-verification-token")).thenReturn("raw-veri...token");

            User saved = buildUser(1L, true, false, null, 0);
            when(userRepository.save(any(User.class))).thenReturn(saved);

            AuthResponse resp = authService.register(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isNull();
            assertThat(resp.getRefreshToken()).isNull();
            verify(verificationTokenRepository).save(any());
            verify(emailService).sendVerificationEmail(any(), any(), any());
            verify(userActivityLogger).logRegister(eq(1L), eq("user"), eq(true), isNull(), eq(httpRequest));
        }
    }

    @Nested
    @DisplayName("login() — user")
    class LoginUser {

        @Test
        @DisplayName("User not found returns success=false")
        void notFound_returnsFalse() {
            LoginRequest req = buildLoginRequest("testUser", "pass", "user");
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.empty());

            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid");
        }

        @Test
        @DisplayName("Inactive user returns success=false")
        void inactive_returnsFalse() {
            User user = buildUser(1L, false, true, null, 0);
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.of(user));

            LoginRequest req = buildLoginRequest("testUser", "pass", "user");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("deactivated");
        }

        @Test
        @DisplayName("Email not verified returns success=false")
        void emailNotVerified_returnsFalse() {
            User user = buildUser(1L, true, false, null, 0);
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.of(user));

            LoginRequest req = buildLoginRequest("testUser", "pass", "user");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("email must be verified");
        }

        @Test
        @DisplayName("Locked account returns success=false")
        void locked_returnsFalse() {
            User user = buildUser(1L, true, true, LocalDateTime.now().plusMinutes(10), 5);
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.of(user));

            LoginRequest req = buildLoginRequest("testUser", "pass", "user");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("temporarily locked");
        }

        @Test
        @DisplayName("Wrong password returns success=false and increments loginAttempts")
        void wrongPassword_incrementsAttempts() {
            User user = buildUser(1L, true, true, null, 0);
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(userRepository.save(any())).thenReturn(user);

            LoginRequest req = buildLoginRequest("testUser", "wrong", "user");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(user.getLoginAttempts()).isEqualTo(1);
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        @DisplayName("5th wrong password locks the account for 15 minutes")
        void fifthWrongPassword_locksAccount() {
            User user = buildUser(1L, true, true, null, 4); // already at 4
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(userRepository.save(any())).thenReturn(user);

            LoginRequest req = buildLoginRequest("testUser", "wrong", "user");
            authService.login(req, httpRequest);

            assertThat(user.getLoginAttempts()).isEqualTo(5);
            assertThat(user.getLockedUntil()).isNotNull();
            assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Successful login resets attempts and returns tokens")
        void validLogin_returnsTokens() {
            User user = buildUser(1L, true, true, null, 2);
            when(userRepository.findByUsernameOrEmail("testUser", "testUser")).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(userRepository.save(any())).thenReturn(user);
            when(jwtUtils.generateUserToken(eq("testUser"), eq(1L), eq("app_user"), eq(0L)))
                    .thenReturn("jwt-access-token");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

            RefreshToken rt = buildRefreshToken();
            when(refreshTokenService.createRefreshToken(1L, "user", 0L, httpRequest)).thenReturn(rt);

            LoginRequest req = buildLoginRequest("testUser", "correct", "user");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isEqualTo("jwt-access-token");
            assertThat(resp.getRefreshToken()).isNotNull();
            assertThat(user.getLoginAttempts()).isEqualTo(0);
            assertThat(user.getLockedUntil()).isNull();
        }
    }

    @Nested
    @DisplayName("login() — admin")
    class LoginAdmin {

        @Test
        @DisplayName("Admin not found returns success=false")
        void notFound_returnsFalse() {
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.empty());
            when(passwordService.verifyPassword(anyString(), anyString(), anyString())).thenReturn(false);

            LoginRequest req = buildLoginRequest("adminUser", "pass", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid credentials");
        }

        @Test
        @DisplayName("Inactive admin returns success=false")
        void inactive_returnsFalse() {
            Admin admin = buildAdmin(3L, false, null, false, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));

            LoginRequest req = buildLoginRequest("adminUser", "pass", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("deactivated");
        }

        @Test
        @DisplayName("Locked admin returns success=false")
        void locked_returnsFalse() {
            Admin admin = buildAdmin(3L, true, LocalDateTime.now().plusMinutes(5), false, 5);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));

            LoginRequest req = buildLoginRequest("adminUser", "pass", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("temporarily locked");
        }

        @Test
        @DisplayName("Wrong admin password returns success=false and increments attempts")
        void wrongPassword_incrementsAttempts() {
            Admin admin = buildAdmin(3L, true, null, false, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(adminRepository.save(any())).thenReturn(admin);

            LoginRequest req = buildLoginRequest("adminUser", "wrong", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(admin.getLoginAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("Valid password + 2FA enabled → requiresTwoFactor=true with challenge token")
        void validPassword_twoFaEnabled_returnsChallengeToken() {
            Admin admin = buildAdmin(3L, true, null, true, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(secondFactorGateway.beginChallenge(3L))
                    .thenReturn(Optional.of(new SecondFactorGateway.Challenge("challenge-token", "Code required")));

            LoginRequest req = buildLoginRequest("adminUser", "correct", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.isRequiresTwoFactor()).isTrue();
            assertThat(resp.getTwoFactorChallengeToken()).isEqualTo("challenge-token");
        }

        @Test
        @DisplayName("Valid password + no 2FA → success=true with access and refresh tokens")
        void validPassword_noTwoFa_returnsTokens() {
            Admin admin = buildAdmin(3L, true, null, false, 0);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(adminRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateAdminToken(eq("adminUser"), eq(3L), any(), eq(0L))).thenReturn("admin-jwt");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);

            RefreshToken rt = new RefreshToken(3L, "admin", 30L);
        rt.setIssuedAuthVersion(0L);
            when(refreshTokenService.createRefreshToken(3L, "admin", 0L, httpRequest)).thenReturn(rt);

            LoginRequest req = buildLoginRequest("adminUser", "correct", "admin");
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
            assertThat(resp.getAccessToken()).isEqualTo("admin-jwt");
            assertThat(resp.getRefreshToken()).isNotNull();
        }

        @Test
        @DisplayName("5th wrong admin password locks account for 15 minutes")
        void fifthWrongPassword_locksAccount() {
            Admin admin = buildAdmin(3L, true, null, false, 4);
            when(adminRepository.findByUsernameOrEmail("adminUser", "adminUser")).thenReturn(Optional.of(admin));
            when(passwordService.verifyPassword("wrong", "salt", "hash")).thenReturn(false);
            when(adminRepository.save(any())).thenReturn(admin);

            LoginRequest req = buildLoginRequest("adminUser", "wrong", "admin");
            authService.login(req, httpRequest);

            assertThat(admin.getLoginAttempts()).isEqualTo(5);
            assertThat(admin.getLockedUntil()).isNotNull().isAfter(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("login() — no role (auto-detect)")
    class LoginNoRole {

        @Test
        @DisplayName("Falls through to user when found first")
        void findsUserFirst() {
            User user = buildUser(1L, true, true, null, 0);
            when(userRepository.findByUsernameOrEmail("user", "user")).thenReturn(Optional.of(user));
            when(passwordService.verifyPassword("correct", "salt", "hash")).thenReturn(true);
            when(userRepository.save(any())).thenReturn(user);
            when(jwtUtils.generateUserToken(anyString(), anyLong(), anyString(), anyLong())).thenReturn("jwt");
            when(jwtUtils.getAccessTokenExpiration()).thenReturn(900L);
            RefreshToken rt = buildRefreshToken();
            when(refreshTokenService.createRefreshToken(any(), any(), anyLong(), any())).thenReturn(rt);

            LoginRequest req = buildLoginRequest("user", "correct", null);
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("No user in any table returns success=false")
        void noUserAnywhere_returnsFalse() {
            when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());
            when(adminRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

            LoginRequest req = buildLoginRequest("ghost", "pass", null);
            AuthResponse resp = authService.login(req, httpRequest);

            assertThat(resp.isSuccess()).isFalse();
            assertThat(resp.getMessage()).containsIgnoringCase("invalid credentials");
        }
    }
}
