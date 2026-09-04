package dev.modularforge.audit;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;
import dev.modularforge.shared.PaginationUtils;

import dev.modularforge.audit.dto.AuthErrorLogListResponse;
import dev.modularforge.audit.dto.AuthErrorLogResponse;
import dev.modularforge.audit.dto.AuthErrorStatisticsResponse;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.audit.AuthenticationErrorLog;
import dev.modularforge.audit.AuthenticationErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
@Service
@AuditModule
@Slf4j
public class AdminAuthErrorService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "errorType", "userId", "role", "username", "ipAddress", "endpoint", "createdAt");

    @Autowired
    private AuthenticationErrorLogRepository authErrorLogRepository;

    @Autowired
    private AdminActivityLogger adminActivityLogger;
    @Transactional(readOnly = true)
    public AuthErrorLogListResponse getAllLogs(
            Long adminId,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long userId,
            String role,
            String errorType,
            String ipAddress,
            LocalDateTime startDate,
            HttpServletRequest request) {

        log.info("Admin {} retrieving auth error logs - page: {}, size: {}, sortBy: {}, filters: userId={}, role={}, errorType={}, ipAddress={}",
                adminId, page, size, sortBy, userId, role, errorType, ipAddress);
        String safeSort = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = sortDirection.equalsIgnoreCase("asc")
                ? Sort.by(safeSort).ascending()
                : Sort.by(safeSort).descending();

        Pageable pageable = PaginationUtils.pageRequest(page, size, sort);
        Page<AuthenticationErrorLog> logsPage;

        if (userId != null) {
            logsPage = authErrorLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else if (role != null && !role.isEmpty()) {
            logsPage = authErrorLogRepository.findByRoleOrderByCreatedAtDesc(role, pageable);
        } else if (errorType != null && !errorType.isEmpty()) {
            try {
                AuthenticationErrorLog.ErrorType type = AuthenticationErrorLog.ErrorType.valueOf(errorType);
                logsPage = authErrorLogRepository.findByErrorTypeOrderByCreatedAtDesc(type, pageable);
            } catch (IllegalArgumentException e) {
                logsPage = authErrorLogRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else if (ipAddress != null && !ipAddress.isEmpty()) {
            logsPage = authErrorLogRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress, pageable);
        } else if (startDate != null) {
            logsPage = authErrorLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(startDate, pageable);
        } else {
            logsPage = authErrorLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<AuthErrorLogResponse> logs = logsPage.getContent().stream()
                .map(AuthErrorLogResponse::from)
                .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("resultCount", logs.size());
        details.put("totalElements", logsPage.getTotalElements());
        if (userId != null) details.put("filterUserId", userId);
        if (role != null) details.put("filterRole", role);
        if (errorType != null) details.put("filterErrorType", errorType);
        if (ipAddress != null) details.put("filterIpAddress", ipAddress);

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "AuthErrorLog",
                "list",
                details,
                request
        );

        return AuthErrorLogListResponse.builder()
                .logs(logs)
                .currentPage(logsPage.getNumber())
                .totalPages(logsPage.getTotalPages())
                .totalElements(logsPage.getTotalElements())
                .pageSize(logsPage.getSize())
                .hasNext(logsPage.hasNext())
                .hasPrevious(logsPage.hasPrevious())
                .build();
    }
    @Transactional(readOnly = true)
    public AuthErrorLogResponse getLogById(Long adminId, Long logId, HttpServletRequest request) {
        log.info("Admin {} retrieving auth error log: {}", adminId, logId);

        AuthenticationErrorLog errorLog = authErrorLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Authentication error log not found with ID: " + logId));
        Map<String, Object> details = new HashMap<>();
        details.put("logId", logId);
        details.put("errorType", errorLog.getErrorType().name());

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "AuthErrorLog",
                logId.toString(),
                details,
                request
        );

        return AuthErrorLogResponse.from(errorLog);
    }
    @Transactional(readOnly = true)
    public AuthErrorLogListResponse getLogsByUserId(
            Long adminId,
            Long userId,
            int page,
            int size,
            HttpServletRequest request) {

        log.info("Admin {} retrieving auth error logs for user: {}", adminId, userId);

        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by("createdAt").descending());
        Page<AuthenticationErrorLog> logsPage = authErrorLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<AuthErrorLogResponse> logs = logsPage.getContent().stream()
                .map(AuthErrorLogResponse::from)
                .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("targetUserId", userId);
        details.put("resultCount", logs.size());

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "AuthErrorLog",
                "user:" + userId,
                details,
                request
        );

        return AuthErrorLogListResponse.builder()
                .logs(logs)
                .currentPage(logsPage.getNumber())
                .totalPages(logsPage.getTotalPages())
                .totalElements(logsPage.getTotalElements())
                .pageSize(logsPage.getSize())
                .hasNext(logsPage.hasNext())
                .hasPrevious(logsPage.hasPrevious())
                .build();
    }
    @Transactional(readOnly = true)
    public AuthErrorLogListResponse getLogsByIpAddress(
            Long adminId,
            String ipAddress,
            int page,
            int size,
            HttpServletRequest request) {

        log.info("Admin {} retrieving auth error logs for IP: {}", adminId, ipAddress);

        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by("createdAt").descending());
        Page<AuthenticationErrorLog> logsPage = authErrorLogRepository.findByIpAddressOrderByCreatedAtDesc(ipAddress, pageable);

        List<AuthErrorLogResponse> logs = logsPage.getContent().stream()
                .map(AuthErrorLogResponse::from)
                .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("targetIpAddress", ipAddress);
        details.put("resultCount", logs.size());

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "AuthErrorLog",
                "ip:" + ipAddress,
                details,
                request
        );

        return AuthErrorLogListResponse.builder()
                .logs(logs)
                .currentPage(logsPage.getNumber())
                .totalPages(logsPage.getTotalPages())
                .totalElements(logsPage.getTotalElements())
                .pageSize(logsPage.getSize())
                .hasNext(logsPage.hasNext())
                .hasPrevious(logsPage.hasPrevious())
                .build();
    }
    @Transactional(readOnly = true)
    public AuthErrorStatisticsResponse getStatistics(Long adminId, HttpServletRequest request) {
        log.info("Admin {} retrieving auth error statistics", adminId);
        long totalErrors = authErrorLogRepository.count();
        long unauthorized401Count = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.UNAUTHORIZED_401);
        long forbidden403Count = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.FORBIDDEN_403);
        long notFound404Count = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.NOT_FOUND_404);
        long badRequest400Count = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.BAD_REQUEST_400);
        long internalServerError500Count = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.INTERNAL_SERVER_ERROR_500);
        long invalidTokenCount = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.INVALID_TOKEN);
        long accessDeniedCount = authErrorLogRepository.countByErrorType(AuthenticationErrorLog.ErrorType.ACCESS_DENIED);
        Map<String, Long> errorsByType = new LinkedHashMap<>();
        List<Object[]> stats = authErrorLogRepository.getStatisticsByErrorType();
        for (Object[] stat : stats) {
            AuthenticationErrorLog.ErrorType type = (AuthenticationErrorLog.ErrorType) stat[0];
            Long count = (Long) stat[1];
            errorsByType.put(type.name(), count);
        }
        Map<String, Long> dailyStatistics = new LinkedHashMap<>();
        List<Object[]> dailyStats = authErrorLogRepository.getDailyStatistics(LocalDateTime.now().minusDays(30));
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Object[] stat : dailyStats) {
            String date = stat[0].toString();
            Long count = (Long) stat[1];
            dailyStatistics.put(date, count);
        }
        Map<String, Object> details = new HashMap<>();
        details.put("totalErrors", totalErrors);

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "AuthErrorLog",
                "statistics",
                details,
                request
        );

        return AuthErrorStatisticsResponse.builder()
                .totalErrors(totalErrors)
                .unauthorized401Count(unauthorized401Count)
                .forbidden403Count(forbidden403Count)
                .notFound404Count(notFound404Count)
                .badRequest400Count(badRequest400Count)
                .internalServerError500Count(internalServerError500Count)
                .invalidTokenCount(invalidTokenCount)
                .accessDeniedCount(accessDeniedCount)
                .errorsByType(errorsByType)
                .dailyStatistics(dailyStatistics)
                .build();
    }
    @Transactional
    public void deleteLog(Long adminId, Long logId, HttpServletRequest request) {
        log.info("Admin {} deleting auth error log: {}", adminId, logId);

        AuthenticationErrorLog errorLog = authErrorLogRepository.findById(logId)
                .orElseThrow(() -> new ResourceNotFoundException("Authentication error log not found with ID: " + logId));
        Map<String, Object> details = new HashMap<>();
        details.put("logId", logId);
        details.put("errorType", errorLog.getErrorType().name());
        details.put("endpoint", errorLog.getEndpoint());

        authErrorLogRepository.deleteById(logId);
        adminActivityLogger.logActivity(
                adminId,
                "DELETE",
                "AuthErrorLog",
                logId.toString(),
                details,
                request
        );

        log.info("Deleted auth error log with ID: {} by admin {}", logId, adminId);
    }
}
