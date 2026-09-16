package dev.modularforge.twofactor.persistence.jpa;

import dev.modularforge.twofactor.*;
import dev.modularforge.twofactor.TwoFactorCredentialRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@org.springframework.context.annotation.Profile("!mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.two-factor", name="enabled", havingValue="true", matchIfMissing=false)
public interface JpaTwoFactorCredentialRepository extends TwoFactorCredentialRepository, JpaRepository<TwoFactorCredential, Long> {
    @Override <S extends TwoFactorCredential> S saveAndFlush(S entity);
    @Override void flush();

    Optional<TwoFactorCredential> findByAdminId(Long adminId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from TwoFactorCredential credential where credential.adminId = :adminId")
    Optional<TwoFactorCredential> findForUpdateByAdminId(@Param("adminId") Long adminId);

    void deleteByAdminId(Long adminId);
}
