package dev.modularforge.admin;

import dev.modularforge.admin.dto.CreateAdminRequest;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminRequest;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.shared.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminManagementServiceExtendedTest {

    @Mock AdminRepository admins;
    @Mock PasswordService passwords;
    @Mock AdminActivityAudit activity;
    @Mock UserRepository users;
    @Mock RefreshTokenService refreshTokens;
    @Mock HttpServletRequest request;

    private AdminManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminManagementService();
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "passwordService", passwords);
        ReflectionTestUtils.setField(service, "activityLogger", activity);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "refreshTokenService", refreshTokens);
        when(admins.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listVisibilityChangesByAdminLevelAndSortInputIsSanitized() {
        Admin levelOne = admin(1L, 1);
        when(admins.findById(1L)).thenReturn(Optional.of(levelOne));
        when(admins.findByLevelGreaterThanEqual(any(Integer.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(admin(2L, 2))));
        assertThat(service.getAllAdmins(1L, 0, 20, null, "asc", request).getAdmins()).hasSize(1);

        Admin levelTwo = admin(2L, 2);
        when(admins.findById(2L)).thenReturn(Optional.of(levelTwo));
        when(admins.findByLevel(any(Integer.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(levelTwo)));
        assertThat(service.getAllAdmins(2L, 0, 20, "", "desc", request).getAdmins()).hasSize(1);
        assertThat(service.getAllAdmins(2L, 0, 20, "unsafe", "desc", request).getAdmins()).hasSize(1);
    }

    @Test
    void getAdminChecksBothRecordsAndHierarchy() {
        when(admins.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAdminById(99L, 2L, request)).isInstanceOf(ResourceNotFoundException.class);

        Admin requester = admin(1L, 1);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        when(admins.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAdminById(1L, 2L, request)).isInstanceOf(ResourceNotFoundException.class);

        Admin superAdmin = admin(2L, 0);
        when(admins.findById(2L)).thenReturn(Optional.of(superAdmin));
        assertThatThrownBy(() -> service.getAdminById(1L, 2L, request)).isInstanceOf(UnauthorizedException.class);
        assertThat(service.getAdminById(1L, 1L, request).getId()).isEqualTo(1L);
    }

    @Test
    void hierarchyCheckHandlesAnEqualIdentifierBeforeRejectingARealPeer() {
        Admin requester = admin(1L, 1);
        Admin inconsistentCopy = admin(1L, 0);
        when(admins.findById(1L)).thenReturn(Optional.of(requester), Optional.of(inconsistentCopy));
        assertThat(service.getAdminById(1L, 1L, request).getId()).isEqualTo(1L);
    }

    @Test
    void createCopiesEveryOptionalFieldAndExplicitInactiveFlag() {
        Admin requester = admin(1L, 0);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        when(passwords.generateSalt()).thenReturn("salt");
        when(passwords.hashPassword("password", "salt")).thenReturn("hash");
        when(admins.save(any())).thenAnswer(invocation -> {
            Admin value = invocation.getArgument(0);
            value.setId(3L);
            return value;
        });
        CreateAdminRequest command = new CreateAdminRequest("moderator", "m@example.com", "password",
                "Mod", "Erator", "picture", 2, List.of("READ"), false);

        var created = service.createAdmin(1L, command, request);

        assertThat(created.getFirstName()).isEqualTo("Mod");
        assertThat(created.getLastName()).isEqualTo("Erator");
        assertThat(created.getProfilePicture()).isEqualTo("picture");
        assertThat(created.getPermissions()).containsExactly("READ");
        assertThat(created.getIsActive()).isFalse();
    }

    @Test
    void createDefaultsActiveFlagWhenItIsOmitted() {
        Admin requester = admin(1L, 0);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        when(passwords.generateSalt()).thenReturn("salt");
        when(passwords.hashPassword("password", "salt")).thenReturn("hash");
        when(admins.save(any())).thenAnswer(invocation -> {
            Admin value = invocation.getArgument(0);
            value.setId(4L);
            return value;
        });
        CreateAdminRequest command = new CreateAdminRequest("operator", "operator@example.com", "password",
                null, null, null, 2, null, null);

        assertThat(service.createAdmin(1L, command, request).getIsActive()).isTrue();
    }

    @Test
    void createChecksUserAndAdminNamespacesForUsernameAndEmail() {
        Admin requester = admin(1L, 0);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        CreateAdminRequest command = new CreateAdminRequest("operator", "operator@example.com", "password",
                null, null, null, 2, null, null);

        when(users.existsByUsername("operator")).thenReturn(true);
        assertThatThrownBy(() -> service.createAdmin(1L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        when(users.existsByUsername("operator")).thenReturn(false);
        when(admins.existsByUsername("operator")).thenReturn(true);
        assertThatThrownBy(() -> service.createAdmin(1L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(admins.existsByUsername("operator")).thenReturn(false);
        when(users.existsByEmail("operator@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createAdmin(1L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        when(users.existsByEmail("operator@example.com")).thenReturn(false);
        when(admins.existsByEmail("operator@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createAdmin(1L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
    }

    @Test
    void updateCoversOptionalFieldsDeactivationAndLevelChange() {
        Admin requester = admin(1L, 0);
        Admin target = admin(2L, 2);
        target.setEmail("old@example.com");
        target.setIsActive(true);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        when(admins.findById(2L)).thenReturn(Optional.of(target));
        UpdateAdminRequest command = new UpdateAdminRequest("new@example.com", "New", "Name", "picture",
                1, List.of("WRITE"), false);

        var updated = service.updateAdmin(1L, 2L, command, request);

        assertThat(updated.getEmail()).isEqualTo("new@example.com");
        assertThat(updated.getProfilePicture()).isEqualTo("picture");
        assertThat(updated.getLevel()).isEqualTo(1);
        assertThat(updated.getPermissions()).containsExactly("WRITE");
        assertThat(updated.getIsActive()).isFalse();
        assertThat(target.getAuthVersion()).isEqualTo(1L);

        when(users.existsByEmail("taken@example.com")).thenReturn(false);
        when(admins.existsByEmail("taken@example.com")).thenReturn(true);
        command.setEmail("taken@example.com");
        assertThatThrownBy(() -> service.updateAdmin(1L, 2L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
    }

    @Test
    void updateReportsMissingRequesterAndTarget() {
        UpdateAdminRequest command = new UpdateAdminRequest();
        when(admins.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateAdmin(99L, 2L, command, request)).isInstanceOf(ResourceNotFoundException.class);
        when(admins.findById(1L)).thenReturn(Optional.of(admin(1L, 0)));
        when(admins.findById(98L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateAdmin(1L, 98L, command, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateHandlesSameValuesAndAllBooleanShortCircuitPaths() {
        Admin superAdmin = admin(1L, 0);
        superAdmin.setEmail("root@example.com");
        when(admins.findById(1L)).thenReturn(Optional.of(superAdmin));
        UpdateAdminRequest self = new UpdateAdminRequest();
        self.setEmail("root@example.com");
        self.setLevel(0);
        self.setIsActive(true);
        assertThat(service.updateAdmin(1L, 1L, self, request).getId()).isEqualTo(1L);

        Admin target = admin(2L, 2);
        target.setEmail("old@example.com");
        target.setIsActive(false);
        when(admins.findById(2L)).thenReturn(Optional.of(target));
        UpdateAdminRequest activate = new UpdateAdminRequest();
        activate.setEmail("new@example.com");
        activate.setLevel(2);
        activate.setIsActive(true);
        when(users.existsByEmail("new@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.updateAdmin(1L, 2L, activate, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(admins.existsByEmail("new@example.com")).thenReturn(false);
        assertThat(service.updateAdmin(1L, 2L, activate, request).getIsActive()).isTrue();
    }

    @Test
    void levelOneCannotUpdateAnotherLevelOneAdmin() {
        Admin requester = admin(1L, 1);
        Admin peer = admin(2L, 1);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        when(admins.findById(2L)).thenReturn(Optional.of(peer));

        assertThatThrownBy(() -> service.updateAdmin(1L, 2L, new UpdateAdminRequest(), request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void levelTwoCannotUpdateAnotherLevelTwoAdmin() {
        Admin requester = admin(1L, 2);
        Admin peer = admin(2L, 2);
        when(admins.findById(1L)).thenReturn(Optional.of(requester));
        when(admins.findById(2L)).thenReturn(Optional.of(peer));

        assertThatThrownBy(() -> service.updateAdmin(1L, 2L, new UpdateAdminRequest(), request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void lifecycleOperationsReportMissingRequestersAndTargets() {
        when(admins.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteAdmin(99L, 2L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.activateAdmin(99L, 2L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivateAdmin(99L, 2L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resetAdminPassword(99L, 2L, new ResetUserPasswordRequest("password")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.unlockAdmin(99L, 2L)).isInstanceOf(ResourceNotFoundException.class);

        when(admins.findById(1L)).thenReturn(Optional.of(admin(1L, 0)));
        when(admins.findById(98L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteAdmin(1L, 98L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.activateAdmin(1L, 98L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivateAdmin(1L, 98L, request)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resetAdminPassword(1L, 98L, new ResetUserPasswordRequest("password")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.unlockAdmin(1L, 98L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private Admin admin(Long id, int level) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername("admin-" + id);
        admin.setEmail("admin-" + id + "@example.com");
        admin.setFirstName("First");
        admin.setLastName("Last");
        admin.setLevel(level);
        admin.setPermissions(null);
        admin.setIsActive(true);
        admin.setLoginAttempts(0);
        admin.setAuthVersion(0L);
        return admin;
    }
}
