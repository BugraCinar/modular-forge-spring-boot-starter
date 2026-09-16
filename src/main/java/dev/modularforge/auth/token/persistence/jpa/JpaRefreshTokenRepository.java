package dev.modularforge.auth.token.persistence.jpa;

import dev.modularforge.auth.token.*;
import dev.modularforge.auth.token.RefreshTokenRepository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.modularforge.auth.token.RefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@org.springframework.context.annotation.Profile("!mongodb")
public interface JpaRefreshTokenRepository extends RefreshTokenRepository, JpaRepository<RefreshToken, Long> {
    @Override <S extends RefreshToken> S saveAndFlush(S entity);
    @Override void flush();

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRole(Long userId, String role);

    List<RefreshToken> findByUserIdAndRoleAndIsRevokedFalse(Long userId, String role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteByExpiryDateBefore(LocalDateTime date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId AND rt.role = :role AND rt.isRevoked = false")
    int revokeAllUserTokens(@Param("userId") Long userId, @Param("role") String role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true, rt.lastUsedAt = :usedAt " +
            "WHERE rt.id = :id AND rt.isRevoked = false")
    int revokeIfActive(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < :date")
    int cleanupRevokedAndExpired(@Param("date") LocalDateTime date);

    long countByUserIdAndRole(Long userId, String role);

    long countByIsRevokedFalseAndExpiryDateAfter(LocalDateTime now);
    List<RefreshToken> findByRole(String role);

    List<RefreshToken> findByIsRevoked(Boolean isRevoked);

    List<RefreshToken> findByIpAddress(String ipAddress);

    @Query("SELECT rt FROM RefreshToken rt WHERE " +
            "(:role IS NULL OR rt.role = :role) AND " +
            "(:userId IS NULL OR rt.userId = :userId) AND " +
            "(:isRevoked IS NULL OR rt.isRevoked = :isRevoked) AND " +
            "(:ipAddress IS NULL OR rt.ipAddress = :ipAddress) " +
            "ORDER BY rt.createdAt DESC")
    List<RefreshToken> findWithFilters(
            @Param("role") String role,
            @Param("userId") Long userId,
            @Param("isRevoked") Boolean isRevoked,
            @Param("ipAddress") String ipAddress);

    @Query("SELECT rt.role, COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = false AND rt.expiryDate > :now GROUP BY rt.role")
    List<Object[]> countActiveTokensByRole(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.isRevoked = false AND rt.expiryDate > :now")
    long countAllActiveTokens(@Param("now") LocalDateTime now);
}
