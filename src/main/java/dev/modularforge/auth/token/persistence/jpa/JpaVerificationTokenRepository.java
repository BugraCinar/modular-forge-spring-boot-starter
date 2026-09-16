package dev.modularforge.auth.token.persistence.jpa;

import dev.modularforge.auth.token.*;
import dev.modularforge.auth.token.VerificationTokenRepository;


import dev.modularforge.auth.token.VerificationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@org.springframework.context.annotation.Profile("!mongodb")
public interface JpaVerificationTokenRepository extends VerificationTokenRepository, JpaRepository<VerificationToken, Long> {
    @Override <S extends VerificationToken> S saveAndFlush(S entity);
    @Override void flush();
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByTokenHash(String tokenHash);
    Optional<VerificationToken> findByUserIdAndRole(Long userId, String role);
    Optional<VerificationToken> findFirstByRoleOrderByCreatedDateDesc(String role);
    void deleteByUserIdAndRole(Long userId, String role);
    Page<VerificationToken> findAllByOrderByCreatedDateDesc(Pageable pageable);
    Page<VerificationToken> findByRoleOrderByCreatedDateDesc(String role, Pageable pageable);
    Page<VerificationToken> findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime dateTime, Pageable pageable);
}
