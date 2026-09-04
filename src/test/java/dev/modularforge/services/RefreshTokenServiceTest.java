package dev.modularforge.services;

import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;

import dev.modularforge.auth.token.RefreshToken;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.auth.token.RefreshTokenRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private AdminRepository adminRepository;
    @Mock private UserRepository userRepository;
    @Mock private TokenHashService tokenHashService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private void stubTokenIssuance() {
        when(tokenHashService.generateToken()).thenReturn("raw-refresh-token");
        when(tokenHashService.hashToken("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(tokenHashService.preview("raw-refresh-token")).thenReturn("raw-refr...token");
    }

    @Nested
    @DisplayName("createRefreshToken()")
    class CreateRefreshToken {

        @Test
        @DisplayName("Saves token and returns the persisted entity")
        void savesAndReturnsToken() {
            stubTokenIssuance();
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit/5.0");
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            RefreshToken saved = new RefreshToken(1L, "user", 30L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

            RefreshToken result = refreshTokenService.createRefreshToken(1L, "user", httpRequest);

            assertThat(result).isNotNull();
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Captures User-Agent as deviceInfo")
        void capturesDeviceInfo() {
            stubTokenIssuance();
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
            when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            RefreshToken saved = new RefreshToken(2L, "user", 30L);
            when(refreshTokenRepository.save(captor.capture())).thenReturn(saved);

            refreshTokenService.createRefreshToken(2L, "user", httpRequest);

            RefreshToken captured = captor.getValue();
            assertThat(captured.getDeviceInfo()).isEqualTo("Mozilla/5.0");
            assertThat(captured.getIpAddress()).isEqualTo("10.0.0.1");
            assertThat(captured.getTokenHash()).isEqualTo("hashed-refresh-token");
            assertThat(captured.getTokenPreview()).isEqualTo("raw-refr...token");
            assertThat(captured.getStoredToken()).isEqualTo("raw-refr...token");
            assertThat(captured.getToken()).isEqualTo("raw-refresh-token");
        }

        @Test
        @DisplayName("Uses servlet remote address for IP")
        void usesRemoteAddressForIp() {
            stubTokenIssuance();
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");
            when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.10");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            RefreshToken saved = new RefreshToken(3L, "admin", 30L);
            when(refreshTokenRepository.save(captor.capture())).thenReturn(saved);

            refreshTokenService.createRefreshToken(3L, "admin", httpRequest);

            assertThat(captor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
        }
    }

    @Nested
    @DisplayName("verifyRefreshToken()")
    class VerifyRefreshToken {

        @Test
        @DisplayName("Null token returns empty Optional")
        void nullToken_returnsEmpty() {
            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken(null);
            assertThat(result).isEmpty();
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("Empty string token returns empty Optional")
        void emptyToken_returnsEmpty() {
            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("");
            assertThat(result).isEmpty();
            verifyNoInteractions(refreshTokenRepository);
        }

        @Test
        @DisplayName("Token not found in DB returns empty Optional")
        void tokenNotFound_returnsEmpty() {
            when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());
            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("unknown-token");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Revoked token triggers revokeAllUserTokens and returns empty")
        void revokedToken_revokesAllAndReturnsEmpty() {
            RefreshToken revokedToken = new RefreshToken(10L, "user", 30L);
            revokedToken.setIsRevoked(true);

            when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revokedToken));

            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("revoked-token");

            assertThat(result).isEmpty();
            verify(refreshTokenRepository).revokeAllUserTokens(10L, "user");
        }

        @Test
        @DisplayName("Expired token returns empty Optional")
        void expiredToken_returnsEmpty() {
            RefreshToken expiredToken = new RefreshToken(11L, "user", 30L);
            expiredToken.setIsRevoked(false);
            expiredToken.setExpiryDate(LocalDateTime.now().minusDays(1));

            when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("expired-token");

            assertThat(result).isEmpty();
            verify(refreshTokenRepository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("Valid token returns present Optional")
        void validToken_returnsToken() {
            RefreshToken validToken = new RefreshToken(12L, "admin", 30L);
            validToken.setIsRevoked(false);

            when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(validToken));

            Optional<RefreshToken> result = refreshTokenService.verifyRefreshToken("valid-token");

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(validToken);
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken()")
    class RotateRefreshToken {

        @Test
        @DisplayName("Atomically consumes the old token before issuing a successor")
        void consumesOldTokenAtomically() {
            RefreshToken oldToken = new RefreshToken(20L, "user", 30L);
            oldToken.setId(20L);
            oldToken.setIsRevoked(false);

            stubTokenIssuance();
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Agent");
            when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");

            RefreshToken newSaved = new RefreshToken(20L, "user", 30L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newSaved);
            when(refreshTokenRepository.revokeIfActive(eq(20L), any(LocalDateTime.class))).thenReturn(1);

            Optional<RefreshToken> result = refreshTokenService.rotateRefreshToken(oldToken, httpRequest);

            assertThat(result).contains(newSaved);
            verify(refreshTokenRepository).revokeIfActive(eq(20L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Creates and returns a new token for the same user")
        void createsNewToken_forSameUser() {
            RefreshToken oldToken = new RefreshToken(21L, "user", 30L);
            oldToken.setId(21L);
            oldToken.setIsRevoked(false);

            stubTokenIssuance();
            when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
            when(httpRequest.getHeader("User-Agent")).thenReturn("Agent");
            when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");

            RefreshToken newToken = new RefreshToken(21L, "user", 30L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(newToken);
            when(refreshTokenRepository.revokeIfActive(eq(21L), any(LocalDateTime.class))).thenReturn(1);

            Optional<RefreshToken> result = refreshTokenService.rotateRefreshToken(oldToken, httpRequest);

            assertThat(result).contains(newToken);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Lost consume race revokes the entire session family")
        void consumeRace_revokesAllTokensAndReturnsEmpty() {
            RefreshToken oldToken = new RefreshToken(22L, "admin", 30L);
            oldToken.setId(22L);

            when(refreshTokenRepository.revokeIfActive(eq(22L), any(LocalDateTime.class))).thenReturn(0);

            Optional<RefreshToken> result = refreshTokenService.rotateRefreshToken(oldToken, httpRequest);

            assertThat(result).isEmpty();
            verify(refreshTokenRepository).revokeAllUserTokens(22L, "admin");
        }
    }

    @Nested
    @DisplayName("revokeRefreshToken()")
    class RevokeRefreshToken {

        @Test
        @DisplayName("Found token is revoked and method returns true")
        void found_revokesAndReturnsTrue() {
            RefreshToken token = new RefreshToken(30L, "user", 30L);
            token.setIsRevoked(false);
            when(refreshTokenRepository.findByToken("some-token")).thenReturn(Optional.of(token));

            boolean result = refreshTokenService.revokeRefreshToken("some-token");

            assertThat(result).isTrue();
            assertThat(token.getIsRevoked()).isTrue();
            verify(refreshTokenRepository, times(2)).save(token);
        }

        @Test
        @DisplayName("Token not found returns false")
        void notFound_returnsFalse() {
            when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

            boolean result = refreshTokenService.revokeRefreshToken("ghost-token");

            assertThat(result).isFalse();
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens()")
    class RevokeAllUserTokens {

        @Test
        @DisplayName("Delegates to repository and returns row count")
        void delegatesToRepository() {
            when(refreshTokenRepository.revokeAllUserTokens(5L, "user")).thenReturn(3);

            int result = refreshTokenService.revokeAllUserTokens(5L, "user");

            assertThat(result).isEqualTo(3);
            verify(refreshTokenRepository).revokeAllUserTokens(5L, "user");
        }
    }

    @Nested
    @DisplayName("cleanupExpiredTokens()")
    class CleanupExpiredTokens {

        @Test
        @DisplayName("Calls cleanupRevokedAndExpired and deleteByExpiryDateBefore")
        void callsBothCleanupMethods() {
            when(refreshTokenRepository.cleanupRevokedAndExpired(any(LocalDateTime.class))).thenReturn(5);

            int result = refreshTokenService.cleanupExpiredTokens();

            assertThat(result).isEqualTo(5);
            verify(refreshTokenRepository).cleanupRevokedAndExpired(any(LocalDateTime.class));
            verify(refreshTokenRepository).deleteByExpiryDateBefore(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("deleteByExpiryDateBefore is called with approximately 7 days ago")
        void deleteCalledWith7DaysAgo() {
            when(refreshTokenRepository.cleanupRevokedAndExpired(any())).thenReturn(0);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            refreshTokenService.cleanupExpiredTokens();

            verify(refreshTokenRepository).deleteByExpiryDateBefore(captor.capture());
            LocalDateTime cutoff = captor.getValue();
            LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(7);
            assertThat(cutoff).isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
        }
    }
}
