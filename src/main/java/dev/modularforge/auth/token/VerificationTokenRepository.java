package dev.modularforge.auth.token;


import dev.modularforge.auth.token.VerificationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

@org.springframework.data.repository.NoRepositoryBean
public interface VerificationTokenRepository extends dev.modularforge.shared.persistence.EntityRepository<VerificationToken> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByTokenHash(String tokenHash);
    Optional<VerificationToken> findByUserIdAndRole(Long userId, String role);
    Optional<VerificationToken> findFirstByRoleOrderByCreatedDateDesc(String role);
    void deleteByUserIdAndRole(Long userId, String role);
    Page<VerificationToken> findAllByOrderByCreatedDateDesc(Pageable pageable);
    Page<VerificationToken> findByRoleOrderByCreatedDateDesc(String role, Pageable pageable);
    Page<VerificationToken> findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime dateTime, Pageable pageable);
}
