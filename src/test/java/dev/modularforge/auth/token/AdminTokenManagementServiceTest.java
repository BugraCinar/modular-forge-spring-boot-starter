package dev.modularforge.auth.token;

import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.shared.audit.AdminActivityAudit;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTokenManagementServiceTest {

    @Mock PasswordResetTokenRepository passwordRepository;
    @Mock VerificationTokenRepository verificationRepository;
    @Mock UserRepository userRepository;
    @Mock AdminRepository adminRepository;
    @Mock TokenHashService tokenHashService;
    @Mock AdminActivityAudit activityAudit;
    @Mock HttpServletRequest request;

    private AdminTokenManagementService service;

    @BeforeEach
    void setUp() {
        service = new AdminTokenManagementService(passwordRepository, verificationRepository,
                userRepository, adminRepository, tokenHashService);
        ReflectionTestUtils.setField(service, "adminActivityLogger", activityAudit);
    }

    @Test
    void passwordResetListsCoverRoleExpiryAndDefaultQueries() {
        PasswordResetToken userToken = passwordToken(1L, "user", "visible-preview");
        PasswordResetToken adminToken = passwordToken(2L, "admin", null);
        PasswordResetToken unknownToken = passwordToken(3L, "service", " ");
        User user = new User();
        user.setUsername("ada");
        user.setEmail("ada@example.com");
        Admin admin = new Admin();
        admin.setUsername("root");
        admin.setEmail("root@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(tokenHashService.preview(any())).thenReturn("legacy-preview");
        var page = new PageImpl<>(List.of(userToken, adminToken, unknownToken));
        when(passwordRepository.findByRoleOrderByCreatedDateDesc(eq("user"), any(Pageable.class))).thenReturn(page);
        when(passwordRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(any(), any(Pageable.class))).thenReturn(page);
        when(passwordRepository.findAllByOrderByCreatedDateDesc(any(Pageable.class))).thenReturn(page);

        var byRole = service.getAllPasswordResetTokens("user", null, -4, 1000,
                "id", "ASC", 9L, request);
        var unexpired = service.getAllPasswordResetTokens(null, false, 0, 20,
                "unsafe", "desc", 9L, request);
        var all = service.getAllPasswordResetTokens("", true, 0, 20,
                null, "desc", 9L, request);

        assertThat(byRole.getTokens()).extracting("username")
                .containsExactly("ada", "root", "Unknown");
        assertThat(byRole.getTokens()).extracting("token")
                .containsExactly("visible-preview", "legacy-preview", "legacy-preview");
        assertThat(unexpired.getTokens()).hasSize(3);
        assertThat(all.getTokens()).hasSize(3);
        verify(activityAudit, org.mockito.Mockito.times(3)).logActivity(
                eq(9L), eq("READ"), eq("PasswordResetToken"), eq("list"), any(), eq(request));
    }

    @Test
    void verificationListsCoverRoleExpiryAndDefaultQueries() {
        VerificationToken userToken = verificationToken(1L, "user", "preview");
        VerificationToken adminToken = verificationToken(2L, "admin", null);
        VerificationToken otherToken = verificationToken(3L, "service", " ");
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(adminRepository.findById(2L)).thenReturn(Optional.empty());
        when(tokenHashService.preview(any())).thenReturn("legacy");
        var page = new PageImpl<>(List.of(userToken, adminToken, otherToken));
        when(verificationRepository.findByRoleOrderByCreatedDateDesc(eq("admin"), any(Pageable.class))).thenReturn(page);
        when(verificationRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(any(), any(Pageable.class))).thenReturn(page);
        when(verificationRepository.findAllByOrderByCreatedDateDesc(any(Pageable.class))).thenReturn(page);

        var byRole = service.getAllVerificationTokens("admin", true, 0, 10,
                "used", "asc", 9L, request);
        var unexpired = service.getAllVerificationTokens(null, false, 0, 10,
                "bad-sort", "DESC", 9L, request);
        var all = service.getAllVerificationTokens("", null, 0, 10,
                "", "desc", 9L, request);

        assertThat(byRole.getTokens()).extracting("username")
                .containsOnly("Unknown");
        assertThat(unexpired.getTokens()).hasSize(3);
        assertThat(all.getTokens()).hasSize(3);
    }

    @Test
    void verificationTokensResolveBothUserAndAdminOwners() {
        VerificationToken userToken = verificationToken(1L, "user", "preview");
        VerificationToken adminToken = verificationToken(2L, "admin", "preview");
        User user = new User();
        user.setUsername("ada");
        user.setEmail("ada@example.com");
        Admin admin = new Admin();
        admin.setUsername("root");
        admin.setEmail("root@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(verificationRepository.findAllByOrderByCreatedDateDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(userToken, adminToken)));

        var result = service.getAllVerificationTokens(null, null, 0, 20,
                "createdDate", "desc", 9L, request);

        assertThat(result.getTokens()).extracting("username").containsExactly("ada", "root");
        assertThat(result.getTokens()).extracting("email").containsExactly("ada@example.com", "root@example.com");
    }

    @Test
    void passwordTokenKeepsUnknownOwnerAndSortValidatorsAcceptEmptyAndNullValues() {
        PasswordResetToken token = passwordToken(2L, "admin", "preview");
        when(adminRepository.findById(2L)).thenReturn(Optional.empty());
        when(passwordRepository.findAllByOrderByCreatedDateDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(token)));
        when(verificationRepository.findAllByOrderByCreatedDateDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getAllPasswordResetTokens(null, null, 0, 20,
                "", "desc", 9L, request).getTokens()).extracting("username").containsExactly("Unknown");
        service.getAllPasswordResetTokens(null, null, 0, 20, null, "desc", 9L, request);
        service.getAllVerificationTokens(null, null, 0, 20, null, "desc", 9L, request);
        service.getAllVerificationTokens(null, null, 0, 20, "", "desc", 9L, request);
    }

    @Test
    void getsAndDeletesIndividualTokensAndReportsMissingIds() {
        PasswordResetToken passwordToken = passwordToken(11L, "user", "preview");
        VerificationToken verificationToken = verificationToken(12L, "admin", "preview");
        when(passwordRepository.findById(11L)).thenReturn(Optional.of(passwordToken));
        when(passwordRepository.findById(99L)).thenReturn(Optional.empty());
        when(verificationRepository.findById(12L)).thenReturn(Optional.of(verificationToken));
        when(verificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.getPasswordResetTokenById(11L, 9L, request).getId()).isEqualTo(11L);
        assertThat(service.getVerificationTokenById(12L, 9L, request).getId()).isEqualTo(12L);
        service.deletePasswordResetToken(11L, 9L, request);
        service.deleteVerificationToken(12L, 9L, request);

        verify(passwordRepository).deleteById(11L);
        verify(verificationRepository).deleteById(12L);
        assertThatThrownBy(() -> service.getPasswordResetTokenById(99L, 9L, request))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.getVerificationTokenById(99L, 9L, request))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.deletePasswordResetToken(99L, 9L, request))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.deleteVerificationToken(99L, 9L, request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void bulkCleanupDeletesEveryExpiredToken() {
        var passwordTokens = List.of(passwordToken(1L, "user", "p"), passwordToken(2L, "admin", "p"));
        var verificationTokens = List.of(verificationToken(3L, "user", "v"));
        when(passwordRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(passwordTokens));
        when(verificationRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(verificationTokens));

        assertThat(service.deleteExpiredPasswordResetTokens(9L, request)).isEqualTo(2);
        assertThat(service.deleteExpiredVerificationTokens(9L, request)).isEqualTo(1);

        verify(passwordRepository).deleteAll(passwordTokens);
        verify(verificationRepository).deleteAll(verificationTokens);
    }

    private PasswordResetToken passwordToken(Long id, String role, String preview) {
        PasswordResetToken token = new PasswordResetToken();
        token.setId(id);
        token.setToken("legacy-" + id);
        token.setTokenPreview(preview);
        token.setUserId(id);
        token.setRole(role);
        token.setCreatedDate(LocalDateTime.now().minusHours(2));
        token.setExpiryDate(LocalDateTime.now().minusHours(1));
        token.setAttemptCount(1);
        token.setRequestingIp("127.0.0.1");
        return token;
    }

    private VerificationToken verificationToken(Long id, String role, String preview) {
        VerificationToken token = new VerificationToken();
        token.setId(id);
        token.setToken("legacy-" + id);
        token.setTokenPreview(preview);
        token.setUserId(id);
        token.setRole(role);
        token.setCreatedDate(LocalDateTime.now().minusHours(2));
        token.setExpiryDate(LocalDateTime.now().minusHours(1));
        return token;
    }
}
