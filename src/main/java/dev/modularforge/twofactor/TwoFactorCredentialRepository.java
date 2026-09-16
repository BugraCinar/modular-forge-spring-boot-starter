package dev.modularforge.twofactor;



import java.util.Optional;

@org.springframework.data.repository.NoRepositoryBean
public interface TwoFactorCredentialRepository extends dev.modularforge.shared.persistence.EntityRepository<TwoFactorCredential> {

    Optional<TwoFactorCredential> findByAdminId(Long adminId);

    Optional<TwoFactorCredential> findForUpdateByAdminId(Long adminId);

    void deleteByAdminId(Long adminId);
}
