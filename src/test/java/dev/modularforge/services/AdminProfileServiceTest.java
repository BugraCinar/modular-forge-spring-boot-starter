package dev.modularforge.services;

import dev.modularforge.admin.AdminProfileService;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;

import dev.modularforge.admin.dto.AdminProfileDTO;
import dev.modularforge.admin.dto.ChangePasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminProfileRequest;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProfileServiceTest {

    @InjectMocks
    private AdminProfileService adminProfileService;

    @Mock private AdminRepository adminRepository;
    @Mock private PasswordService passwordService;
    @Mock private RefreshTokenService refreshTokenService;

    private Admin makeAdmin(Long id, Integer level) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setLevel(level);
        admin.setUsername("admin" + id);
        admin.setEmail("admin" + id + "@test.com");
        admin.setFirstName("First" + id);
        admin.setLastName("Last" + id);
        admin.setIsActive(true);
        admin.setSalt("salt");
        admin.setPasswordHash("hash");
        return admin;
    }

    private ChangePasswordRequest makeChangePasswordRequest(String current, String newPwd, String confirm) {
        return new ChangePasswordRequest(current, newPwd, confirm);
    }

    @Test
    @DisplayName("getAdminProfile - admin not found throws ResourceNotFoundException")
    void getAdminProfile_notFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.getAdminProfile(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getAdminProfile - found returns populated DTO")
    void getAdminProfile_found_returnsDTO() {
        Admin admin = makeAdmin(1L, 0);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminProfileDTO result = adminProfileService.getAdminProfile(1L);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("admin1");
        assertThat(result.getEmail()).isEqualTo("admin1@test.com");
        assertThat(result.getLevel()).isEqualTo(0);
    }

    @Test
    @DisplayName("updateAdminProfile - admin not found throws ResourceNotFoundException")
    void updateAdminProfile_notFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.updateAdminProfile(1L, new UpdateAdminProfileRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateAdminProfile - duplicate email throws BadRequestException")
    void updateAdminProfile_duplicateEmail_badRequest() {
        Admin admin = makeAdmin(1L, 1);
        UpdateAdminProfileRequest req = new UpdateAdminProfileRequest();
        req.setEmail("taken@test.com");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> adminProfileService.updateAdminProfile(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email");
    }

    @Test
    @DisplayName("updateAdminProfile - same email is NOT treated as a change (no uniqueness check)")
    void updateAdminProfile_sameEmail_noUniqueCheck() {
        Admin admin = makeAdmin(1L, 1);
        UpdateAdminProfileRequest req = new UpdateAdminProfileRequest();
        req.setEmail("admin1@test.com"); // same as existing
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.save(any(Admin.class))).thenReturn(admin);

        adminProfileService.updateAdminProfile(1L, req);

        verify(adminRepository, never()).existsByEmail(anyString());
        verify(adminRepository).save(admin);
    }

    @Test
    @DisplayName("updateAdminProfile - valid update applies field changes and saves")
    void updateAdminProfile_valid_appliesChanges() {
        Admin admin = makeAdmin(1L, 1);
        UpdateAdminProfileRequest req = new UpdateAdminProfileRequest();
        req.setFirstName("Updated");
        req.setLastName("Name");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.save(any(Admin.class))).thenReturn(admin);

        AdminProfileDTO result = adminProfileService.updateAdminProfile(1L, req);

        assertThat(result).isNotNull();
        assertThat(admin.getFirstName()).isEqualTo("Updated");
        assertThat(admin.getLastName()).isEqualTo("Name");
        verify(adminRepository).save(admin);
    }

    @Test
    @DisplayName("updateProfilePicture - admin not found throws ResourceNotFoundException")
    void updateProfilePicture_notFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.updateProfilePicture(1L, "https://img.example.com/pic.jpg"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateProfilePicture - valid update sets profile picture URL and saves")
    void updateProfilePicture_valid_setsUrl() {
        Admin admin = makeAdmin(1L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminRepository.save(any(Admin.class))).thenReturn(admin);

        adminProfileService.updateProfilePicture(1L, "https://img.example.com/pic.jpg");

        assertThat(admin.getProfilePicture()).isEqualTo("https://img.example.com/pic.jpg");
        verify(adminRepository).save(admin);
    }

    @Test
    @DisplayName("changePassword - confirm password mismatch throws BadRequestException before DB call")
    void changePassword_confirmMismatch_throwsWithoutDbCall() {
        assertThatThrownBy(() -> adminProfileService.changePassword(1L,
                makeChangePasswordRequest("current", "newpass1", "newpass2")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");

        verify(adminRepository, never()).findById(any());
    }

    @Test
    @DisplayName("changePassword - admin not found throws ResourceNotFoundException")
    void changePassword_notFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.changePassword(1L,
                makeChangePasswordRequest("current", "newpass1", "newpass1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("changePassword - wrong current password throws BadRequestException")
    void changePassword_wrongCurrentPassword_throws() {
        Admin admin = makeAdmin(1L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordService.verifyPassword("wrongpass", "salt", "hash")).thenReturn(false);

        assertThatThrownBy(() -> adminProfileService.changePassword(1L,
                makeChangePasswordRequest("wrongpass", "newpass1", "newpass1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    @DisplayName("changePassword - new password same as current throws BadRequestException")
    void changePassword_newPasswordSameAsCurrent_throws() {
        Admin admin = makeAdmin(1L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordService.verifyPassword("samepass", "salt", "hash")).thenReturn(true);

        assertThatThrownBy(() -> adminProfileService.changePassword(1L,
                makeChangePasswordRequest("samepass", "samepass", "samepass")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different from current");
    }

    @Test
    @DisplayName("changePassword - valid change generates new salt, hashes, and saves")
    void changePassword_valid_savesNewCredentials() {
        Admin admin = makeAdmin(1L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordService.verifyPassword("oldpass", "salt", "hash")).thenReturn(true);
        when(passwordService.generateSalt()).thenReturn("newSalt");
        when(passwordService.hashPassword("NewPass1!", "newSalt")).thenReturn("newHash");

        adminProfileService.changePassword(1L, makeChangePasswordRequest("oldpass", "NewPass1!", "NewPass1!"));

        assertThat(admin.getSalt()).isEqualTo("newSalt");
        assertThat(admin.getPasswordHash()).isEqualTo("newHash");
        verify(adminRepository).save(admin);
    }

    @Test
    @DisplayName("deactivateAccount - admin not found throws ResourceNotFoundException")
    void deactivateAccount_adminNotFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.deactivateAccount(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deactivateAccount - requesting admin not found throws ResourceNotFoundException")
    void deactivateAccount_requestingAdminNotFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));
        when(adminRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProfileService.deactivateAccount(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deactivateAccount - level 2 deactivating level 1 admin throws BadRequestException")
    void deactivateAccount_level2DeactivatesLevel1_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));  // target
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 2)));  // requester

        assertThatThrownBy(() -> adminProfileService.deactivateAccount(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("deactivateAccount - cannot deactivate super admin (level 0) throws BadRequestException")
    void deactivateAccount_targetIsSuperAdmin_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));  // target
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 0)));  // requester

        assertThatThrownBy(() -> adminProfileService.deactivateAccount(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("super admin");
    }

    @Test
    @DisplayName("deactivateAccount - super admin deactivating level 1 sets isActive false")
    void deactivateAccount_valid_setsInactive() {
        Admin target = makeAdmin(1L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(target));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 0)));

        adminProfileService.deactivateAccount(1L, 2L);

        assertThat(target.getIsActive()).isFalse();
        verify(adminRepository).save(target);
        verify(refreshTokenService).revokeAllUserTokens(1L, "admin");
    }

    @Test
    @DisplayName("reactivateAccount - level 2 reactivating level 1 throws BadRequestException")
    void reactivateAccount_level2ReactivatesLevel1_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));  // target
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 2)));  // requester

        assertThatThrownBy(() -> adminProfileService.reactivateAccount(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("reactivateAccount - valid reactivation sets isActive true")
    void reactivateAccount_valid_setsActive() {
        Admin target = makeAdmin(1L, 1);
        target.setIsActive(false);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(target));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 0)));

        adminProfileService.reactivateAccount(1L, 2L);

        assertThat(target.getIsActive()).isTrue();
        verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("verifyProfileImageOwnership - null URL returns false without DB call")
    void verifyProfileImageOwnership_nullUrl_returnsFalse() {
        boolean result = adminProfileService.verifyProfileImageOwnership(1L, null);

        assertThat(result).isFalse();
        verify(adminRepository, never()).findById(any());
    }

    @Test
    @DisplayName("verifyProfileImageOwnership - empty URL returns false without DB call")
    void verifyProfileImageOwnership_emptyUrl_returnsFalse() {
        boolean result = adminProfileService.verifyProfileImageOwnership(1L, "");

        assertThat(result).isFalse();
        verify(adminRepository, never()).findById(any());
    }

    @Test
    @DisplayName("verifyProfileImageOwnership - URL does not match stored picture returns false")
    void verifyProfileImageOwnership_urlMismatch_returnsFalse() {
        Admin admin = makeAdmin(1L, 1);
        admin.setProfilePicture("https://img.example.com/other.jpg");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        boolean result = adminProfileService.verifyProfileImageOwnership(1L, "https://img.example.com/different.jpg");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyProfileImageOwnership - URL matches stored picture returns true")
    void verifyProfileImageOwnership_urlMatches_returnsTrue() {
        Admin admin = makeAdmin(1L, 1);
        admin.setProfilePicture("https://img.example.com/pic.jpg");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        boolean result = adminProfileService.verifyProfileImageOwnership(1L, "https://img.example.com/pic.jpg");

        assertThat(result).isTrue();
    }
}
