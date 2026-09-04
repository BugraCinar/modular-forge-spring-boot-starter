package dev.modularforge.audit;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import dev.modularforge.audit.SensitiveEndpointAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SensitiveEndpointAccessLogRepository extends JpaRepository<SensitiveEndpointAccessLog, Long> {
    Page<SensitiveEndpointAccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SensitiveEndpointAccessLog> findBySeverityOrderByCreatedAtDesc(
            SensitiveEndpointAccessLog.SeverityLevel severity, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByEndpointCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByEndpointContainingOrderByCreatedAtDesc(String endpointPattern, Pageable pageable);
    @Query("SELECT s FROM SensitiveEndpointAccessLog s WHERE s.createdAt BETWEEN :startDate AND :endDate ORDER BY s.createdAt DESC")
    Page<SensitiveEndpointAccessLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    @Query("SELECT COUNT(s) FROM SensitiveEndpointAccessLog s WHERE s.ipAddress = :ipAddress AND s.createdAt >= :since")
    Long countByIpAddressSince(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
    @Query("SELECT COUNT(s) FROM SensitiveEndpointAccessLog s WHERE s.userId = :userId AND s.createdAt >= :since")
    Long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    @Query("SELECT s.severity, COUNT(s) FROM SensitiveEndpointAccessLog s GROUP BY s.severity")
    List<Object[]> getStatisticsBySeverity();
    @Query("SELECT s.endpointCategory, COUNT(s) FROM SensitiveEndpointAccessLog s GROUP BY s.endpointCategory")
    List<Object[]> getStatisticsByCategory();
    @Query("SELECT DATE(s.createdAt) as date, COUNT(s) as count FROM SensitiveEndpointAccessLog s " +
           "WHERE s.createdAt >= :since GROUP BY DATE(s.createdAt) ORDER BY date DESC")
    List<Object[]> getDailyStatistics(@Param("since") LocalDateTime since);
    long count();
    long countBySeverity(SensitiveEndpointAccessLog.SeverityLevel severity);
    long countByEndpointCategory(String category);
    @Query("SELECT s FROM SensitiveEndpointAccessLog s WHERE s.severity = 'CRITICAL' AND s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<SensitiveEndpointAccessLog> findRecentCriticalLogs(@Param("since") LocalDateTime since);
    List<SensitiveEndpointAccessLog> findByEmailAlertSentFalseOrderByCreatedAtDesc();
}
