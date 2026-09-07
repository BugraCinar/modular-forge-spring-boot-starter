package dev.modularforge.bootstrap;

import dev.modularforge.auth.PasswordService;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock AdminRepository adminRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordService passwordService;

    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(adminRepository, userRepository, passwordService);
        ReflectionTestUtils.setField(initializer, "adminUsername", "local_admin");
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-password-strong");
        ReflectionTestUtils.setField(initializer, "userUsername", "local_user");
        ReflectionTestUtils.setField(initializer, "userEmail", "user@example.com");
        ReflectionTestUtils.setField(initializer, "userPassword", "user-password-strong");
    }

    @Test
    void createsConfiguredAdminAndUser() {
        when(passwordService.generateSalt()).thenReturn("admin-salt", "user-salt");
        when(passwordService.hashPassword("admin-password-strong", "admin-salt")).thenReturn("admin-hash");
        when(passwordService.hashPassword("user-password-strong", "user-salt")).thenReturn("user-hash");

        initializer.run("ignored");

        ArgumentCaptor<Admin> admin = ArgumentCaptor.forClass(Admin.class);
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(adminRepository).save(admin.capture());
        verify(userRepository).save(user.capture());
        assertThat(admin.getValue().getUsername()).isEqualTo("local_admin");
        assertThat(admin.getValue().getPasswordHash()).isEqualTo("admin-hash");
        assertThat(admin.getValue().getPermissions()).containsExactly("ALL");
        assertThat(user.getValue().getUsername()).isEqualTo("local_user");
        assertThat(user.getValue().getPasswordHash()).isEqualTo("user-hash");
        assertThat(user.getValue().getUserType()).isEqualTo(UserType.APP_USER);
        assertThat(user.getValue().getEmailVerified()).isTrue();
    }

    @Test
    void skipsAccountsThatAlreadyExistByUsername() {
        when(adminRepository.existsByUsername("local_admin")).thenReturn(true);
        when(userRepository.existsByUsername("local_user")).thenReturn(true);

        initializer.run();

        verify(adminRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsAccountsThatAlreadyExistByEmail() {
        when(adminRepository.existsByEmail("admin@example.com")).thenReturn(true);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        initializer.run();

        verify(adminRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingAndShortSeedPasswords() {
        ReflectionTestUtils.setField(initializer, "adminPassword", null);
        assertThatThrownBy(initializer::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin-password");

        ReflectionTestUtils.setField(initializer, "adminPassword", "short");
        assertThatThrownBy(initializer::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin-password");

        ReflectionTestUtils.setField(initializer, "adminPassword", "admin-password-strong");
        ReflectionTestUtils.setField(initializer, "userPassword", "short");
        assertThatThrownBy(initializer::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user-password");
    }
}
