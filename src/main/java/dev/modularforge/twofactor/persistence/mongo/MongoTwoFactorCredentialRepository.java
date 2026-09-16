package dev.modularforge.twofactor.persistence.mongo;
import dev.modularforge.twofactor.*;
import org.springframework.data.mongodb.repository.Query;



import java.util.Optional;

@org.springframework.context.annotation.Profile("mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.two-factor", name="enabled", havingValue="true", matchIfMissing=false)
public interface MongoTwoFactorCredentialRepository extends TwoFactorCredentialRepository, org.springframework.data.mongodb.repository.MongoRepository<TwoFactorCredential, Long> {

    Optional<TwoFactorCredential> findByAdminId(Long adminId);

    @Query(value="{'adminId':?0}")
    Optional<TwoFactorCredential> findForUpdateByAdminId(Long adminId);

    void deleteByAdminId(Long adminId);
}
