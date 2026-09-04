package dev.modularforge.services;

import dev.modularforge.admin.AdminUserManagementService;
import dev.modularforge.admin.dto.AdminCreateUserRequest;
import dev.modularforge.admin.dto.AdminUpdateUserRequest;
import dev.modularforge.admin.dto.AdminUserDTO;
import dev.modularforge.admin.dto.AdminUserListResponse;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.audit.AdminActivityLogger;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.notification.EmailService;

import dev.modularforge.admin.dto.*;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.token.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserManagementService Unit Tests")
class AdminUserManagementServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AdminActivityLogger activityLogger;
    @Mock
    private TokenHashService tokenHashService;
    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AdminUserManagementService service;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(10L);
        activeUser.setUsername("johndoe");
        activeUser.setEmail("john@example.com");
        activeUser.setFirstName("John");
        activeUser.setLastName("Doe");
        activeUser.setIsActive(true);
        activeUser.setAdminDeactivated(false);
        activeUser.setEmailVerified(true);
        activeUser.setPasswordHash("hash");
        activeUser.setSalt("salt");
        activeUser.setUserType(UserType.APP_USER);
        activeUser.setLoginAttempts(0);
        activeUser.setCreatedAt(LocalDateTime.now());
        activeUser.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("getUsers → returns paginated list")
    void getUsers_returnsList() {
        Page<User> page = new PageImpl<>(List.of(activeUser));
        when(userRepository.findWithFilters(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        AdminUserListResponse response = service.getUsers(1L, 0, 20,
                "createdAt", "desc", null, null, null, null, httpRequest);

        assertThat(response.getTotalItems()).isEqualTo(1);
        assertThat(response.getUsers()).hasSize(1);
        assertThat(response.getUsers().get(0).getUsername()).isEqualTo("johndoe");
    }

    @Test
    @DisplayName("getUsers → throws BadRequestException for unknown userType filter")
    void getUsers_invalidUserType_throws() {
        assertThatThrownBy(() -> service.getUsers(
                1L, 0, 20, "createdAt", "desc", null, null, null, "INVALID_TYPE", httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid userType value");
    }

    @Test
    @DisplayName("getUser → returns DTO for existing user")
    void getUser_found_returnsDTO() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));

        AdminUserDTO dto = service.getUser(1L, 10L, httpRequest);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getUsername()).isEqualTo("johndoe");
    }

    @Test
    @DisplayName("getUser → throws ResourceNotFoundException for missing user")
    void getUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(1L, 99L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createUser → throws when username already taken")
    void createUser_usernameTaken_throws() {
        AdminCreateUserRequest req = makeCreateRequest();
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(1L, req, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("createUser → throws when email already taken")
    void createUser_emailTaken_throws() {
        AdminCreateUserRequest req = makeCreateRequest();
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(adminRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(1L, req, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("createUser → success: saves user with verification email")
    void createUser_success_savesAndSendsEmail() {
        AdminCreateUserRequest req = makeCreateRequest();
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(adminRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(adminRepository.existsByEmail(any())).thenReturn(false);
        when(passwordService.generateSalt()).thenReturn("salt");
        when(passwordService.hashPassword(any(), any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(verificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenHashService.generateToken()).thenReturn("raw-verification-token");
        when(tokenHashService.hashToken("raw-verification-token")).thenReturn("hashed-verification-token");
        when(tokenHashService.preview("raw-verification-token")).thenReturn("raw-veri...token");

        AdminUserDTO dto = service.createUser(1L, req, httpRequest);

        assertThat(dto.getUsername()).isEqualTo("johndoe");
        verify(verificationTokenRepository).save(argThat(t ->
                "hashed-verification-token".equals(t.getTokenHash())
                        && "raw-veri...token".equals(t.getTokenPreview())
                        && "raw-veri...token".equals(t.getStoredToken())));
        verify(emailService).sendVerificationEmail(anyString(), eq("raw-verification-token"), anyString());
    }

    @Test
    @DisplayName("createUser → skips verification email when skipEmailVerification=true")
    void createUser_skipVerification_doesNotSendEmail() {
        AdminCreateUserRequest req = makeCreateRequest();
        req.setSkipEmailVerification(true);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(adminRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(adminRepository.existsByEmail(any())).thenReturn(false);
        when(passwordService.generateSalt()).thenReturn("salt");
        when(passwordService.hashPassword(any(), any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        AdminUserDTO dto = service.createUser(1L, req, httpRequest);

        assertThat(dto.getEmailVerified()).isTrue();
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("updateUser → throws when new email already taken")
    void updateUser_emailTaken_throws() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setEmail("taken@example.com");

        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateUser(1L, 10L, req, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    @DisplayName("updateUser → applies non-null fields and saves")
    void updateUser_success_appliesFields() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setFirstName("Jane");
        req.setUserType(UserType.APP_USER);

        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserDTO dto = service.updateUser(1L, 10L, req, httpRequest);

        assertThat(dto.getFirstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("updateUser → resets emailVerified when admin changes email")
    void updateUser_emailChange_resetsEmailVerified() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setEmail("brand-new@example.com");

        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("brand-new@example.com")).thenReturn(false);
        when(adminRepository.existsByEmail("brand-new@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserDTO dto = service.updateUser(1L, 10L, req, httpRequest);

        assertThat(dto.getEmail()).isEqualTo("brand-new@example.com");
        assertThat(dto.getEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("deactivateUser → throws when user already inactive")
    void deactivateUser_alreadyInactive_throws() {
        activeUser.setIsActive(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> service.deactivateUser(1L, 10L, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already inactive");
    }

    @Test
    @DisplayName("deactivateUser → sets isActive=false, adminDeactivated=true, revokes tokens")
    void deactivateUser_success_setsFlags() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenService.revokeAllUserTokens(10L, "user")).thenReturn(1);

        AdminUserDTO dto = service.deactivateUser(1L, 10L, httpRequest);

        assertThat(dto.getIsActive()).isFalse();
        assertThat(dto.getAdminDeactivated()).isTrue();
        verify(refreshTokenService).revokeAllUserTokens(10L, "user");
    }

    @Test
    @DisplayName("reactivateUser → throws when user already active")
    void reactivateUser_alreadyActive_throws() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> service.reactivateUser(1L, 10L, httpRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already active");
    }

    @Test
    @DisplayName("reactivateUser → clears deactivated flags and login lock")
    void reactivateUser_success_clearsFlags() {
        activeUser.setIsActive(false);
        activeUser.setAdminDeactivated(true);
        activeUser.setDeactivatedAt(LocalDateTime.now().minusDays(5));
        activeUser.setLoginAttempts(5);
        activeUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserDTO dto = service.reactivateUser(1L, 10L, httpRequest);

        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getAdminDeactivated()).isFalse();
        assertThat(dto.getDeactivatedAt()).isNull();
        assertThat(dto.getLoginAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("hardDeleteUser → throws for unknown user")
    void hardDeleteUser_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hardDeleteUser(1L, 99L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("hardDeleteUser → revokes tokens, removes tokens, deletes user")
    void hardDeleteUser_success_deletesAll() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(refreshTokenService.revokeAllUserTokens(10L, "user")).thenReturn(2);

        service.hardDeleteUser(1L, 10L, httpRequest);

        verify(refreshTokenService).revokeAllUserTokens(10L, "user");
        verify(verificationTokenRepository).deleteByUserIdAndRole(10L, "user");
        verify(verificationTokenRepository).deleteByUserIdAndRole(10L, "email_change");
        verify(userRepository).delete(activeUser);
    }

    @Test
    @DisplayName("resetUserPassword → hashes new password and saves")
    void resetUserPassword_success() {
        ResetUserPasswordRequest req = new ResetUserPasswordRequest();
        req.setNewPassword("NewPass1!");

        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(passwordService.generateSalt()).thenReturn("newSalt");
        when(passwordService.hashPassword("NewPass1!", "newSalt")).thenReturn("newHash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resetUserPassword(1L, 10L, req);

        verify(userRepository)
                .save(argThat(u -> "newSalt".equals(u.getSalt()) && "newHash".equals(u.getPasswordHash())));
    }

    @Test
    @DisplayName("unlockUser → resets loginAttempts and lockedUntil")
    void unlockUser_success_clearsLock() {
        activeUser.setLoginAttempts(5);
        activeUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserDTO dto = service.unlockUser(1L, 10L, httpRequest);

        assertThat(dto.getLoginAttempts()).isEqualTo(0);
        assertThat(dto.getLockedUntil()).isNull();
    }

    private AdminCreateUserRequest makeCreateRequest() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("johndoe");
        req.setEmail("john@example.com");
        req.setPassword("Password1!");
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setUserType(UserType.APP_USER);
        return req;
    }
}
