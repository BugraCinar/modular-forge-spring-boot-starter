package dev.modularforge.services;

import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.notification.EmailService;
import dev.modularforge.profile.UserProfileService;

import dev.modularforge.admin.dto.ChangePasswordRequest;
import dev.modularforge.profile.dto.ChangeEmailRequest;
import dev.modularforge.profile.dto.DeactivateAccountRequest;
import dev.modularforge.profile.dto.UpdateUserProfileRequest;
import dev.modularforge.profile.dto.UserProfileDTO;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.token.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Unit Tests")
class UserProfileServiceTest {

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
    private TokenHashService tokenHashService;

    @InjectMocks
    private UserProfileService userProfileService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setUsername("testUser");
        activeUser.setEmail("user@test.com");
        activeUser.setFirstName("John");
        activeUser.setLastName("Doe");
        activeUser.setIsActive(true);
        activeUser.setEmailVerified(true);
        activeUser.setPasswordHash("hash");
        activeUser.setSalt("salt");
        activeUser.setAdminDeactivated(false);
        activeUser.setUserType(UserType.APP_USER);
        activeUser.setLoginAttempts(0);
        activeUser.setCreatedAt(LocalDateTime.now());
        activeUser.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("getProfile → returns DTO for active user")
    void getProfile_returnsDTO() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        UserProfileDTO dto = userProfileService.getProfile(1L);

