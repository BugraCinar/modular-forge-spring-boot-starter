package dev.modularforge.audit;


import dev.modularforge.audit.SensitiveEndpointAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.data.repository.NoRepositoryBean
public interface SensitiveEndpointAccessLogRepository extends dev.modularforge.shared.persistence.EntityRepository<SensitiveEndpointAccessLog> {
    Page<SensitiveEndpointAccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SensitiveEndpointAccessLog> findBySeverityOrderByCreatedAtDesc(
            SensitiveEndpointAccessLog.SeverityLevel severity, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByEndpointCategoryOrderByCreatedAtDesc(String category, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByEndpointContainingOrderByCreatedAtDesc(String endpointPattern, Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
    Page<SensitiveEndpointAccessLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    Long countByIpAddressSince(String ipAddress, LocalDateTime since);
    Long countByUserIdSince(Long userId, LocalDateTime since);
    List<Object[]> getStatisticsBySeverity();
    List<Object[]> getStatisticsByCategory();
    List<Object[]> getDailyStatistics(LocalDateTime since);
    long count();
    long countBySeverity(SensitiveEndpointAccessLog.SeverityLevel severity);
    long countByEndpointCategory(String category);
    List<SensitiveEndpointAccessLog> findRecentCriticalLogs(LocalDateTime since);
    List<SensitiveEndpointAccessLog> findByEmailAlertSentFalseOrderByCreatedAtDesc();
}
