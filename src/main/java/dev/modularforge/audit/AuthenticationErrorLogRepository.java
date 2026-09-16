package dev.modularforge.audit;


import dev.modularforge.audit.AuthenticationErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.data.repository.NoRepositoryBean
public interface AuthenticationErrorLogRepository extends dev.modularforge.shared.persistence.EntityRepository<AuthenticationErrorLog> {
    Page<AuthenticationErrorLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuthenticationErrorLog> findByErrorTypeOrderByCreatedAtDesc(AuthenticationErrorLog.ErrorType errorType, Pageable pageable);
    Page<AuthenticationErrorLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AuthenticationErrorLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<AuthenticationErrorLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    Page<AuthenticationErrorLog> findByDateRange(
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );
    Page<AuthenticationErrorLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    Long countByIpAddressSince(String ipAddress, LocalDateTime since);
    Long countByUserIdSince(Long userId, LocalDateTime since);
    List<AuthenticationErrorLog> findRecentByIpAddress(String ipAddress, Pageable pageable);
    List<Object[]> getStatisticsByErrorType();
    List<Object[]> getDailyStatistics(LocalDateTime since);
    long count();
    long countByErrorType(AuthenticationErrorLog.ErrorType errorType);
}
