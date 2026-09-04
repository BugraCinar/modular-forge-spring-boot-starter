package dev.modularforge.security;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLevelAuthorizationServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminLevelAuthorizationService authorizationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inactiveAdminCannotPassLevelCheck() {
        Admin admin = admin(1L, false, null);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        authenticateAsAdmin(1L);

        assertThat(authorizationService.isLevel0()).isFalse();
    }

    @Test
    void lockedAdminCannotPassLevelCheck() {
        Admin admin = admin(2L, true, LocalDateTime.now().plusMinutes(1));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin));
        authenticateAsAdmin(2L);

        assertThat(authorizationService.isLevel0()).isFalse();
    }

    @Test
    void activeUnlockedAdminCanPassLevelCheck() {
        Admin admin = admin(3L, true, null);
        when(adminRepository.findById(3L)).thenReturn(Optional.of(admin));
        authenticateAsAdmin(3L);

        assertThat(authorizationService.isLevel0()).isTrue();
    }

    private Admin admin(Long id, Boolean active, LocalDateTime lockedUntil) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setLevel(0);
        admin.setIsActive(active);
        admin.setLockedUntil(lockedUntil);
        return admin;
    }

    private void authenticateAsAdmin(Long id) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(id);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
