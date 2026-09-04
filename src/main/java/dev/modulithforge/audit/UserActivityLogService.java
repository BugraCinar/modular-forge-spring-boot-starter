package dev.modulithforge.audit;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.shared.PaginationUtils;

import dev.modulithforge.audit.dto.UserActivityLogDTO;
import dev.modulithforge.audit.dto.UserActivityLogListResponse;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.audit.UserActivityLog;
import dev.modulithforge.identity.UserRepository;
import dev.modulithforge.audit.UserActivityLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
@Service
@AuditModule
@RequiredArgsConstructor
@Slf4j
public class UserActivityLogService {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "userId", "role", "action", "resourceType",
        "resourceId", "ipAddress", "success", "createdAt"
    );

    private final UserActivityLogRepository userActivityLogRepository;
    private final UserRepository userRepository;
    private final AdminActivityLogger adminActivityLogger;
    @Transactional(readOnly = true)
    public UserActivityLogListResponse getAllUserActivityLogs(
            Long userId,
            String role,
            String action,
            String resourceType,
            Boolean success,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String ipAddress,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long currentAdminId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String validatedSortBy = validateSortField(sortBy, "createdAt");

        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by(direction, validatedSortBy));
        Specification<UserActivityLog> spec = buildSpecification(userId, role, action, resourceType, success, startDate, endDate, ipAddress);

        Page<UserActivityLog> logPage = userActivityLogRepository.findAll(spec, pageable);

        Map<Long, User> usersById = loadUsers(logPage.getContent());
        List<UserActivityLogDTO> logDTOs = logPage.getContent().stream()
                .map(log -> convertToDTO(log, usersById))
                .collect(Collectors.toList());
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (userId != null) details.put("filterUserId", userId);
        if (role != null) details.put("filterRole", role);
        if (action != null) details.put("filterAction", action);
        if (resourceType != null) details.put("filterResourceType", resourceType);
        if (success != null) details.put("filterSuccess", success);
        if (startDate != null) details.put("filterStartDate", startDate.toString());
        if (endDate != null) details.put("filterEndDate", endDate.toString());
        if (ipAddress != null) details.put("filterIpAddress", ipAddress);
        details.put("resultCount", logDTOs.size());
        details.put("totalElements", logPage.getTotalElements());

        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "UserActivityLog",
                "list",
                details,
                httpRequest
        );

        return UserActivityLogListResponse.builder()
                .logs(logDTOs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .pageSize(logPage.getSize())
                .hasNext(logPage.hasNext())
                .hasPrevious(logPage.hasPrevious())
                .build();
    }
    @Transactional(readOnly = true)
    public UserActivityLogDTO getUserActivityLogById(Long logId, Long currentAdminId, jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserActivityLog activityLog = userActivityLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("User activity log not found with ID: " + logId));
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("logId", logId);
        details.put("targetUserId", activityLog.getUserId());
        details.put("targetRole", activityLog.getRole());
        details.put("action", activityLog.getAction());

        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "UserActivityLog",
                logId.toString(),
                details,
                httpRequest
        );

        return convertToDTO(activityLog);
    }
    @Transactional(readOnly = true)
    public UserActivityLogListResponse getUserActivityLogsByUser(
            Long userId,
            String role,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long currentAdminId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String validatedSortBy = validateSortField(sortBy, "createdAt");

        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by(direction, validatedSortBy));

        Page<UserActivityLog> logPage = userActivityLogRepository.findByUserIdAndRoleOrderByCreatedAtDesc(userId, role, pageable);

        Map<Long, User> usersById = loadUsers(logPage.getContent());
        List<UserActivityLogDTO> logDTOs = logPage.getContent().stream()
                .map(log -> convertToDTO(log, usersById))
                .collect(Collectors.toList());
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("targetUserId", userId);
        details.put("targetRole", role);
        details.put("page", page);
        details.put("size", size);
        details.put("resultCount", logDTOs.size());

        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "UserActivityLog",
                "user:" + userId,
                details,
                httpRequest
        );

        return UserActivityLogListResponse.builder()
                .logs(logDTOs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .pageSize(logPage.getSize())
                .hasNext(logPage.hasNext())
                .hasPrevious(logPage.hasPrevious())
                .build();
    }
    @Transactional
    public int deleteOldActivityLogs(LocalDateTime beforeDate) {
        List<UserActivityLog> oldLogs = userActivityLogRepository.findAll(
                (root, query, cb) -> cb.lessThan(root.get("createdAt"), beforeDate)
        );

        int count = oldLogs.size();
        userActivityLogRepository.deleteAll(oldLogs);

        log.info("Deleted {} old user activity logs before date: {}", count, beforeDate);
        return count;
    }
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getActivityStatistics(LocalDateTime since) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();

        stats.put("totalLogins", userActivityLogRepository.countByActionSince("LOGIN", since));
        stats.put("totalRegistrations", userActivityLogRepository.countByActionSince("REGISTER", since));
        stats.put("totalPasswordResets", userActivityLogRepository.countByActionSince("PASSWORD_RESET_COMPLETE", since));
        stats.put("totalProfileUpdates", userActivityLogRepository.countByActionSince("PROFILE_UPDATE", since));
        stats.put("successfulActions", userActivityLogRepository.countBySuccessAndCreatedAtAfter(true, since));
        stats.put("failedActions", userActivityLogRepository.countBySuccessAndCreatedAtAfter(false, since));

        return stats;
    }
    private Specification<UserActivityLog> buildSpecification(
            Long userId, String role, String action, String resourceType,
            Boolean success, LocalDateTime startDate, LocalDateTime endDate, String ipAddress
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }

            if (role != null && !role.isEmpty()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.lower(root.get("role")), role.toLowerCase()));
            }

            if (action != null && !action.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action));
            }

            if (resourceType != null && !resourceType.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("resourceType"), resourceType));
            }

            if (success != null) {
                predicates.add(criteriaBuilder.equal(root.get("success"), success));
            }

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            if (ipAddress != null && !ipAddress.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("ipAddress"), ipAddress));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    private UserActivityLogDTO convertToDTO(UserActivityLog log) {
        return convertToDTO(log, loadUsers(List.of(log)));
    }
    private Map<Long, User> loadUsers(List<UserActivityLog> logs) {
        Set<Long> userIds = logs.stream()
                .filter(log -> "user".equalsIgnoreCase(log.getRole()))
                .map(UserActivityLog::getUserId)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private UserActivityLogDTO convertToDTO(UserActivityLog log, Map<Long, User> usersById) {
        String username = "Unknown";
        String email = "Unknown";
        if ("user".equalsIgnoreCase(log.getRole())) {
            User user = usersById.get(log.getUserId());
            if (user != null) {
                username = user.getUsername();
                email = user.getEmail();
            }
        }

        return UserActivityLogDTO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .role(log.getRole())
                .username(username)
                .email(email)
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .success(log.getSuccess())
                .failureReason(log.getFailureReason())
                .createdAt(log.getCreatedAt())
                .build();
    }
    private String validateSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }

        return sortBy;
    }
}
