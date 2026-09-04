package dev.modulithforge.audit;

import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.audit.UserActivityLog;
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
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long>, JpaSpecificationExecutor<UserActivityLog> {
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
