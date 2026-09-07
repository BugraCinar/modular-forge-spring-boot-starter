package dev.modularforge.admin;

import dev.modularforge.admin.dto.AdminCreateUserRequest;
import dev.modularforge.admin.dto.AdminUpdateUserRequest;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.auth.token.VerificationTokenRepository;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.shared.notification.NotificationGateway;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserManagementServiceExtendedTest {

    @Mock UserRepository users;
    @Mock AdminRepository admins;
    @Mock PasswordService passwords;
    @Mock RefreshTokenService refreshTokens;
    @Mock VerificationTokenRepository verificationTokens;
    @Mock NotificationGateway notifications;
    @Mock AdminActivityAudit activity;
    @Mock TokenHashService tokenHashes;
    @Mock HttpServletRequest request;

    private AdminUserManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserManagementService(users, admins, passwords, refreshTokens,
                verificationTokens, notifications, activity, tokenHashes);
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listSupportsValidUserTypeTrimsSearchAndSanitizesSort() {
        User user = user();
        when(users.findWithFilters(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));

        assertThat(service.getUsers(7L, 0, 20, null, "desc", " alice ", true, false,
                "app_user", request).getUsers()).hasSize(1);
        assertThat(service.getUsers(7L, 0, 20, " ", "asc", " ", null, null,
                "", request).getUsers()).hasSize(1);
        assertThat(service.getUsers(7L, 0, 20, "unsafe", "desc", null, null, null,
                null, request).getUsers()).hasSize(1);
    }

    @Test
    void createCopiesExplicitTypeInactiveFlagAndUsesUsernameForEmail() {
        when(passwords.generateSalt()).thenReturn("salt");
        when(passwords.hashPassword("password", "salt")).thenReturn("hash");
        when(tokenHashes.generateToken()).thenReturn("raw");
        when(tokenHashes.hashToken("raw")).thenReturn("hashed");
        when(tokenHashes.preview("raw")).thenReturn("preview");
        when(users.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        AdminCreateUserRequest command = new AdminCreateUserRequest("alice", "alice@example.com", "password",
                null, "Doe", "+90", "bio", UserType.APP_USER, false, false);

        var result = service.createUser(7L, command, request);

        assertThat(result.getUserType()).isEqualTo("app_user");
        assertThat(result.getIsActive()).isFalse();
        verify(notifications).sendVerificationEmail("alice@example.com", "raw", "alice");
    }

    @Test
    void createChecksBothIdentityStoresAndAppliesDefaults() {
        AdminCreateUserRequest command = new AdminCreateUserRequest("alice", "alice@example.com", "password",
                "Alice", "Doe", null, null, null, null, true);
        when(users.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(7L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(users.existsByUsername("alice")).thenReturn(false);
        when(admins.existsByUsername("alice")).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(7L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(admins.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(7L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(admins.existsByEmail("alice@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(7L, command, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(admins.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwords.generateSalt()).thenReturn("salt");
        when(passwords.hashPassword("password", "salt")).thenReturn("hash");
        when(users.save(any())).thenAnswer(invocation -> {
            User value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });
        assertThat(service.createUser(7L, command, request).getUserType()).isEqualTo("app_user");
    }

    @Test
    void updateCoversEveryOptionalFieldAndNullOldNames() {
        User user = user();
        user.setFirstName(null);
        user.setLastName(null);
        user.setEmailVerified(false);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        AdminUpdateUserRequest command = new AdminUpdateUserRequest("new@example.com", "New", "Name",
                "+90123", "new bio", UserType.APP_USER, true);

        var result = service.updateUser(7L, 10L, command, request);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getFirstName()).isEqualTo("New");
        assertThat(result.getLastName()).isEqualTo("Name");
        assertThat(result.getPhone()).isEqualTo("+90123");
        assertThat(result.getBio()).isEqualTo("new bio");
        assertThat(result.getEmailVerified()).isTrue();
    }

    @Test
    void updateAllowsTheSameEmailAndChecksBothStoresForAChangedEmail() {
        User user = user();
        when(users.findById(10L)).thenReturn(Optional.of(user));
        AdminUpdateUserRequest same = new AdminUpdateUserRequest();
        same.setEmail("ALICE@example.com");
        assertThat(service.updateUser(7L, 10L, same, request).getEmail()).isEqualTo("alice@example.com");

        AdminUpdateUserRequest changed = new AdminUpdateUserRequest();
        changed.setEmail("taken@example.com");
        when(users.existsByEmail("taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.updateUser(7L, 10L, changed, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);

        when(users.existsByEmail("taken@example.com")).thenReturn(false);
        when(admins.existsByEmail("taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.updateUser(7L, 10L, changed, request))
                .isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
    }

    @Test
    void togglesEmailVerificationInBothDirections() {
        User user = user();
        user.setEmailVerified(null);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        assertThat(service.toggleEmailVerified(7L, 10L, request).getEmailVerified()).isTrue();
        assertThat(service.toggleEmailVerified(7L, 10L, request).getEmailVerified()).isFalse();
    }

    @Test
    void dtoAllowsOptionalUserTypeToBeAbsent() {
        User user = user();
        user.setUserType(null);
        assertThat(service.mapToDTO(user).getUserType()).isNull();
    }

    private User user() {
        User user = new User();
        user.setId(10L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");
        user.setLastName("Doe");
        user.setPhone("1");
        user.setBio("bio");
        user.setUserType(UserType.APP_USER);
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setAdminDeactivated(false);
        user.setLoginAttempts(0);
        user.setAuthVersion(0L);
        return user;
    }
}
