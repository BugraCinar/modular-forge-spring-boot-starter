package dev.modularforge.audit;


import dev.modularforge.audit.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@org.springframework.data.repository.NoRepositoryBean
public interface UserActivityLogRepository extends dev.modularforge.shared.persistence.EntityRepository<UserActivityLog> {
    Page<UserActivityLog> findWithFilters(Long userId, String role, String action, String resourceType,
                                        Boolean success, LocalDateTime startDate, LocalDateTime endDate,
                                        String ipAddress, Pageable pageable);
    Page<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);

    List<UserActivityLog> findByUserIdAndRoleOrderByCreatedAtDesc(Long userId, String role);
    Page<UserActivityLog> findByRoleOrderByCreatedAtDesc(String role, Pageable pageable);
    Page<UserActivityLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    Page<UserActivityLog> findByActionAndRoleOrderByCreatedAtDesc(String action, String role, Pageable pageable);
    Page<UserActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<UserActivityLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime startDate, Pageable pageable);
    Page<UserActivityLog> findByRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            String role,
            LocalDateTime startDate,
            Pageable pageable
    );
    Page<UserActivityLog> findByUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId,
            String role,
            LocalDateTime startDate,
            Pageable pageable
    );
    long countByUserIdAndRoleAndCreatedAtAfter(
            Long userId,
            String role,
            LocalDateTime date
    );
    Page<UserActivityLog> findByUserIdAndRoleAndSuccessFalseOrderByCreatedAtDesc(Long userId, String role, Pageable pageable);
    Page<UserActivityLog> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);
    long countByActionSince(String action, LocalDateTime since);
    long countBySuccessAndCreatedAtAfter(boolean success, LocalDateTime date);

    int deleteByCreatedAtBefore(LocalDateTime before);
}
