package dev.modularforge.audit.persistence.mongo;
import dev.modularforge.audit.*;
import org.springframework.data.mongodb.repository.Query;


import dev.modularforge.audit.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.context.annotation.Profile("mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.audit", name="enabled", havingValue="true", matchIfMissing=true)
public interface MongoUserActivityLogRepository extends UserActivityLogRepository, org.springframework.data.mongodb.repository.MongoRepository<UserActivityLog, Long> {
    @Query(value="?#{T(dev.modularforge.shared.persistence.mongo.MongoFilters).activity([0],[1],[2],[3],[4],[5],[6],[7])}")
    Page<UserActivityLog> findWithFilters(Long userId, String role, String action, String resourceType,
                                        Boolean success, LocalDateTime startDate, LocalDateTime endDate,
                                        String ipAddress, Pageable pageable);
    Page<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);

    List<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role);
    Page<UserActivityLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<UserActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    Page<UserActivityLog> findByActionAndRoleOrderByCreatedAtDesc(String action, String role, Pageable pageable);
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @Query(value="{'createdAt':{'$gte':?0}}", sort="{'createdAt':-1}")
    Page<UserActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime startDate, Pageable pageable);
    @Query(value="{'role':?0,'createdAt':{'$gte':?1}}", sort="{'createdAt':-1}")
    Page<UserActivityLog> findByRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            String role,
            LocalDateTime startDate,
            Pageable pageable
    );
    @Query(value="{'userId':?0,'role':?1,'createdAt':{'$gte':?2}}", sort="{'createdAt':-1}")
    Page<UserActivityLog> findByUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            String role,
            LocalDateTime startDate,
            Pageable pageable
    );
    @Query(value="{'userId':?0,'role':?1,'createdAt':{'$gte':?2}}", count=true)
    long countByUserIdAndRoleAndCreatedAtAfter(
            Long userId,
            String role,
            LocalDateTime date
    );
    Page<UserActivityLog> findByUserIdAndRoleAndSuccessFalseOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);
    Page<UserActivityLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    @Query(value="{'action':?0,'createdAt':{'$gte':?1}}", count=true)
    long countByActionSince(String action, LocalDateTime since);
    long countBySuccessAndCreatedAtAfter(boolean success, LocalDateTime date);

    int deleteByCreatedAtBefore(LocalDateTime before);
}
