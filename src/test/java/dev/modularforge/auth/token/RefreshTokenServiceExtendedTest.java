package dev.modularforge.auth.token;

import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceExtendedTest {

    @Mock RefreshTokenRepository repository;
    @Mock JwtUtils jwtUtils;
    @Mock UserRepository users;
    @Mock AdminRepository admins;
    @Mock TokenHashService hashes;
    @Mock HttpServletRequest request;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService();
        ReflectionTestUtils.setField(service, "refreshTokenRepository", repository);
        ReflectionTestUtils.setField(service, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "adminRepository", admins);
        ReflectionTestUtils.setField(service, "tokenHashService", hashes);
        when(hashes.hashToken(any())).thenAnswer(invocation -> "hash-" + invocation.getArgument(0));
        when(hashes.preview(any())).thenReturn("preview");
    }

    @Test
    void createTokenNormalizesMissingEmptyAndLongDeviceNames() {
        when(jwtUtils.getRefreshTokenExpirationDays()).thenReturn(30L);
        when(hashes.generateToken()).thenReturn("raw");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(request.getHeader("User-Agent")).thenReturn(null);
        assertThat(service.createRefreshToken(1L, "user", 0L, request).getDeviceInfo()).isEqualTo("Unknown");
        when(request.getHeader("User-Agent")).thenReturn("");
        assertThat(service.createRefreshToken(1L, "user", 0L, request).getDeviceInfo()).isEqualTo("Unknown");
        when(request.getHeader("User-Agent")).thenReturn("x".repeat(501));
        assertThat(service.createRefreshToken(1L, "user", 0L, request).getDeviceInfo()).hasSize(500);
    }

    @Test
    void verifiesHashedAndMigratesOnlyEligibleLegacyTokens() {
        RefreshToken current = token(1L, "user", false, false);
        when(repository.findByTokenHash("hash-current")).thenReturn(Optional.of(current));
        assertThat(service.verifyRefreshToken("current")).contains(current);

        RefreshToken ineligible = token(2L, "user", false, false);
        ineligible.setTokenHash("already-hashed");
        when(repository.findByTokenHash("hash-ineligible")).thenReturn(Optional.empty());
        when(repository.findByToken("ineligible")).thenReturn(Optional.of(ineligible));
        assertThat(service.verifyRefreshToken("ineligible")).isEmpty();

        RefreshToken legacy = token(3L, "user", false, false);
        legacy.setTokenHash(null);
        when(repository.findByTokenHash("hash-legacy")).thenReturn(null);
        when(repository.findByToken("legacy")).thenReturn(Optional.of(legacy));
        when(repository.save(legacy)).thenReturn(null);
        assertThat(service.verifyRefreshToken("legacy")).contains(legacy);
        assertThat(legacy.getTokenHash()).isEqualTo("hash-legacy");
        assertThat(legacy.getToken()).isEqualTo("legacy");
    }

    @Test
    void revokeRejectsMissingTokensAndMigratesLegacyOnSuccess() {
        assertThat(service.revokeRefreshToken(null)).isFalse();
        assertThat(service.revokeRefreshToken(" ")).isFalse();

        RefreshToken legacy = token(3L, "user", false, false);
        legacy.setTokenHash(null);
        when(repository.findByTokenHash("hash-legacy")).thenReturn(Optional.empty());
        when(repository.findByToken("legacy")).thenReturn(Optional.of(legacy));
        when(repository.save(legacy)).thenReturn(legacy);
        assertThat(service.revokeRefreshToken("legacy")).isTrue();
        assertThat(legacy.getIsRevoked()).isTrue();
    }

    @Test
    void listsTokensWithNamesFallbacksPreviewsAndExpiryFiltering() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        Admin admin = new Admin();
        admin.setId(2L);
        admin.setUsername("root");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(admins.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findById(4L)).thenReturn(Optional.empty());
        when(admins.findById(5L)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("db")).when(users).findById(6L);

        RefreshToken userToken = token(1L, "user", false, false);
        userToken.setId(11L);
        userToken.setTokenPreview("stored-preview");
        RefreshToken adminToken = token(2L, "admin", false, false);
        adminToken.setId(12L);
        adminToken.setTokenPreview("");
        adminToken.setToken("stored-token");
        RefreshToken other = token(3L, "service", false, false);
        RefreshToken missingUser = token(4L, "user", false, false);
        RefreshToken missingAdmin = token(5L, "admin", false, false);
        RefreshToken failedLookup = token(6L, "user", false, false);
        when(repository.findWithFilters(null, null, null, null))
                .thenReturn(List.of(userToken, adminToken, other, missingUser, missingAdmin, failedLookup));

        var responses = service.getFilteredTokens(null, null, null, null);

        assertThat(responses).extracting("username")
                .containsExactly("alice", "root", "Unknown #3", "Unknown User #4", "Unknown Admin #5", "Unknown #6");
        assertThat(responses.get(0).getTokenPreview()).isEqualTo("stored-preview");
        assertThat(responses.get(1).getTokenPreview()).isEqualTo("preview");

        RefreshToken expired = token(1L, "user", false, true);
        when(repository.findByUserIdAndRoleAndIsRevokedFalse(1L, "user")).thenReturn(List.of(userToken, expired));
        assertThat(service.getActiveTokensForUser(1L, "user")).hasSize(1);
    }

    @Test
    void getsRevokesAndDeletesTokensById() {
        RefreshToken token = token(1L, "user", false, false);
        token.setId(9L);
        when(users.findById(1L)).thenReturn(Optional.empty());
        when(repository.findById(9L)).thenReturn(Optional.of(token));
        when(repository.findById(10L)).thenReturn(Optional.empty());
        assertThat(service.getTokenById(9L)).isPresent();
        assertThat(service.getTokenById(10L)).isEmpty();
        assertThat(service.revokeTokenById(9L)).isTrue();
        assertThat(service.revokeTokenById(10L)).isFalse();

        when(repository.existsById(9L)).thenReturn(true);
        when(repository.existsById(10L)).thenReturn(false);
        assertThat(service.deleteTokenById(9L)).isTrue();
        assertThat(service.deleteTokenById(10L)).isFalse();
        verify(repository).deleteById(9L);
    }

    @Test
    void tokenStatisticsSeparateKnownRolesAndRetainOtherRoles() {
        when(repository.countAllActiveTokens(any())).thenReturn(8L);
        when(repository.count()).thenReturn(12L);
        when(repository.countActiveTokensByRole(any())).thenReturn(List.of(
                new Object[]{"USER", 5L},
                new Object[]{"admin", 2L},
                new Object[]{"service", 1L}));

        assertThat(service.getTokenStatistics())
                .containsEntry("totalActiveTokens", 8L)
                .containsEntry("totalTokens", 12L)
                .containsEntry("userTokens", 5L)
                .containsEntry("adminTokens", 2L);
    }

    private RefreshToken token(Long userId, String role, boolean revoked, boolean expired) {
        RefreshToken token = new RefreshToken(userId, role, 30L);
        token.setIssuedAuthVersion(0L);
        token.setIsRevoked(revoked);
        token.setExpiryDate(expired ? LocalDateTime.now().minusMinutes(1) : LocalDateTime.now().plusMinutes(5));
        return token;
    }

    @Test void tokenCreationCapturesCurrentAccountVersionAndLogoutInvalidatesBothRoles() {
        User user = new User(); user.setId(1L); user.setAuthVersion(4L);
        Admin admin = new Admin(); admin.setId(2L); admin.setAuthVersion(8L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(admins.findById(2L)).thenReturn(Optional.of(admin));
        when(hashes.generateToken()).thenReturn("token");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createRefreshToken(1L, "user", request).getIssuedAuthVersion()).isEqualTo(4L);
        assertThat(service.createRefreshToken(2L, "admin", request).getIssuedAuthVersion()).isEqualTo(8L);
        service.revokeAllSessions(1L, "user"); service.revokeAllSessions(2L, "admin");
        assertThat(user.currentAuthVersion()).isEqualTo(5L); assertThat(admin.currentAuthVersion()).isEqualTo(9L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.revokeAllSessions(1L, "other")).isInstanceOf(IllegalArgumentException.class);
        RefreshToken legacy = new RefreshToken(1L, "user", 30L);
        assertThat(service.rotateRefreshToken(legacy, request)).isEmpty();
        verify(repository, never()).revokeIfActive(any(), any());
    }

}
