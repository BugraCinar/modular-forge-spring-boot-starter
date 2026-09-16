package dev.modularforge.audit.persistence.mongo;
import dev.modularforge.audit.*;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import dev.modularforge.shared.persistence.mongo.CountBucket;


import dev.modularforge.audit.SensitiveEndpointAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.context.annotation.Profile("mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.audit", name="enabled", havingValue="true", matchIfMissing=true)
public interface MongoSensitiveEndpointAccessLogRepository extends SensitiveEndpointAccessLogRepository, org.springframework.data.mongodb.repository.MongoRepository<SensitiveEndpointAccessLog, Long> {
    Page<SensitiveEndpointAccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SensitiveEndpointAccessLog> findBySeverityOrderByCreatedAtDesc(
            SensitiveEndpointAccessLog.SeverityLevel severity, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByEndpointCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByEndpointContainingOrderByCreatedAtDesc(String endpointPattern, Pageable pageable);
    @Query(value="{'createdAt':{'$gte':?0,'$lte':?1}}", sort="{'createdAt':-1}")
    Page<SensitiveEndpointAccessLog> findByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    @Query(value="{'ipAddress':?0,'createdAt':{'$gte':?1}}", count=true)
    Long countByIpAddressSince(String ipAddress, LocalDateTime since);
    @Query(value="{'userId':?0,'createdAt':{'$gte':?1}}", count=true)
    Long countByUserIdSince(Long userId, LocalDateTime since);
    @Aggregation(pipeline={"{'$group':{'_id':'$severity','count':{'$sum':1}}}"})
    java.util.List<CountBucket> getStatisticsBySeverityBuckets();
    @Override
    default java.util.List<Object[]> getStatisticsBySeverity() {
        return getStatisticsBySeverityBuckets().stream().map(bucket -> new Object[]{SensitiveEndpointAccessLog.SeverityLevel.valueOf(bucket.key()), bucket.count()}).toList();
    }
    @Aggregation(pipeline={"{'$group':{'_id':'$endpointCategory','count':{'$sum':1}}}"})
    java.util.List<CountBucket> getStatisticsByCategoryBuckets();
    @Override
    default java.util.List<Object[]> getStatisticsByCategory() {
        return getStatisticsByCategoryBuckets().stream().map(bucket -> new Object[]{bucket.key(), bucket.count()}).toList();
    }
    @Aggregation(pipeline={"{'$match':{'createdAt':{'$gte':?0}}}","{'$group':{'_id':{'$dateToString':{'format':'%Y-%m-%d','date':'$createdAt','timezone':'UTC'}},'count':{'$sum':1}}}","{'$sort':{'_id':-1}}"})
    java.util.List<CountBucket> getDailyStatisticsBuckets(LocalDateTime since);
    @Override
    default java.util.List<Object[]> getDailyStatistics(LocalDateTime since) {
        return getDailyStatisticsBuckets(since).stream().map(bucket -> new Object[]{java.time.LocalDate.parse(bucket.key()), bucket.count()}).toList();
    }
    long count();
    long countBySeverity(SensitiveEndpointAccessLog.SeverityLevel severity);
    long countByEndpointCategory(String category);
    @Query(value="{'severity':'CRITICAL','createdAt':{'$gte':?0}}", sort="{'createdAt':-1}")
    List<SensitiveEndpointAccessLog> findRecentCriticalLogs(LocalDateTime since);
    List<SensitiveEndpointAccessLog> findByEmailAlertSentFalseOrderByCreatedAtDesc();
}
