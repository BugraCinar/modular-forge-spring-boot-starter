package dev.modularforge.audit;

import dev.modularforge.audit.AdminActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.data.repository.NoRepositoryBean
public interface AdminActivityLogRepository extends dev.modularforge.shared.persistence.EntityRepository<AdminActivityLog> {

    List<AdminActivityLog> findByAdminIdOrderByCreatedAtDesc(Long adminId);

    Page<AdminActivityLog> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    List<AdminActivityLog> findByActionOrderByCreatedAtDesc(String action);

    List<AdminActivityLog> findByResourceTypeOrderByCreatedAtDesc(String resourceType);

    List<AdminActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime date);

    List<AdminActivityLog> findByAdminIdAndCreatedAtAfterOrderByCreatedAtDesc(Long adminId, LocalDateTime date);

    long countByAdminIdAndCreatedAtAfter(Long adminId, LocalDateTime date);

    Page<AdminActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}