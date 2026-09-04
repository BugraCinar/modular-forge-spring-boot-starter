package dev.modulithforge.auth.token;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;

import dev.modulithforge.auth.token.PasswordResetToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    List<PasswordResetToken> findByUserIdAndRole(Long userId, String role);
    void deleteByExpiryDateBefore(LocalDateTime dateTime);
    void deleteByUserIdAndRole(Long userId, String role);
    Optional<PasswordResetToken> findFirstByRequestingIpOrderByCreatedDateDesc(String ipAddress);
    Optional<PasswordResetToken> findFirstByOrderByCreatedDateDesc();
    Page<PasswordResetToken> findAllByOrderByCreatedDateDesc(Pageable pageable);
    Page<PasswordResetToken> findByRoleOrderByCreatedDateDesc(String role, Pageable pageable);
    Page<PasswordResetToken> findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime dateTime, Pageable pageable);
}
