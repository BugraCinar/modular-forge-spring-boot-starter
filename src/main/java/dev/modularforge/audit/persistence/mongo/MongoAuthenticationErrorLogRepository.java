package dev.modularforge.audit.persistence.mongo;
import dev.modularforge.audit.*;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import dev.modularforge.shared.persistence.mongo.CountBucket;


import dev.modularforge.audit.AuthenticationErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.context.annotation.Profile("mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.audit", name="enabled", havingValue="true", matchIfMissing=true)
public interface MongoAuthenticationErrorLogRepository extends AuthenticationErrorLogRepository, org.springframework.data.mongodb.repository.MongoRepository<AuthenticationErrorLog, Long> {
    Page<AuthenticationErrorLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuthenticationErrorLog> findByErrorTypeOrderByCreatedAtDesc(AuthenticationErrorLog.ErrorType errorType, Pageable pageable);
    Page<AuthenticationErrorLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AuthenticationErrorLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<AuthenticationErrorLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    @Query(value="{'createdAt':{'$gte':?0,'$lte':?1}}", sort="{'createdAt':-1}")
    Page<AuthenticationErrorLog> findByDateRange(
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );
    Page<AuthenticationErrorLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    @Query(value="{'ipAddress':?0,'createdAt':{'$gte':?1}}", count=true)
    Long countByIpAddressSince(String ipAddress, LocalDateTime since);
    @Query(value="{'userId':?0,'createdAt':{'$gte':?1}}", count=true)
    Long countByUserIdSince(Long userId, LocalDateTime since);
    @Query(value="{'ipAddress':?0}", sort="{'createdAt':-1}")
    List<AuthenticationErrorLog> findRecentByIpAddress(String ipAddress, Pageable pageable);
    @Aggregation(pipeline={"{'$group':{'_id':'$errorType','count':{'$sum':1}}}"})
    java.util.List<CountBucket> getStatisticsByErrorTypeBuckets();
    @Override
    default java.util.List<Object[]> getStatisticsByErrorType() {
        return getStatisticsByErrorTypeBuckets().stream().map(bucket -> new Object[]{AuthenticationErrorLog.ErrorType.valueOf(bucket.key()), bucket.count()}).toList();
    }
    @Aggregation(pipeline={"{'$match':{'createdAt':{'$gte':?0}}}","{'$group':{'_id':{'$dateToString':{'format':'%Y-%m-%d','date':'$createdAt','timezone':'UTC'}},'count':{'$sum':1}}}","{'$sort':{'_id':-1}}"})
    java.util.List<CountBucket> getDailyStatisticsBuckets(LocalDateTime since);
    @Override
    default java.util.List<Object[]> getDailyStatistics(LocalDateTime since) {
        return getDailyStatisticsBuckets(since).stream().map(bucket -> new Object[]{java.time.LocalDate.parse(bucket.key()), bucket.count()}).toList();
    }
    long count();
    long countByErrorType(AuthenticationErrorLog.ErrorType errorType);
}
