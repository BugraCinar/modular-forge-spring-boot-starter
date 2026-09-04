package dev.modulithforge.twofactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

interface TwoFactorCredentialRepository extends JpaRepository<TwoFactorCredential, Long> {

    Optional<TwoFactorCredential> findByAdminId(Long adminId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from TwoFactorCredential credential where credential.adminId = :adminId")
    Optional<TwoFactorCredential> findForUpdateByAdminId(@Param("adminId") Long adminId);

    void deleteByAdminId(Long adminId);
}
