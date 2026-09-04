package dev.modularforge.auth;

import dev.modularforge.shared.audit.UserActivityAudit;
import dev.modularforge.auth.dto.AuthResponse;
import dev.modularforge.auth.dto.ForgotPasswordRequest;
import dev.modularforge.auth.dto.LoginRequest;
import dev.modularforge.auth.dto.RegisterRequest;
import dev.modularforge.auth.dto.ResendVerificationRequest;
import dev.modularforge.auth.dto.ResetPasswordRequest;
import dev.modularforge.auth.dto.UserSessionDTO;
import dev.modularforge.auth.dto.VerifyPasswordRequest;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.identity.model.Role;
import dev.modularforge.shared.notification.NotificationGateway;
import dev.modularforge.ratelimit.RateLimitService;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ErrorResponse;
import dev.modularforge.shared.error.ResourceNotFoundException;

import dev.modularforge.auth.dto.*;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.auth.token.PasswordResetToken;
import dev.modularforge.auth.token.VerificationToken;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.token.PasswordResetTokenRepository;
import dev.modularforge.auth.token.VerificationTokenRepository;
import dev.modularforge.security.JwtUtils;

import dev.modularforge.auth.token.RefreshToken;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AuthService {
    private static final String DUMMY_SALT = "bW9kdWxpdGhGb3JnZUR1bW15U2FsdA==";
    private static final String DUMMY_HASH = "$argon2id$v=19$m=65536,t=3,p=4$INTQ2i0lYlIO7x6L6j5rb1oEwMtyN1ocQG3GX0f64QI$gejhj2ZNYoMmixSYgJi4txkuutI8m7yVqxUL1Kvjcjh9mAODDxw4NB4sLER+poxiIeyFyJIKl6+CATMOrp1llQ";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private NotificationGateway emailService;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private UserActivityAudit userActivityLogger;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private TokenHashService tokenHashService;

    @Autowired(required = false)
    private SecondFactorGateway secondFactorGateway;
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            if (userRepository.existsByUsername(request.getUsername()) ||
                    adminRepository.existsByUsername(request.getUsername())) {
                return errorResponse("Username already exists");
            }

            if (userRepository.existsByEmail(request.getEmail()) ||
                    adminRepository.existsByEmail(request.getEmail())) {
                return errorResponse("Email already exists");
            }

            String salt = passwordService.generateSalt();
            String hashedPassword = passwordService.hashPassword(request.getPassword(), salt);

            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPasswordHash(hashedPassword);
            user.setSalt(salt);
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setPhone(request.getPhone());
            user.setBio(request.getBio());
            user.setUserType(UserType.APP_USER); // fixed default — userType is not accepted from the client
            user.setIsActive(true);
            user.setEmailVerified(false);

            user = userRepository.save(user);

            VerificationToken verificationToken = createVerificationToken(user.getId(), "user");
            verificationTokenRepository.save(verificationToken);

            emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken(),
                    user.getFirstName() != null ? user.getFirstName() : user.getUsername());

            userActivityLogger.logRegister(user.getId(), "user", true, null, httpRequest);

            return AuthResponse.builder()
                    .success(true)
                    .message(
                            "Registered successfully. Please check your email to verify your account before logging in.")
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .profilePicture(user.getProfilePicture())
                            .isActive(user.getIsActive())
                            .emailVerified(user.getEmailVerified())
                            .role("user")
                            .userType(user.getUserType().name().toLowerCase(Locale.ROOT))
                            .lastLoginAt(user.getLastLoginAt())
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Registration failed for user: {}", request.getUsername(), e);
            return errorResponse("Registration failed. Please try again.");
        }
    }
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            String requestedRole = request.getRole() != null ? request.getRole().toLowerCase() : null;

            if ("user".equals(requestedRole)) {
                Optional<User> userOpt = userRepository.findByUsernameOrEmail(
                        request.getUsername(), request.getUsername());
                if (userOpt.isPresent()) {
                    return authenticateUser(userOpt.get(), request.getPassword(), httpRequest);
                }
                passwordService.verifyPassword(request.getPassword(), DUMMY_SALT, DUMMY_HASH);
                return errorResponse("Invalid credentials");

            } else if ("admin".equals(requestedRole)) {
                Optional<Admin> adminOpt = adminRepository.findByUsernameOrEmail(
                        request.getUsername(), request.getUsername());
                if (adminOpt.isPresent()) {
                    return authenticateAdmin(adminOpt.get(), request.getPassword(), httpRequest);
                }
                passwordService.verifyPassword(request.getPassword(), DUMMY_SALT, DUMMY_HASH);
                return errorResponse("Invalid credentials");

            } else {
                Optional<User> userOpt = userRepository.findByUsernameOrEmail(
                        request.getUsername(), request.getUsername());
                if (userOpt.isPresent()) {
                    return authenticateUser(userOpt.get(), request.getPassword(), httpRequest);
                }
                Optional<Admin> adminOpt = adminRepository.findByUsernameOrEmail(
                        request.getUsername(), request.getUsername());
                if (adminOpt.isPresent()) {
                    return authenticateAdmin(adminOpt.get(), request.getPassword(), httpRequest);
                }
                passwordService.verifyPassword(request.getPassword(), DUMMY_SALT, DUMMY_HASH);
                return errorResponse("Invalid credentials");
            }

        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            return errorResponse("Login failed. Please try again.");
        }
    }

    private AuthResponse authenticateUser(User user, String password, HttpServletRequest httpRequest) {
        boolean reactivated = false;
        if (!user.getIsActive()) {
            if (Boolean.TRUE.equals(user.getAdminDeactivated())) {
                return errorResponse("Account has been suspended. Please contact support.");
            }
            boolean withinGracePeriod = user.getDeactivatedAt() != null
                    && user.getDeactivatedAt().isAfter(LocalDateTime.now().minusDays(30));
            if (!withinGracePeriod) {
                return errorResponse("Account is deactivated");
            }
            if (!passwordService.verifyPassword(password, user.getSalt(), user.getPasswordHash())) {
                return errorResponse("Invalid credentials");
            }
            user.setIsActive(true);
            user.setDeactivatedAt(null);
            user.setLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            log.info("Account auto-reactivated for userId={} within grace period", user.getId());
            reactivated = true;
        }

        if (!user.getEmailVerified()) {
            return errorResponse("Email must be verified before login. Please check your email for verification link.");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return errorResponse("Account is temporarily locked. Please try again later.");
        }
        boolean valid = reactivated || passwordService.verifyPassword(password, user.getSalt(), user.getPasswordHash());

        if (!valid) {
            user.setLoginAttempts(user.getLoginAttempts() + 1);
            if (user.getLoginAttempts() >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                user.invalidateAccessTokens();
            }
            userRepository.save(user);
            userActivityLogger.logLoginFailure(user.getId(), "user", "Invalid password", httpRequest);
            return errorResponse("Invalid credentials");
        }

        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        userActivityLogger.logLoginSuccess(user.getId(), "user", httpRequest);

        String accessToken = jwtUtils.generateUserToken(user.getUsername(), user.getId(),
                user.getUserType().name().toLowerCase(Locale.ROOT), user.currentAuthVersion());
        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(user.getId(), "user", httpRequest);

        return AuthResponse.builder()
                .success(true)
                .message(reactivated ? "Account reactivated. Login successful." : "Login successful")
                .accessToken(accessToken)
                .refreshToken(refreshTokenEntity.getToken())
                .expiresIn(jwtUtils.getAccessTokenExpiration())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .profilePicture(user.getProfilePicture())
                        .isActive(user.getIsActive())
                        .emailVerified(user.getEmailVerified())
                        .role("user")
                        .userType(user.getUserType().name().toLowerCase(Locale.ROOT))
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .build();
    }

    private AuthResponse authenticateAdmin(Admin admin, String password, HttpServletRequest httpRequest) {
        if (!admin.getIsActive())
            return errorResponse("Admin account is deactivated");

        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(LocalDateTime.now())) {
            return errorResponse("Admin account is temporarily locked. Please try again later.");
        }

        boolean valid = passwordService.verifyPassword(password, admin.getSalt(), admin.getPasswordHash());

        if (!valid) {
            admin.setLoginAttempts(admin.getLoginAttempts() + 1);
            if (admin.getLoginAttempts() >= 5) {
                admin.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                admin.invalidateAccessTokens();
            }
            adminRepository.save(admin);
            return errorResponse("Invalid credentials");
        }

        admin.setLoginAttempts(0);
        admin.setLockedUntil(null);

        Optional<SecondFactorGateway.Challenge> secondFactorChallenge = secondFactorGateway == null
                ? Optional.empty()
                : secondFactorGateway.beginChallenge(admin.getId());
        if (secondFactorChallenge.isPresent()) {
            SecondFactorGateway.Challenge challenge = secondFactorChallenge.get();
            return AuthResponse.builder()
                    .success(false)
                    .requiresTwoFactor(true)
                    .twoFactorChallengeToken(challenge.token())
                    .message(challenge.message())
                    .user(AuthResponse.UserInfo.builder()
                            .username(admin.getUsername())
                            .role("admin")
                            .build())
                    .build();
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);

        String accessToken = jwtUtils.generateAdminToken(
                admin.getUsername(), admin.getId(), admin.getLevel(), admin.currentAuthVersion());
        RefreshToken refreshTokenEntity = refreshTokenService.createRefreshToken(admin.getId(), "admin", httpRequest);

        return AuthResponse.builder()
                .success(true)
                .message("Admin login successful")
                .accessToken(accessToken)
                .refreshToken(refreshTokenEntity.getToken())
                .expiresIn(jwtUtils.getAccessTokenExpiration())
                .user(AuthResponse.UserInfo.builder()
                        .id(admin.getId())
                        .username(admin.getUsername())
                        .email(admin.getEmail())
                        .firstName(admin.getFirstName())
                        .lastName(admin.getLastName())
                        .profilePicture(admin.getProfilePicture())
                        .isActive(admin.getIsActive())
                        .emailVerified(true)
                        .role("admin")
                        .level(admin.getLevel())
                        .lastLoginAt(admin.getLastLoginAt())
                        .build())
                .build();
    }

    public boolean verifyPassword(VerifyPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            String requestedRole = request.getRole() != null ? request.getRole().toLowerCase() : null;

            if ("user".equals(requestedRole)) {
                return userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                        .map(u -> passwordService.verifyPassword(request.getPassword(), u.getSalt(),
                                u.getPasswordHash()))
                        .orElse(false);

            } else if ("admin".equals(requestedRole)) {
                return adminRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                        .map(a -> passwordService.verifyPassword(request.getPassword(), a.getSalt(),
                                a.getPasswordHash()))
                        .orElse(false);

            } else {
                Optional<User> userOpt = userRepository.findByUsernameOrEmail(
                        request.getUsername(), request.getUsername());
                if (userOpt.isPresent()) {
                    return passwordService.verifyPassword(request.getPassword(),
                            userOpt.get().getSalt(), userOpt.get().getPasswordHash());
                }
                return adminRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                        .map(a -> passwordService.verifyPassword(request.getPassword(), a.getSalt(),
                                a.getPasswordHash()))
                        .orElse(false);
            }

        } catch (Exception e) {
            log.error("Password verification failed for user: {}", request.getUsername(), e);
            return false;
        }
    }

    @Transactional
    public boolean forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            String clientIp = getClientIpAddress(httpRequest);
            String requestedRole = request.getRole() != null ? request.getRole().toLowerCase() : null;

            if ("user".equals(requestedRole)) {
                userRepository.findByEmail(request.getEmail())
                        .ifPresent(u -> createPasswordResetToken(u.getId(), "user", request.getEmail(), clientIp));

            } else if ("admin".equals(requestedRole)) {
                adminRepository.findByEmail(request.getEmail())
                        .ifPresent(a -> createPasswordResetToken(a.getId(), "admin", request.getEmail(), clientIp));

            } else {
                Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
                if (userOpt.isPresent()) {
                    createPasswordResetToken(userOpt.get().getId(), "user", request.getEmail(), clientIp);
                } else {
                    adminRepository.findByEmail(request.getEmail())
                            .ifPresent(a -> createPasswordResetToken(a.getId(), "admin", request.getEmail(), clientIp));
                }
            }

            return true; // Always return true for security

        } catch (Exception e) {
            log.error("Forgot password failed for email: {}", request.getEmail(), e);
            return false;
        }
    }

    private void createPasswordResetToken(Long userId, String role, String email, String clientIp) {
        passwordResetTokenRepository.deleteByUserIdAndRole(userId, role);

        PasswordResetToken resetToken = createPasswordResetTokenEntity(userId, role, clientIp);
        passwordResetTokenRepository.save(resetToken);

        if ("user".equals(role)) {
            userActivityLogger.logPasswordResetRequest(userId, role, null);
        }

        emailService.sendPasswordResetEmail(email, resetToken.getToken(), resolveDisplayName(email, role));
    }

    @Transactional
    public boolean resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        try {
            Optional<PasswordResetToken> tokenOpt = findPasswordResetToken(request.getToken());

            if (tokenOpt.isEmpty())
                return false;

            PasswordResetToken resetToken = tokenOpt.get();

            if (resetToken.isExpired()) {
                passwordResetTokenRepository.delete(resetToken);
                return false;
            }

            if (resetToken.hasTooManyAttempts()) {
                passwordResetTokenRepository.delete(resetToken);
                return false;
            }

            String salt = passwordService.generateSalt();
            String hashedPassword = passwordService.hashPassword(request.getNewPassword(), salt);

            if ("user".equals(resetToken.getRole())) {
                userRepository.findById(resetToken.getUserId()).ifPresent(u -> {
                    u.setPasswordHash(hashedPassword);
                    u.setSalt(salt);
                    u.setLoginAttempts(0);
                    u.setLockedUntil(null);
                    u.invalidateAccessTokens();
                    userRepository.save(u);
                    refreshTokenService.revokeAllUserTokens(u.getId(), "user");
                    userActivityLogger.logPasswordResetComplete(u.getId(), "user", true, httpRequest);
                });

            } else if ("admin".equals(resetToken.getRole())) {
                adminRepository.findById(resetToken.getUserId()).ifPresent(a -> {
                    a.setPasswordHash(hashedPassword);
                    a.setSalt(salt);
                    a.setLoginAttempts(0);
                    a.setLockedUntil(null);
                    a.invalidateAccessTokens();
                    adminRepository.save(a);
                    refreshTokenService.revokeAllUserTokens(a.getId(), "admin");
                });
            }

            passwordResetTokenRepository.delete(resetToken);
            return true;

        } catch (Exception e) {
            log.error("Password reset failed for tokenPreview: {}", tokenHashService.preview(request.getToken()), e);
            return false;
        }
    }

    @Transactional
    public boolean verifyEmail(String token) {
        try {
            Optional<VerificationToken> tokenOpt = findVerificationToken(token);

            if (tokenOpt.isEmpty())
                return false;

            VerificationToken verificationToken = tokenOpt.get();

            if (verificationToken.isExpired()) {
                verificationTokenRepository.delete(verificationToken);
                return false;
            }

            if ("user".equals(verificationToken.getRole())) {
                userRepository.findById(verificationToken.getUserId()).ifPresent(u -> {
                    u.setEmailVerified(true);
                    userRepository.save(u);
                    userActivityLogger.logEmailVerification(u.getId(), "user", true, null);
                });
            }

            verificationTokenRepository.delete(verificationToken);
            return true;

        } catch (Exception e) {
            log.error("Email verification failed for tokenPreview: {}", tokenHashService.preview(token), e);
            return false;
        }
    }

    @Transactional
    public boolean resendVerificationEmail(ResendVerificationRequest request) {
        try {
            String email = request.getEmail();

            if (rateLimitService.isEmailVerificationRateLimitExceeded(email)) {
                log.warn("Rate limit exceeded for verification email resend: {}", email);
                return true;
            }

            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isPresent()) {
                User user = userOpt.get();

                if (user.getEmailVerified()) {
                    log.info("Verification resend requested for already verified email: {}", email);
                    return true;
                }

                verificationTokenRepository.deleteByUserIdAndRole(user.getId(), "user");

                VerificationToken verificationToken = createVerificationToken(user.getId(), "user");
                verificationTokenRepository.save(verificationToken);

                emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken(),
                        user.getFirstName() != null ? user.getFirstName() : user.getUsername());

                log.info("Verification email resent to: {}", email);
                return true;
            }

            log.warn("Verification resend requested for non-existent email: {}", email);
            return true;

        } catch (Exception e) {
            log.error("Resend verification email failed for email: {}", request.getEmail(), e);
            return false;
        }
    }

    public UserSessionDTO getCurrentUserSession(String token) {
        try {
            if (!jwtUtils.validateToken(token))
                return null;

            Long userId = jwtUtils.extractUserIdAsLong(token);
            String role = jwtUtils.extractRole(token);

            if (userId == null || role == null)
                return null;

            switch (role.toLowerCase(Locale.ROOT)) {
                case "user":
                    return userRepository.findById(userId)
                            .map(u -> UserSessionDTO.builder()
                                    .id(u.getId())
                                    .firstName(u.getFirstName())
                                    .lastName(u.getLastName())
                                    .profilePicture(u.getProfilePicture())
                                    .role("USER")
                                    .userType(u.getUserType().name().toLowerCase(Locale.ROOT))
                                    .build())
                            .orElse(null);

                case "admin":
                    return adminRepository.findById(userId)
                            .map(a -> UserSessionDTO.builder()
                                    .id(a.getId())
                                    .firstName(a.getFirstName())
                                    .lastName(a.getLastName())
                                    .profilePicture(a.getProfilePicture())
                                    .role("ADMIN")
                                    .adminLevel(a.getLevel())
                                    .build())
                            .orElse(null);

                default:
                    return null;
            }
        } catch (Exception e) {
            log.error("Error getting user session from token", e);
            return null;
        }
    }
    @Transactional
    public Map<String, Object> verifyEmailChange(String token) {
        VerificationToken vt = findVerificationToken(token)
                .orElseThrow(() -> new dev.modularforge.shared.error.BadRequestException(
                        "Invalid or expired email change token"));

        if (!"email_change".equals(vt.getRole())) {
            throw new dev.modularforge.shared.error.BadRequestException("Invalid token type");
        }

        if (vt.isExpired()) {
            verificationTokenRepository.delete(vt);
            throw new dev.modularforge.shared.error.BadRequestException(
                    "Email change token has expired. Please request a new one.");
        }

        User user = userRepository.findById(vt.getUserId())
                .orElseThrow(() -> new dev.modularforge.shared.error.ResourceNotFoundException(
                        "User not found with ID: " + vt.getUserId()));

        if (user.getPendingEmail() == null || user.getPendingEmail().isBlank()) {
            verificationTokenRepository.delete(vt);
            throw new dev.modularforge.shared.error.BadRequestException(
                    "No pending email change found for this account");
        }

        String newEmail = user.getPendingEmail();
        if (userRepository.existsByEmail(newEmail) || adminRepository.existsByEmail(newEmail)) {
            user.setPendingEmail(null);
            userRepository.save(user);
            verificationTokenRepository.delete(vt);
            throw new dev.modularforge.shared.error.BadRequestException("This email address is already in use");
        }

        user.setEmail(newEmail);
        user.setPendingEmail(null);
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationTokenRepository.delete(vt);
        log.info("Email change verified for userId={} → new email={}", user.getId(), newEmail);

        return Map.of("success", true, "message", "Email address updated successfully.");
    }

    private AuthResponse errorResponse(String message) {
        return AuthResponse.builder().success(false).message(message).build();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String resolveDisplayName(String email, String role) {
        if ("user".equals(role)) {
            return userRepository.findByEmail(email)
                    .map(u -> u.getFirstName() != null ? u.getFirstName() : u.getUsername())
                    .orElse("User");
        } else if ("admin".equals(role)) {
            return adminRepository.findByEmail(email)
                    .map(a -> a.getFirstName() != null ? a.getFirstName() : a.getUsername())
                    .orElse("Admin");
        }
        return "User";
    }

    private PasswordResetToken createPasswordResetTokenEntity(Long userId, String role, String clientIp) {
        String rawToken = tokenHashService.generateToken();
        PasswordResetToken resetToken = new PasswordResetToken(userId, role, clientIp);
        resetToken.storeTokenMetadata(rawToken, tokenHashService.hashToken(rawToken), tokenHashService.preview(rawToken));
        return resetToken;
    }

    private VerificationToken createVerificationToken(Long userId, String role) {
        String rawToken = tokenHashService.generateToken();
        VerificationToken verificationToken = new VerificationToken(userId, role);
        verificationToken.storeTokenMetadata(
                rawToken,
                tokenHashService.hashToken(rawToken),
                tokenHashService.preview(rawToken)
        );
        return verificationToken;
    }

    private Optional<PasswordResetToken> findPasswordResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Optional<PasswordResetToken> hashedToken = Optional.ofNullable(
                passwordResetTokenRepository.findByTokenHash(tokenHashService.hashToken(token))
        ).orElse(Optional.empty());
        return hashedToken.isPresent()
                ? hashedToken
                : passwordResetTokenRepository.findByToken(token).filter(t -> t.getTokenHash() == null);
    }

    private Optional<VerificationToken> findVerificationToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        Optional<VerificationToken> hashedToken = Optional.ofNullable(
                verificationTokenRepository.findByTokenHash(tokenHashService.hashToken(token))
        ).orElse(Optional.empty());
        return hashedToken.isPresent()
                ? hashedToken
                : verificationTokenRepository.findByToken(token).filter(t -> t.getTokenHash() == null);
    }
}
