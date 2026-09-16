package dev.modularforge.auth.token;


import org.springframework.transaction.annotation.Transactional;

import dev.modularforge.auth.token.RefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@org.springframework.data.repository.NoRepositoryBean
public interface RefreshTokenRepository extends dev.modularforge.shared.persistence.EntityRepository<RefreshToken> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRole(Long userId, String role);

    List<RefreshToken> findByUserIdAndRoleAndIsRevokedFalse(Long userId, String role);

    @Transactional
    void deleteByToken(String token);

    @Transactional
    void deleteByTokenHash(String tokenHash);

    @Transactional
    void deleteByExpiryDateBefore(LocalDateTime date);

    @Transactional
    int revokeAllUserTokens(Long userId, String role);

    @Transactional
    int revokeIfActive(Long id, LocalDateTime usedAt);

    @Transactional
    int cleanupRevokedAndExpired(LocalDateTime date);

    long countByUserIdAndRole(Long userId, String role);

    long countByIsRevokedFalseAndExpiryDateAfter(LocalDateTime now);
    List<RefreshToken> findByRole(String role);

    List<RefreshToken> findByIsRevoked(Boolean isRevoked);

    List<RefreshToken> findByIpAddress(String ipAddress);

    List<RefreshToken> findWithFilters(
            String role,
            Long userId,
            Boolean isRevoked,
            String ipAddress);

    List<Object[]> countActiveTokensByRole(LocalDateTime now);

    long countAllActiveTokens(LocalDateTime now);
}
