package dev.modulithforge.profile;

import dev.modulithforge.auth.AuthService;
import dev.modulithforge.auth.PasswordService;
import dev.modulithforge.auth.token.RefreshTokenService;
import dev.modulithforge.auth.token.TokenHashService;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.UserType;
import dev.modulithforge.shared.notification.NotificationGateway;
import dev.modulithforge.admin.dto.ChangePasswordRequest;
import dev.modulithforge.profile.dto.ChangeEmailRequest;
import dev.modulithforge.profile.dto.DeactivateAccountRequest;
import dev.modulithforge.profile.dto.UpdateUserProfileRequest;
import dev.modulithforge.profile.dto.UserProfileDTO;
import dev.modulithforge.shared.error.BadRequestException;
import dev.modulithforge.shared.error.ResourceNotFoundException;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.auth.token.VerificationToken;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.identity.UserRepository;
import dev.modulithforge.auth.token.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@ConditionalOnProperty(prefix = "app.modules.user-profile", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final NotificationGateway emailService;
    private final TokenHashService tokenHashService;
    public UserProfileDTO getProfile(Long userId) {
        User user = findActiveUserById(userId);
        return mapToDTO(user);
    }
    @Transactional
    public UserProfileDTO updateProfile(Long userId, UpdateUserProfileRequest request) {
        User user = findActiveUserById(userId);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail()) ||
                    adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null)
            user.setFirstName(request.getFirstName());
        if (request.getLastName() != null)
            user.setLastName(request.getLastName());
        if (request.getPhone() != null)
            user.setPhone(request.getPhone());
        if (request.getBio() != null)
            user.setBio(request.getBio());

        user = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);
        return mapToDTO(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        User user = findActiveUserById(userId);

        if (!passwordService.verifyPassword(request.getCurrentPassword(), user.getSalt(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        String newSalt = passwordService.generateSalt();
        String newHash = passwordService.hashPassword(request.getNewPassword(), newSalt);
        user.setSalt(newSalt);
        user.setPasswordHash(newHash);
        user.invalidateAccessTokens();

        userRepository.save(user);
        int revoked = refreshTokenService.revokeAllUserTokens(userId, "user");
        log.info("Password changed for userId={}; {} refresh tokens revoked", userId, revoked);
    }
    @Transactional
    public void deactivateAccount(Long userId, DeactivateAccountRequest request) {
        User user = findActiveUserById(userId);

        if (!passwordService.verifyPassword(request.getPassword(), user.getSalt(), user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect");
        }

        user.setIsActive(false);
        user.setDeactivatedAt(LocalDateTime.now());
        user.invalidateAccessTokens();
        userRepository.save(user);
        int revoked = refreshTokenService.revokeAllUserTokens(userId, "user");
        log.info("Account deactivated for userId={}; {} refresh tokens revoked", userId, revoked);
    }
    @Transactional
    public void requestEmailChange(Long userId, ChangeEmailRequest request) {
        User user = findActiveUserById(userId);

        if (!passwordService.verifyPassword(request.getCurrentPassword(), user.getSalt(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        String newEmail = request.getNewEmail().trim().toLowerCase(Locale.ROOT);

        if (newEmail.equals(user.getEmail().toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("New email must be different from the current email");
        }

        if (userRepository.existsByEmail(newEmail) || adminRepository.existsByEmail(newEmail)) {
            throw new BadRequestException("This email address is already in use");
        }
        user.setPendingEmail(newEmail);
        userRepository.save(user);
        verificationTokenRepository.deleteByUserIdAndRole(userId, "email_change");

        VerificationToken token = createVerificationToken(userId, "email_change");
        verificationTokenRepository.save(token);

        String displayName = user.getFirstName() != null ? user.getFirstName() : user.getUsername();
        emailService.sendEmailChangeVerificationEmail(newEmail, token.getToken(), displayName);

        log.info("Email change requested for userId={} → pendingEmail={}", userId, newEmail);
    }
    public String getProfilePictureUrl(Long userId) {
        return findActiveUserById(userId).getProfilePicture();
    }
    @Transactional
    public UserProfileDTO updateProfilePicture(Long userId, String imageUrl) {
        User user = findActiveUserById(userId);
        user.setProfilePicture(imageUrl);
        user = userRepository.save(user);
        log.info("Profile picture updated for userId={}", userId);
        return mapToDTO(user);
    }

    private User findActiveUserById(Long userId) {
        return userRepository.findById(userId)
                .filter(User::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    public UserProfileDTO mapToDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePicture(user.getProfilePicture())
                .phone(user.getPhone())
                .bio(user.getBio())
                .userType(user.getUserType() != null
                        ? user.getUserType().name().toLowerCase(Locale.ROOT)
                        : null)
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private VerificationToken createVerificationToken(Long userId, String role) {
        String rawToken = tokenHashService.generateToken();
        VerificationToken token = new VerificationToken(userId, role);
        token.storeTokenMetadata(rawToken, tokenHashService.hashToken(rawToken), tokenHashService.preview(rawToken));
        return token;
    }
}
