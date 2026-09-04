package dev.modularforge.audit;

import dev.modularforge.audit.AdminActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long> {

    List<AdminActivityLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    Page<AdminActivityLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    List<AdminActivityLog> findByActionOrderByCreatedAtDesc(String action);

    List<AdminActivityLog> findByResourceTypeOrderByCreatedAtDesc(String resourceType);

    @Query("SELECT aal FROM AdminActivityLog aal WHERE aal.createdAt >= :date ORDER BY aal.createdAt DESC")
    List<AdminActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(@Param("date") LocalDateTime date);

    @Query("SELECT aal FROM AdminActivityLog aal WHERE aal.adminId = :adminId AND aal.createdAt >= :date ORDER BY aal.createdAt DESC")
    List<AdminActivityLog> findByAdminIdAndCreatedAtAfterOrderByCreatedAtDesc(@Param("adminId") Long adminId, @Param("date") LocalDateTime date);

    @Query("SELECT COUNT(aal) FROM AdminActivityLog aal WHERE aal.adminId = :adminId AND aal.createdAt >= :date")
    long countByAdminIdAndCreatedAtAfter(@Param("adminId") Long adminId, @Param("date") LocalDateTime date);

    Page<AdminActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}