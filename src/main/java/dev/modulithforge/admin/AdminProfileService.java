package dev.modulithforge.admin;

import dev.modulithforge.auth.PasswordService;
import dev.modulithforge.auth.token.RefreshTokenService;

import dev.modulithforge.admin.dto.AdminProfileDTO;
import dev.modulithforge.admin.dto.ChangePasswordRequest;
import dev.modulithforge.admin.dto.UpdateAdminProfileRequest;
import dev.modulithforge.shared.error.BadRequestException;
import dev.modulithforge.shared.error.ResourceNotFoundException;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.identity.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.modules.admin-management", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AdminProfileService {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private RefreshTokenService refreshTokenService;
    public AdminProfileDTO getAdminProfile(Long adminId) {
        log.info("Fetching profile for admin ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));

        return mapToDTO(admin);
    }
    @Transactional
    public AdminProfileDTO updateAdminProfile(Long adminId, UpdateAdminProfileRequest request) {
        log.info("Updating profile for admin ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        if (request.getEmail() != null && !request.getEmail().equals(admin.getEmail())) {
            if (adminRepository.existsByEmail(request.getEmail()) ||
                userRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            admin.setEmail(request.getEmail());
            log.info("Email changed for admin ID: {}", adminId);
        }
        if (request.getFirstName() != null) {
            admin.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            admin.setLastName(request.getLastName());
        }

        if (request.getProfilePicture() != null) {
            admin.setProfilePicture(request.getProfilePicture());
        }

        admin = adminRepository.save(admin);
        log.info("Profile updated successfully for admin ID: {}", adminId);

        return mapToDTO(admin);
    }
    @Transactional
    public AdminProfileDTO updateProfilePicture(Long adminId, String imageUrl) {
        log.info("Updating profile picture for admin ID: {}", adminId);

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));

        admin.setProfilePicture(imageUrl);
        admin = adminRepository.save(admin);

        log.info("Profile picture updated successfully for admin ID: {}", adminId);
        return mapToDTO(admin);
    }
    @Transactional
    public void changePassword(Long adminId, ChangePasswordRequest request) {
        log.info("Changing password for admin ID: {}", adminId);
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));
        boolean isValidPassword = passwordService.verifyPassword(
            request.getCurrentPassword(), admin.getSalt(), admin.getPasswordHash());

        if (!isValidPassword) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }
        String newSalt = passwordService.generateSalt();
        String newHashedPassword = passwordService.hashPassword(request.getNewPassword(), newSalt);

        admin.setSalt(newSalt);
        admin.setPasswordHash(newHashedPassword);
        admin.invalidateAccessTokens();

        adminRepository.save(admin);
        int revoked = refreshTokenService.revokeAllUserTokens(adminId, "admin");
        log.info("Password changed successfully for admin ID: {}; {} refresh tokens revoked", adminId, revoked);
    }
    @Transactional
    public void deactivateAccount(Long adminId, Long requestingAdminId) {
        log.info("Deactivating account for admin ID: {} by admin ID: {}", adminId, requestingAdminId);

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found with ID: " + requestingAdminId));
        if (requestingAdmin.getLevel() > admin.getLevel() && requestingAdmin.getLevel() != 0) {
            throw new BadRequestException("You don't have permission to deactivate this admin account");
        }
        if (admin.getLevel() == 0) {
            throw new BadRequestException("Cannot deactivate super admin account");
        }

        admin.setIsActive(false);
        admin.invalidateAccessTokens();
        adminRepository.save(admin);
        int revoked = refreshTokenService.revokeAllUserTokens(adminId, "admin");

        log.info("Account deactivated successfully for admin ID: {}; {} refresh tokens revoked", adminId, revoked);
    }
    @Transactional
    public void reactivateAccount(Long adminId, Long requestingAdminId) {
        log.info("Reactivating account for admin ID: {} by admin ID: {}", adminId, requestingAdminId);

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found with ID: " + requestingAdminId));
        if (requestingAdmin.getLevel() > admin.getLevel() && requestingAdmin.getLevel() != 0) {
            throw new BadRequestException("You don't have permission to reactivate this admin account");
        }

        admin.setIsActive(true);
        adminRepository.save(admin);

        log.info("Account reactivated successfully for admin ID: {}", adminId);
    }
    public boolean verifyProfileImageOwnership(Long adminId, String imageUrl) {
        log.debug("Verifying profile image ownership for admin ID: {} and URL: {}", adminId, imageUrl);

        if (imageUrl == null || imageUrl.isEmpty()) {
            return false;
        }

        Admin admin = adminRepository.findById(adminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + adminId));

        String adminProfilePicture = admin.getProfilePicture();

        if (adminProfilePicture == null || adminProfilePicture.isEmpty()) {
            log.warn("Admin {} attempted to delete image but has no profile picture set", adminId);
            return false;
        }

        boolean isOwner = adminProfilePicture.equals(imageUrl);

        if (!isOwner) {
            log.warn("Admin {} attempted to delete image that doesn't belong to them: {}", adminId, imageUrl);
        }

        return isOwner;
    }
    private AdminProfileDTO mapToDTO(Admin admin) {
        return AdminProfileDTO.builder()
            .id(admin.getId())
            .username(admin.getUsername())
            .email(admin.getEmail())
            .firstName(admin.getFirstName())
            .lastName(admin.getLastName())
            .profilePicture(admin.getProfilePicture())
            .level(admin.getLevel())
            .permissions(admin.getPermissions())
            .isActive(admin.getIsActive())
            .createdBy(admin.getCreatedBy())
            .createdAt(admin.getCreatedAt())
            .updatedAt(admin.getUpdatedAt())
            .lastLoginAt(admin.getLastLoginAt())
            .build();
    }
}
