package dev.modularforge.auth;

import dev.modularforge.auth.token.*;
import dev.modularforge.identity.*;
import dev.modularforge.identity.model.*;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.notification.NotificationGateway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class EmailChangeServiceTest {
    UserRepository users = mock(UserRepository.class);
    AdminRepository admins = mock(AdminRepository.class);
    PasswordService passwords = mock(PasswordService.class);
    VerificationTokenRepository tokens = mock(VerificationTokenRepository.class);
    TokenHashService hashes = mock(TokenHashService.class);
    NotificationGateway notifications = mock(NotificationGateway.class);
    RefreshTokenService sessions = mock(RefreshTokenService.class);
    org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    EmailChangeService service = new EmailChangeService(users, admins, passwords, tokens, hashes, notifications, sessions, events);
    User user = new User(); Admin admin = new Admin();
    @BeforeEach void account() {
        user.setId(1L); user.setEmail("old@example.com"); user.setUsername("alice");
        admin.setId(1L); admin.setEmail("old@example.com"); admin.setUsername("alice");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(admins.findById(1L)).thenReturn(Optional.of(admin));
        when(passwords.verifyPassword(eq("correct"), any(), any())).thenReturn(true);
        when(hashes.generateToken()).thenReturn("raw"); when(hashes.hashToken(anyString())).thenAnswer(i -> "hash-" + i.getArgument(0));
        when(hashes.preview("raw")).thenReturn("masked");
    }
    @ParameterizedTest @ValueSource(strings = {"user", "admin"})
    void requestRequiresPasswordAndSendsBoundTokenToNewAddress(String role) {
        assertThatThrownBy(() -> service.request(1L, role, "wrong", "new@example.com")).isInstanceOf(BadRequestException.class);
        service.request(1L, role, "correct", " NEW@example.com ");
        verify(tokens).deleteByUserIdAndRole(1L, role + "_email_change");
        verify(tokens).save(argThat(t -> t.getRole().equals(role + "_email_change") && t.getRequestedEmail().equals("new@example.com")
                && t.getIssuedAuthVersion() == 0L && t.getTokenHash().equals("hash-raw")));
        verify(notifications).sendEmailChangeVerificationEmail("new@example.com", "raw", "alice");
        verify(notifications).sendEmailChangeNotice("old@example.com", "new@example.com");
        assertThat(user.getEmail()).isEqualTo("old@example.com"); assertThat(admin.getEmail()).isEqualTo("old@example.com");
    }
    @ParameterizedTest @ValueSource(strings = {"user", "admin"})
    void unchangedOccupiedMissingAndInactiveAccountsCannotRequest(String role) {
        assertThatThrownBy(() -> service.request(1L, role, "correct", "old@example.com")).isInstanceOf(BadRequestException.class);
        when(users.existsByEmail("user@example.com")).thenReturn(true);
        when(admins.existsByEmail("admin@example.com")).thenReturn(true);
        for (String email : new String[]{"user@example.com", "admin@example.com"})
            assertThatThrownBy(() -> service.request(1L, role, "correct", email)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.request(99L, role, "correct", "new@example.com")).isInstanceOf(BadRequestException.class);
        user.setIsActive(false); admin.setIsActive(false);
        assertThatThrownBy(() -> service.request(1L, role, "correct", "new@example.com")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(notifications);
    }
    VerificationToken token(String role) {
        var token = new VerificationToken(1L, role + "_email_change");
        token.setRequestedEmail("new@example.com"); token.setIssuedAuthVersion(0L);
        when(tokens.findByTokenHash("hash-raw")).thenReturn(Optional.of(token)); return token;
    }
    @ParameterizedTest @ValueSource(strings = {"user", "admin"})
    void confirmationChangesOnlyTheBoundAccountAndInvalidatesItsSessions(String role) {
        var token = token(role); service.confirm("raw");
        if (role.equals("user")) {
            assertThat(user.getEmail()).isEqualTo("new@example.com"); assertThat(user.currentAuthVersion()).isEqualTo(1);
            assertThat(user.getEmailVerified()).isTrue(); verify(users).saveAndFlush(user);
        } else {
            assertThat(admin.getEmail()).isEqualTo("new@example.com"); assertThat(admin.currentAuthVersion()).isEqualTo(1);
            verify(admins).saveAndFlush(admin);
        }
        verify(sessions).revokeAllUserTokens(1L, role); verify(tokens).delete(token);
        verify(events).publishEvent(argThat((Object e) -> e instanceof dev.modularforge.shared.events.AccountEvent event
                && event.accountRole().equals(role) && event.accountId().equals(1L)
                && event.type().equals("account.email-changed")));
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"user", "admin"})
    void accountChangesInvalidateOutstandingConfirmations(String role) {
        var token = token(role);
        token.setUserId(99L);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        token.setUserId(1L); user.setIsActive(false); admin.setIsActive(false);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        user.setIsActive(true); admin.setIsActive(true); token.setIssuedAuthVersion(2L);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(sessions);
    }
    @Test void rejectsMissingExpiredLegacyAndWrongPurposeTokens() {
        for (String raw : new String[]{null, " ", "missing"})
            assertThatThrownBy(() -> service.confirm(raw)).isInstanceOf(BadRequestException.class);
        var token = token("user"); token.setExpiryDate(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(1)); token.setRequestedEmail(null);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        token.setRequestedEmail("new@example.com"); token.setIssuedAuthVersion(null);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        token.setIssuedAuthVersion(0L); token.setRole("password_reset");
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.request(1L, "other", "correct", "new@example.com")).isInstanceOf(BadRequestException.class);
    }
    @Test void addressClaimedAfterRequestCannotBeConfirmed() {
        token("user"); when(users.existsByEmail("new@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        when(users.existsByEmail("new@example.com")).thenReturn(false); when(admins.existsByEmail("new@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.confirm("raw")).isInstanceOf(BadRequestException.class);
        verify(tokens, never()).delete(any()); verifyNoInteractions(sessions);
    }
}
