package dev.modularforge.auth.token.persistence.mongo;
import dev.modularforge.auth.token.*;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.mongodb.repository.Aggregation;
import dev.modularforge.shared.persistence.mongo.CountBucket;


import org.springframework.transaction.annotation.Transactional;

import dev.modularforge.auth.token.RefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@org.springframework.context.annotation.Profile("mongodb")
public interface MongoRefreshTokenRepository extends RefreshTokenRepository, org.springframework.data.mongodb.repository.MongoRepository<RefreshToken, Long> {

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
    @Query(value="{'userId':?0,'role':?1,'isRevoked':false}")
    @Update("{'$set':{'isRevoked':true}}")
    int revokeAllUserTokens(Long userId, String role);

    @Transactional
    @Query(value="{'id':?0,'isRevoked':false}")
    @Update("{'$set':{'isRevoked':true,'lastUsedAt':?1}}")
    int revokeIfActive(Long id, LocalDateTime usedAt);

    @Transactional
    @Query(value="{'expiryDate':{'$lt':?0}}", delete=true)
    int cleanupRevokedAndExpired(LocalDateTime date);

    long countByUserIdAndRole(Long userId, String role);

    long countByIsRevokedFalseAndExpiryDateAfter(LocalDateTime now);
    List<RefreshToken> findByRole(String role);

    List<RefreshToken> findByIsRevoked(Boolean isRevoked);

    List<RefreshToken> findByIpAddress(String ipAddress);

    @Query(value="?#{T(dev.modularforge.shared.persistence.mongo.MongoFilters).filter('role',[0],'userId',[1],'isRevoked',[2],'ipAddress',[3])}", sort="{'createdAt':-1}")
    List<RefreshToken> findWithFilters(
            String role,
            Long userId,
            Boolean isRevoked,
            String ipAddress);

    @Aggregation(pipeline={"{'$match':{'isRevoked':false,'expiryDate':{'$gt':?0}}}","{'$group':{'_id':'$role','count':{'$sum':1}}}"})
    java.util.List<CountBucket> countActiveTokensByRoleBuckets(LocalDateTime now);
    @Override
    default java.util.List<Object[]> countActiveTokensByRole(LocalDateTime now) {
        return countActiveTokensByRoleBuckets(now).stream().map(bucket -> new Object[]{bucket.key(), bucket.count()}).toList();
    }

    @Query(value="{'isRevoked':false,'expiryDate':{'$gt':?0}}", count=true)
    long countAllActiveTokens(LocalDateTime now);
}
