package dev.modulithforge.audit;

import dev.modulithforge.shared.PaginationUtils;

import dev.modulithforge.audit.dto.AdminActivityLogDTO;
import dev.modulithforge.audit.dto.AdminActivityLogListResponse;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.audit.AdminActivityLog;
import dev.modulithforge.audit.AdminActivityLogRepository;
import dev.modulithforge.identity.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AuditModule
@RequiredArgsConstructor
@Slf4j
public class AdminActivityLogService {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "adminId", "action", "resourceType",
        "resourceId", "ipAddress", "createdAt"
    );

    private final AdminActivityLogRepository activityLogRepository;
    private final AdminRepository adminRepository;
    private final AdminActivityLogger adminActivityLogger;
    @Transactional(readOnly = true)
    public AdminActivityLogListResponse getAllActivityLogs(
            Long adminId,
            String action,
            String resourceType,
            LocalDateTime startDate,
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

        Page<AdminActivityLog> logPage;
        if (adminId != null) {
            logPage = activityLogRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable);
        } else {
            logPage = activityLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        Map<Long, Admin> adminsById = loadAdmins(logPage.getContent());
        List<AdminActivityLogDTO> logDTOs = logPage.getContent().stream()
                .map(log -> convertToDTO(log, adminsById))
                .collect(Collectors.toList());
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (adminId != null) details.put("filterAdminId", adminId);
        if (action != null) details.put("filterAction", action);
        if (resourceType != null) details.put("filterResourceType", resourceType);
        if (startDate != null) details.put("filterStartDate", startDate.toString());
        details.put("resultCount", logDTOs.size());
        details.put("totalElements", logPage.getTotalElements());

        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "AdminActivityLog",
                "list",
                details,
                httpRequest
        );

        return AdminActivityLogListResponse.builder()
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
    public AdminActivityLogDTO getActivityLogById(Long logId, Long currentAdminId, jakarta.servlet.http.HttpServletRequest httpRequest) {
        AdminActivityLog log = activityLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Activity log not found with ID: " + logId));
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("logId", logId);
        details.put("targetAdminId", log.getAdminId());
        details.put("action", log.getAction());
        details.put("resourceType", log.getResourceType());

        adminActivityLogger.logActivity(
                currentAdminId,
                "READ",
                "AdminActivityLog",
                logId.toString(),
                details,
                httpRequest
        );

        return convertToDTO(log);
    }
    @Transactional
    public void deleteActivityLog(Long logId) {
        if (!activityLogRepository.existsById(logId)) {
            throw new RuntimeException("Activity log not found with ID: " + logId);
        }

        activityLogRepository.deleteById(logId);
        log.info("Deleted activity log with ID: {}", logId);
    }
    @Transactional
    public int deleteOldActivityLogs(LocalDateTime beforeDate) {
        List<AdminActivityLog> oldLogs = activityLogRepository
                .findByCreatedAtAfterOrderByCreatedAtDesc(beforeDate);

        int count = oldLogs.size();
        activityLogRepository.deleteAll(oldLogs);

        log.info("Deleted {} old activity logs before date: {}", count, beforeDate);
        return count;
    }
    @Transactional(readOnly = true)
    public long getActivityCountForAdmin(Long adminId, LocalDateTime since) {
        return activityLogRepository.countByAdminIdAndCreatedAtAfter(adminId, since);
    }
    private AdminActivityLogDTO convertToDTO(AdminActivityLog log) {
        return convertToDTO(log, loadAdmins(List.of(log)));
    }
    private Map<Long, Admin> loadAdmins(List<AdminActivityLog> logs) {
        Set<Long> adminIds = logs.stream()
                .map(AdminActivityLog::getAdminId)
                .collect(Collectors.toSet());
        if (adminIds.isEmpty()) {
            return Map.of();
        }
        return adminRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(Admin::getId, admin -> admin));
    }

    private AdminActivityLogDTO convertToDTO(AdminActivityLog log, Map<Long, Admin> adminsById) {
        Admin admin = adminsById.get(log.getAdminId());

        return AdminActivityLogDTO.builder()
                .id(log.getId())
                .adminId(log.getAdminId())
                .adminUsername(admin != null ? admin.getUsername() : "Unknown")
                .adminEmail(admin != null ? admin.getEmail() : "Unknown")
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
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
