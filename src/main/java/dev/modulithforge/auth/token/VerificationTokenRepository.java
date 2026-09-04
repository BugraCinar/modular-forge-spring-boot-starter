package dev.modulithforge.auth.token;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;

import dev.modulithforge.auth.token.VerificationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByTokenHash(String tokenHash);
    Optional<VerificationToken> findByUserIdAndRole(Long userId, String role);
    Optional<VerificationToken> findFirstByRoleOrderByCreatedDateDesc(String role);
    void deleteByUserIdAndRole(Long userId, String role);
    Page<VerificationToken> findAllByOrderByCreatedDateDesc(Pageable pageable);
    Page<VerificationToken> findByRoleOrderByCreatedDateDesc(String role, Pageable pageable);
    Page<VerificationToken> findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime dateTime, Pageable pageable);
}
