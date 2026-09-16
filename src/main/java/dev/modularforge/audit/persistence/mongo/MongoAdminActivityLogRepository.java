package dev.modularforge.audit.persistence.mongo;
import dev.modularforge.audit.*;
import org.springframework.data.mongodb.repository.Query;

import dev.modularforge.audit.AdminActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.context.annotation.Profile("mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.audit", name="enabled", havingValue="true", matchIfMissing=true)
public interface MongoAdminActivityLogRepository extends AdminActivityLogRepository, org.springframework.data.mongodb.repository.MongoRepository<AdminActivityLog, Long> {

    List<AdminActivityLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    Page<AdminActivityLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    List<AdminActivityLog> findByActionOrderByCreatedAtDesc(String action);

    List<AdminActivityLog> findByResourceTypeOrderByCreatedAtDesc(String resourceType);

    @Query(value="{'createdAt':{'$gte':?0}}", sort="{'createdAt':-1}")
    List<AdminActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime date);

    @Query(value="{'adminId':?0,'createdAt':{'$gte':?1}}", sort="{'createdAt':-1}")
    List<AdminActivityLog> findByAdminIdAndCreatedAtAfterOrderByCreatedAtDesc(Long adminId, LocalDateTime date);

    @Query(value="{'adminId':?0,'createdAt':{'$gte':?1}}", count=true)
    long countByAdminIdAndCreatedAtAfter(Long adminId, LocalDateTime date);

    Page<AdminActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}