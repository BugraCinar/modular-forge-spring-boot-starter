package dev.modulithforge.audit;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.audit.AuthenticationErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuthenticationErrorLogRepository extends JpaRepository<AuthenticationErrorLog, Long> {
    Page<AuthenticationErrorLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuthenticationErrorLog> findByErrorTypeOrderByCreatedAtDesc(AuthenticationErrorLog.ErrorType errorType, Pageable pageable);
    Page<AuthenticationErrorLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<AuthenticationErrorLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<AuthenticationErrorLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    @Query("SELECT a FROM AuthenticationErrorLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    Page<AuthenticationErrorLog> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );
    Page<AuthenticationErrorLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime afterDate, Pageable pageable);
    @Query("SELECT COUNT(a) FROM AuthenticationErrorLog a WHERE a.ipAddress = :ipAddress AND a.createdAt >= :since")
    Long countByIpAddressSince(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
    @Query("SELECT COUNT(a) FROM AuthenticationErrorLog a WHERE a.userId = :userId AND a.createdAt >= :since")
    Long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    @Query("SELECT a FROM AuthenticationErrorLog a WHERE a.ipAddress = :ipAddress ORDER BY a.createdAt DESC")
    List<AuthenticationErrorLog> findRecentByIpAddress(@Param("ipAddress") String ipAddress, Pageable pageable);
    @Query("SELECT a.errorType, COUNT(a) FROM AuthenticationErrorLog a GROUP BY a.errorType")
    List<Object[]> getStatisticsByErrorType();
    @Query("SELECT DATE(a.createdAt) as date, COUNT(a) as count FROM AuthenticationErrorLog a " +
           "WHERE a.createdAt >= :since GROUP BY DATE(a.createdAt) ORDER BY date DESC")
    List<Object[]> getDailyStatistics(@Param("since") LocalDateTime since);
    long count();
    long countByErrorType(AuthenticationErrorLog.ErrorType errorType);
}
