package dev.modularforge.services;

import dev.modularforge.admin.AdminManagementService;
import dev.modularforge.audit.AdminActivityLogger;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;

import dev.modularforge.admin.dto.AdminManagementDTO;
import dev.modularforge.admin.dto.CreateAdminRequest;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminRequest;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.shared.error.UnauthorizedException;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceTest {

    @InjectMocks
    private AdminManagementService adminManagementService;

    @Mock private AdminRepository adminRepository;
    @Mock private PasswordService passwordService;
    @Mock private AdminActivityLogger activityLogger;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private HttpServletRequest httpRequest;

    private Admin makeAdmin(Long id, Integer level) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setLevel(level);
        admin.setUsername("admin" + id);
        admin.setEmail("admin" + id + "@test.com");
        admin.setFirstName("First" + id);
        admin.setLastName("Last" + id);
        admin.setIsActive(true);
        admin.setLoginAttempts(0);
        return admin;
    }

    private CreateAdminRequest makeCreateRequest(Integer level) {
        CreateAdminRequest req = new CreateAdminRequest();
        req.setUsername("newadmin");
        req.setEmail("new@test.com");
        req.setPassword("Password1!");
        req.setFirstName("New");
        req.setLastName("Admin");
        req.setLevel(level);
        return req;
    }

    private void stubAllUniquenessChecks() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(adminRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(adminRepository.existsByEmail(any())).thenReturn(false);
    }

    @Test
    @DisplayName("createAdmin - requesting admin not found throws ResourceNotFoundException")
    void createAdmin_requestingAdminNotFound_throws() {
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminManagementService.createAdmin(1L, makeCreateRequest(2), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createAdmin - level 2 cannot create anyone - UnauthorizedException")
    void createAdmin_level2Requester_unauthorized() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 2)));

        assertThatThrownBy(() -> adminManagementService.createAdmin(1L, makeCreateRequest(2), httpRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("createAdmin - level 1 cannot create level 1 - UnauthorizedException")
    void createAdmin_level1CreatesLevel1_unauthorized() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));

        assertThatThrownBy(() -> adminManagementService.createAdmin(1L, makeCreateRequest(1), httpRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("createAdmin - super admin creating level 0 via API throws BadRequestException")
    void createAdmin_superAdminCreatesLevel0_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));

        assertThatThrownBy(() -> adminManagementService.createAdmin(1L, makeCreateRequest(0), httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("super admin");
    }

    @Test
    @DisplayName("createAdmin - duplicate username throws BadRequestException")
    void createAdmin_duplicateUsername_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(userRepository.existsByUsername("newadmin")).thenReturn(false);
        when(adminRepository.existsByUsername("newadmin")).thenReturn(true);

        assertThatThrownBy(() -> adminManagementService.createAdmin(1L, makeCreateRequest(1), httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Username");
    }

    @Test
    @DisplayName("createAdmin - email taken in clientRepository throws BadRequestException")
    void createAdmin_duplicateEmail_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(adminRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(true);

        assertThatThrownBy(() -> adminManagementService.createAdmin(1L, makeCreateRequest(1), httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email");
    }

    @Test
    @DisplayName("createAdmin - level 0 creating level 1 succeeds")
    void createAdmin_superAdminCreatesLevel1_success() {
        Admin saved = makeAdmin(99L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        stubAllUniquenessChecks();
        when(passwordService.generateSalt()).thenReturn("salt");
        when(passwordService.hashPassword(any(), any())).thenReturn("hash");
        when(adminRepository.save(any(Admin.class))).thenReturn(saved);

        AdminManagementDTO result = adminManagementService.createAdmin(1L, makeCreateRequest(1), httpRequest);

        assertThat(result).isNotNull();
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    @DisplayName("createAdmin - level 1 creating level 2 succeeds")
    void createAdmin_level1CreatesLevel2_success() {
        Admin saved = makeAdmin(99L, 2);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));
        stubAllUniquenessChecks();
        when(passwordService.generateSalt()).thenReturn("salt");
        when(passwordService.hashPassword(any(), any())).thenReturn("hash");
        when(adminRepository.save(any(Admin.class))).thenReturn(saved);

        AdminManagementDTO result = adminManagementService.createAdmin(1L, makeCreateRequest(2), httpRequest);

        assertThat(result).isNotNull();
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    @DisplayName("deleteAdmin - self-delete throws BadRequestException")
    void deleteAdmin_selfDelete_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));

        assertThatThrownBy(() -> adminManagementService.deleteAdmin(1L, 1L, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("delete your own account");
    }

    @Test
    @DisplayName("deleteAdmin - super admin deleting another super admin throws BadRequestException")
    void deleteAdmin_superAdminDeletesSuperAdmin_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 0)));

        assertThatThrownBy(() -> adminManagementService.deleteAdmin(1L, 2L, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("super admin");
    }

    @Test
    @DisplayName("deleteAdmin - level 1 cannot delete level 1 - UnauthorizedException")
    void deleteAdmin_level1CannotDeleteLevel1_unauthorized() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 1)));

        assertThatThrownBy(() -> adminManagementService.deleteAdmin(1L, 2L, httpRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("deleteAdmin - level 1 deleting level 2 sets isActive false")
    void deleteAdmin_level1DeletesLevel2_success() {
        Admin target = makeAdmin(2L, 2);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(adminRepository.save(any(Admin.class))).thenReturn(target);

        adminManagementService.deleteAdmin(1L, 2L, httpRequest);

        assertThat(target.getIsActive()).isFalse();
        verify(adminRepository).save(target);
        verify(refreshTokenService).revokeAllUserTokens(2L, "admin");
    }

    @Test
    @DisplayName("deleteAdmin - super admin deleting level 1 sets isActive false")
    void deleteAdmin_superAdminDeletesLevel1_success() {
        Admin target = makeAdmin(2L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(adminRepository.save(any(Admin.class))).thenReturn(target);

        adminManagementService.deleteAdmin(1L, 2L, httpRequest);

        assertThat(target.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("deactivateAdmin - self-deactivate throws BadRequestException")
    void deactivateAdmin_selfDeactivate_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));

        assertThatThrownBy(() -> adminManagementService.deactivateAdmin(1L, 1L, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deactivate your own account");
    }

    @Test
    @DisplayName("deactivateAdmin - super admin deactivating another super admin throws BadRequestException")
    void deactivateAdmin_superAdminDeactivatesSuperAdmin_badRequest() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 0)));

        assertThatThrownBy(() -> adminManagementService.deactivateAdmin(1L, 2L, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("super admin");
    }

    @Test
    @DisplayName("deactivateAdmin - valid deactivation sets isActive false")
    void deactivateAdmin_valid_setsInactive() {
        Admin target = makeAdmin(2L, 1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(adminRepository.save(any(Admin.class))).thenReturn(target);

        adminManagementService.deactivateAdmin(1L, 2L, httpRequest);

        assertThat(target.getIsActive()).isFalse();
        verify(adminRepository).save(target);
        verify(refreshTokenService).revokeAllUserTokens(2L, "admin");
    }

    @Test
    @DisplayName("activateAdmin - sets isActive true, resets loginAttempts and lockedUntil")
    void activateAdmin_valid_resetsLockAndActivates() {
        Admin target = makeAdmin(2L, 1);
        target.setIsActive(false);
        target.setLoginAttempts(5);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(adminRepository.save(any(Admin.class))).thenReturn(target);

        adminManagementService.activateAdmin(1L, 2L, httpRequest);

        assertThat(target.getIsActive()).isTrue();
        assertThat(target.getLoginAttempts()).isEqualTo(0);
        assertThat(target.getLockedUntil()).isNull();
        verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("updateAdmin - email already in use throws BadRequestException")
    void updateAdmin_emailAlreadyInUse_badRequest() {
        UpdateAdminRequest req = new UpdateAdminRequest();
        req.setEmail("taken@test.com");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 1)));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> adminManagementService.updateAdmin(1L, 2L, req, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email");
    }

    @Test
    @DisplayName("updateAdmin - level change to 0 throws BadRequestException")
    void updateAdmin_levelChangeTo0_badRequest() {
        UpdateAdminRequest req = new UpdateAdminRequest();
        req.setLevel(0);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 1)));

        assertThatThrownBy(() -> adminManagementService.updateAdmin(1L, 2L, req, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("super admin level");
    }

    @Test
    @DisplayName("updateAdmin - level change from 0 throws BadRequestException")
    void updateAdmin_levelChangeFrom0_badRequest() {
        UpdateAdminRequest req = new UpdateAdminRequest();
        req.setLevel(1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 0)));

        assertThatThrownBy(() -> adminManagementService.updateAdmin(1L, 2L, req, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("super admin level");
    }

    @Test
    @DisplayName("updateAdmin - valid update applies field changes and saves")
    void updateAdmin_valid_appliesChanges() {
        Admin target = makeAdmin(2L, 1);
        UpdateAdminRequest req = new UpdateAdminRequest();
        req.setFirstName("UpdatedFirst");
        req.setLastName("UpdatedLast");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(adminRepository.save(any(Admin.class))).thenReturn(target);

        AdminManagementDTO result = adminManagementService.updateAdmin(1L, 2L, req, httpRequest);

        assertThat(result).isNotNull();
        assertThat(target.getFirstName()).isEqualTo("UpdatedFirst");
        assertThat(target.getLastName()).isEqualTo("UpdatedLast");
        verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("resetAdminPassword - generates new salt and hash, resets lockout state")
    void resetAdminPassword_valid_savesNewCredentials() {
        Admin target = makeAdmin(2L, 1);
        ResetUserPasswordRequest req = new ResetUserPasswordRequest();
        req.setNewPassword("NewPassword1!");
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordService.generateSalt()).thenReturn("newSalt");
        when(passwordService.hashPassword("NewPassword1!", "newSalt")).thenReturn("newHash");

        adminManagementService.resetAdminPassword(1L, 2L, req);

        assertThat(target.getSalt()).isEqualTo("newSalt");
        assertThat(target.getPasswordHash()).isEqualTo("newHash");
        assertThat(target.getLoginAttempts()).isEqualTo(0);
        assertThat(target.getLockedUntil()).isNull();
        verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("resetAdminPassword - insufficient permission throws UnauthorizedException")
    void resetAdminPassword_insufficientPermission_unauthorized() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 1)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(makeAdmin(2L, 1)));

        ResetUserPasswordRequest req = new ResetUserPasswordRequest();
        req.setNewPassword("NewPassword1!");

        assertThatThrownBy(() -> adminManagementService.resetAdminPassword(1L, 2L, req))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("unlockAdmin - resets loginAttempts to 0 and clears lockedUntil")
    void unlockAdmin_valid_resetsLock() {
        Admin target = makeAdmin(2L, 1);
        target.setLoginAttempts(5);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(makeAdmin(1L, 0)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(target));
        when(adminRepository.save(any(Admin.class))).thenReturn(target);

        adminManagementService.unlockAdmin(1L, 2L);

        assertThat(target.getLoginAttempts()).isEqualTo(0);
        assertThat(target.getLockedUntil()).isNull();
        verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("getAllAdmins - requesting admin not found throws ResourceNotFoundException")
    void getAllAdmins_requestingAdminNotFound_throws() {
        when(adminRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminManagementService.getAllAdmins(99L, 0, 10, "createdAt", "asc", httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getAllAdmins - level 0 (super admin) calls findAll")
    void getAllAdmins_superAdmin_callsFindAll() {
        Admin requester = makeAdmin(1L, 0);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(adminRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(requester)));

        var result = adminManagementService.getAllAdmins(1L, 0, 10, "createdAt", "asc", httpRequest);

        assertThat(result).isNotNull();
        assertThat(result.getAdmins()).hasSize(1);
        verify(adminRepository).findAll(any(org.springframework.data.domain.Pageable.class));
    }
}
