package dev.modularforge.audit.persistence.jpa;

import dev.modularforge.audit.*;
import dev.modularforge.audit.UserActivityLogRepository;


import dev.modularforge.audit.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@org.springframework.context.annotation.Profile("!mongodb")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.modules.audit", name="enabled", havingValue="true", matchIfMissing=true)
public interface JpaUserActivityLogRepository extends UserActivityLogRepository, JpaRepository<UserActivityLog, Long>, JpaSpecificationExecutor<UserActivityLog> {
    @Override <S extends UserActivityLog> S saveAndFlush(S entity);
    @Override void flush();
    @Query("SELECT u FROM UserActivityLog u WHERE (:userId IS NULL OR u.userId = :userId) " +
            "AND (:role IS NULL OR u.role = :role) AND (:action IS NULL OR u.action = :action) " +
            "AND (:resourceType IS NULL OR u.resourceType = :resourceType) " +
            "AND (:success IS NULL OR u.success = :success) " +
            "AND (:startDate IS NULL OR u.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR u.createdAt <= :endDate) " +
            "AND (:ipAddress IS NULL OR u.ipAddress = :ipAddress)")
    Page<UserActivityLog> findWithFilters(Long userId, String role, String action, String resourceType,
                                        Boolean success, LocalDateTime startDate, LocalDateTime endDate,
                                        String ipAddress, Pageable pageable);
    Page<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);

    List<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role);
    Page<UserActivityLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<UserActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    Page<UserActivityLog> findByActionAndRoleOrderByCreatedAtDesc(String action, String role, Pageable pageable);
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.role = :role AND ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("role") String role,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );
    @Query("SELECT ual FROM UserActivityLog ual WHERE ual.userId = :userId AND ual.role = :role AND ual.createdAt >= :startDate ORDER BY ual.createdAt DESC")
    Page<UserActivityLog> findByUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("startDate") LocalDateTime startDate,
            Pageable pageable
    );
    @Query("SELECT COUNT(ual) FROM UserActivityLog ual WHERE ual.userId = :userId AND ual.role = :role AND ual.createdAt >= :date")
    long countByUserIdAndRoleAndCreatedAtAfter(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("date") LocalDateTime date
    );
    Page<UserActivityLog> findByUserIdAndRoleAndSuccessFalseOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);
    Page<UserActivityLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    @Query("SELECT COUNT(ual) FROM UserActivityLog ual WHERE ual.action = :action AND ual.createdAt >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") LocalDateTime since);
    long countBySuccessAndCreatedAtAfter(boolean success, LocalDateTime date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserActivityLog u WHERE u.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);
}