        assertThat(dto.getUsername()).isEqualTo("testUser");
        assertThat(dto.getEmail()).isEqualTo("user@test.com");
        assertThat(dto.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getProfile → throws ResourceNotFoundException for unknown user")
    void getProfile_throwsForUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProfile → throws ResourceNotFoundException for inactive user")
    void getProfile_throwsForInactiveUser() {
        activeUser.setIsActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> userProfileService.getProfile(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateProfile → applies non-null fields")
    void updateProfile_appliesNonNullFields() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setFirstName("Jane");
        req.setBio("New bio");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileDTO dto = userProfileService.updateProfile(1L, req);

        assertThat(dto.getFirstName()).isEqualTo("Jane");
        assertThat(dto.getBio()).isEqualTo("New bio");
        assertThat(dto.getLastName()).isEqualTo("Doe"); // unchanged
    }

    @Test
    @DisplayName("updateProfile → throws BadRequestException when email already taken")
    void updateProfile_emailAlreadyTaken_throwsBadRequest() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setEmail("taken@test.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateProfile(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email is already in use");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile → skips email check when same email provided")
    void updateProfile_sameEmail_noConflictCheck() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setEmail("user@test.com"); // same as current

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userProfileService.updateProfile(1L, req);

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateProfileAppliesEmailLastNameAndPhoneWhenAvailable() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setEmail("new@test.com");
        request.setLastName("Updated");
        request.setPhone("+905551112233");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(adminRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        UserProfileDTO result = userProfileService.updateProfile(1L, request);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getLastName()).isEqualTo("Updated");
        assertThat(result.getPhone()).isEqualTo("+905551112233");
    }

    @Test
    void updateProfileRejectsEmailOwnedByAdmin() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setEmail("admin@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(adminRepository.existsByEmail("admin@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.updateProfile(1L, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void profilePictureLookupAndNullUserTypeMappingAreSupported() {
        activeUser.setProfilePicture("https://cdn.example.com/profile.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        assertThat(userProfileService.getProfilePictureUrl(1L))
                .isEqualTo("https://cdn.example.com/profile.jpg");

        activeUser.setUserType(null);
        assertThat(userProfileService.mapToDTO(activeUser).getUserType()).isNull();
    }

    @Test
    @DisplayName("changePassword → throws when confirmPassword doesn't match")
    void changePassword_mismatch_throws() {
        ChangePasswordRequest req = new ChangePasswordRequest("OldPass1!", "NewPass1!", "WrongConfirm!");

        assertThatThrownBy(() -> userProfileService.changePassword(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    @DisplayName("changePassword → throws when new password same as current")
    void changePassword_sameAsOld_throws() {
        ChangePasswordRequest req = new ChangePasswordRequest("SamePass1!", "SamePass1!", "SamePass1!");

        assertThatThrownBy(() -> userProfileService.changePassword(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different from current");
    }

    @Test
    @DisplayName("changePassword → throws when current password wrong")
    void changePassword_wrongCurrent_throws() {
        ChangePasswordRequest req = new ChangePasswordRequest("WrongOld!", "NewPass1!", "NewPass1!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("WrongOld!", "salt", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userProfileService.changePassword(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    @DisplayName("changePassword → success: generates new salt/hash and saves")
    void changePassword_success_savesNewHash() {
        ChangePasswordRequest req = new ChangePasswordRequest("OldPass1!", "NewPass1!", "NewPass1!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("OldPass1!", "salt", "hash")).thenReturn(true);
        when(passwordService.generateSalt()).thenReturn("newSalt");
        when(passwordService.hashPassword("NewPass1!", "newSalt")).thenReturn("newHash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userProfileService.changePassword(1L, req);

        verify(userRepository)
                .save(argThat(u -> "newSalt".equals(u.getSalt()) && "newHash".equals(u.getPasswordHash())));
    }

    @Test
    @DisplayName("deactivateAccount → throws BadRequestException when password wrong")
    void deactivateAccount_wrongPassword_throws() {
        DeactivateAccountRequest req = new DeactivateAccountRequest();
        req.setPassword("WrongPass!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("WrongPass!", "salt", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userProfileService.deactivateAccount(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateAccount → sets isActive=false, sets deactivatedAt, revokes tokens")
    void deactivateAccount_success_deactivatesAndRevokesTokens() {
        DeactivateAccountRequest req = new DeactivateAccountRequest();
        req.setPassword("CorrectPass1!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("CorrectPass1!", "salt", "hash")).thenReturn(true);
        when(refreshTokenService.revokeAllUserTokens(1L, "user")).thenReturn(2);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userProfileService.deactivateAccount(1L, req);

        verify(userRepository).save(argThat(u -> !u.getIsActive() && u.getDeactivatedAt() != null));
        verify(refreshTokenService).revokeAllUserTokens(1L, "user");
    }

    @Test
    @DisplayName("updateProfilePicture → sets URL and returns DTO")
    void updateProfilePicture_setsUrlAndReturnsDTO() {
        String url = "https://cdn.example.com/photo.jpg";

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileDTO dto = userProfileService.updateProfilePicture(1L, url);

        assertThat(dto.getProfilePicture()).isEqualTo(url);
    }

    @Test
    @DisplayName("requestEmailChange → throws when current password wrong")
    void requestEmailChange_wrongPassword_throws() {
        ChangeEmailRequest req = new ChangeEmailRequest("WrongPass!", "new@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("WrongPass!", "salt", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userProfileService.requestEmailChange(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestEmailChange → throws when new email same as current")
    void requestEmailChange_sameEmail_throws() {
        ChangeEmailRequest req = new ChangeEmailRequest("CorrectPass1!", "user@test.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("CorrectPass1!", "salt", "hash")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.requestEmailChange(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different from the current email");
    }

    @Test
    @DisplayName("requestEmailChange → throws when new email already in use")
    void requestEmailChange_emailTaken_throws() {
        ChangeEmailRequest req = new ChangeEmailRequest("CorrectPass1!", "taken@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("CorrectPass1!", "salt", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.requestEmailChange(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in use");
    }

    @Test
    void requestEmailChangeRejectsAnAddressOwnedByAnAdmin() {
        ChangeEmailRequest req = new ChangeEmailRequest("CorrectPass1!", "admin@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("CorrectPass1!", "salt", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(adminRepository.existsByEmail("admin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.requestEmailChange(1L, req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("requestEmailChange → success: sets pendingEmail, saves token, sends email")
    void requestEmailChange_success_setsPendingEmailAndSendsEmail() {
        ChangeEmailRequest req = new ChangeEmailRequest("CorrectPass1!", "new@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("CorrectPass1!", "salt", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(adminRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(verificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenHashService.generateToken()).thenReturn("raw-email-change-token");
        when(tokenHashService.hashToken("raw-email-change-token")).thenReturn("hashed-email-change-token");
        when(tokenHashService.preview("raw-email-change-token")).thenReturn("raw-emai...token");

        userProfileService.requestEmailChange(1L, req);

        verify(userRepository).save(argThat(u -> "new@example.com".equals(u.getPendingEmail())));
        verify(verificationTokenRepository)
                .save(argThat(t -> "email_change".equals(t.getRole())
                        && t.getUserId().equals(1L)
                        && "hashed-email-change-token".equals(t.getTokenHash())
                        && "raw-emai...token".equals(t.getTokenPreview())
                        && "raw-emai...token".equals(t.getStoredToken())));
        verify(emailService).sendEmailChangeVerificationEmail(
                eq("new@example.com"), eq("raw-email-change-token"), anyString());
    }

    @Test
    void requestEmailChangeUsesUsernameWhenFirstNameIsMissing() {
        activeUser.setFirstName(null);
        ChangeEmailRequest req = new ChangeEmailRequest("CorrectPass1!", "new@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(passwordService.verifyPassword("CorrectPass1!", "salt", "hash")).thenReturn(true);
        when(tokenHashService.generateToken()).thenReturn("raw-token");
        when(tokenHashService.hashToken("raw-token")).thenReturn("hash-token");
        when(tokenHashService.preview("raw-token")).thenReturn("preview");

        userProfileService.requestEmailChange(1L, req);

        verify(emailService).sendEmailChangeVerificationEmail("new@example.com", "raw-token", "testUser");
    }
}
