package dev.modularforge.profile;

import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.shared.dto.ChangePasswordRequest;
import dev.modularforge.shared.dto.ChangeEmailRequest;
import dev.modularforge.profile.dto.DeactivateAccountRequest;
import dev.modularforge.profile.dto.UpdateUserProfileRequest;
import dev.modularforge.profile.dto.UserProfileDTO;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.UserRepository;
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
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;

    private final dev.modularforge.auth.EmailChangeService emailChanges;
    public UserProfileDTO getProfile(Long userId) {
        User user = findActiveUserById(userId);
        return mapToDTO(user);
    }
    @Transactional
    public UserProfileDTO updateProfile(Long userId, UpdateUserProfileRequest request) {
        User user = findActiveUserById(userId);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            throw new BadRequestException("Use the verified email change endpoint to change your email");
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
        emailChanges.request(userId, "user", request.getCurrentPassword(), request.getNewEmail());
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

}
