package dev.modularforge.audit;

import dev.modularforge.shared.error.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditReadServicesTest {

    @Mock AuthenticationErrorLogRepository authRepository;
    @Mock SensitiveEndpointAccessLogRepository sensitiveRepository;
    @Mock AdminActivityLogger activityLogger;
    @Mock HttpServletRequest request;

    private AdminAuthErrorService authService;
    private AdminSensitiveAccessService sensitiveService;

    @BeforeEach
    void setUp() {
        authService = new AdminAuthErrorService();
        ReflectionTestUtils.setField(authService, "authErrorLogRepository", authRepository);
        ReflectionTestUtils.setField(authService, "adminActivityLogger", activityLogger);
        sensitiveService = new AdminSensitiveAccessService();
        ReflectionTestUtils.setField(sensitiveService, "accessLogRepository", sensitiveRepository);
        ReflectionTestUtils.setField(sensitiveService, "adminActivityLogger", activityLogger);
    }

    @Test
    void authLogListingExercisesEveryFilterAndSortBranch() {
        var page = new PageImpl<>(List.of(authLog(1L)));
        when(authRepository.findByUserIdOrderByCreatedAtDesc(eq(4L), any())).thenReturn(page);
        when(authRepository.findByRoleOrderByCreatedAtDesc(eq("admin"), any())).thenReturn(page);
        when(authRepository.findByErrorTypeOrderByCreatedAtDesc(eq(AuthenticationErrorLog.ErrorType.INVALID_TOKEN), any())).thenReturn(page);
        when(authRepository.findByIpAddressOrderByCreatedAtDesc(eq("127.0.0.1"), any())).thenReturn(page);
        when(authRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(), any())).thenReturn(page);
        when(authRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        assertAuthList(authService.getAllLogs(9L, -1, 1000, "id", "asc", 4L,
                "admin", "INVALID_TOKEN", "127.0.0.1", LocalDateTime.now(), request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                "admin", null, null, null, request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, "INVALID_TOKEN", null, null, request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "unsafe", "desc", null,
                null, "INVALID", null, null, request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, "127.0.0.1", null, request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, null, LocalDateTime.now(), request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, null, null, request));
        assertAuthList(authService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                "", "", "", null, request));
    }

    @Test
    void authLogDetailConvenienceQueriesStatisticsAndDeletionWork() {
        AuthenticationErrorLog log = authLog(2L);
        var page = new PageImpl<>(List.of(log));
        when(authRepository.findById(2L)).thenReturn(Optional.of(log));
        when(authRepository.findById(99L)).thenReturn(Optional.empty());
        when(authRepository.findByUserIdOrderByCreatedAtDesc(eq(4L), any())).thenReturn(page);
        when(authRepository.findByIpAddressOrderByCreatedAtDesc(eq("127.0.0.1"), any())).thenReturn(page);
        when(authRepository.count()).thenReturn(20L);
        when(authRepository.countByErrorType(any())).thenReturn(2L);
        when(authRepository.getStatisticsByErrorType()).thenReturn(List.<Object[]>of(
                new Object[]{AuthenticationErrorLog.ErrorType.INVALID_TOKEN, 3L}));
        when(authRepository.getDailyStatistics(any())).thenReturn(List.<Object[]>of(
                new Object[]{LocalDate.of(2026, 9, 4), 5L}));

        assertThat(authService.getLogById(9L, 2L, request).getId()).isEqualTo(2L);
        assertThat(authService.getLogsByUserId(9L, 4L, 0, 20, request).getLogs()).hasSize(1);
        assertThat(authService.getLogsByIpAddress(9L, "127.0.0.1", 0, 20, request).getLogs()).hasSize(1);
        var statistics = authService.getStatistics(9L, request);
        assertThat(statistics.getTotalErrors()).isEqualTo(20);
        assertThat(statistics.getErrorsByType()).containsEntry("INVALID_TOKEN", 3L);
        assertThat(statistics.getDailyStatistics()).containsEntry("2026-09-04", 5L);
        authService.deleteLog(9L, 2L, request);
        verify(authRepository).deleteById(2L);

        assertThatThrownBy(() -> authService.getLogById(9L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> authService.deleteLog(9L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sensitiveLogListingExercisesEveryFilterAndSortBranch() {
        var page = new PageImpl<>(List.of(sensitiveLog(1L)));
        when(sensitiveRepository.findByUserIdOrderByCreatedAtDesc(eq(4L), any())).thenReturn(page);
        when(sensitiveRepository.findByRoleOrderByCreatedAtDesc(eq("admin"), any())).thenReturn(page);
        when(sensitiveRepository.findBySeverityOrderByCreatedAtDesc(eq(SensitiveEndpointAccessLog.SeverityLevel.HIGH), any())).thenReturn(page);
        when(sensitiveRepository.findByEndpointCategoryOrderByCreatedAtDesc(eq("TOKENS"), any())).thenReturn(page);
        when(sensitiveRepository.findByIpAddressOrderByCreatedAtDesc(eq("127.0.0.1"), any())).thenReturn(page);
        when(sensitiveRepository.findByCreatedAtAfterOrderByCreatedAtDesc(any(), any())).thenReturn(page);
        when(sensitiveRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        assertSensitiveList(sensitiveService.getAllLogs(9L, -1, 1000, "id", "asc", 4L,
                "admin", "HIGH", "TOKENS", "127.0.0.1", LocalDateTime.now(), request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                "admin", null, null, null, null, request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, "HIGH", null, null, null, request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "unsafe", "desc", null,
                null, "INVALID", null, null, null, request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, "TOKENS", null, null, request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, null, "127.0.0.1", null, request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, null, null, LocalDateTime.now(), request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                null, null, null, null, null, request));
        assertSensitiveList(sensitiveService.getAllLogs(9L, 0, 20, "createdAt", "desc", null,
                "", "", "", "", null, request));
    }

    @Test
    void sensitiveDetailConvenienceQueriesStatisticsAndDeletionWork() {
        SensitiveEndpointAccessLog log = sensitiveLog(2L);
        var page = new PageImpl<>(List.of(log));
        when(sensitiveRepository.findById(2L)).thenReturn(Optional.of(log));
        when(sensitiveRepository.findById(99L)).thenReturn(Optional.empty());
        when(sensitiveRepository.findByUserIdOrderByCreatedAtDesc(eq(4L), any())).thenReturn(page);
        when(sensitiveRepository.findByIpAddressOrderByCreatedAtDesc(eq("127.0.0.1"), any())).thenReturn(page);
        when(sensitiveRepository.count()).thenReturn(20L);
        when(sensitiveRepository.countBySeverity(any())).thenReturn(2L);
        when(sensitiveRepository.getStatisticsBySeverity()).thenReturn(List.<Object[]>of(
                new Object[]{SensitiveEndpointAccessLog.SeverityLevel.CRITICAL, 3L}));
        when(sensitiveRepository.getStatisticsByCategory()).thenReturn(List.of(
                new Object[]{"TOKENS", 4L}, new Object[]{null, 1L}));
        when(sensitiveRepository.getDailyStatistics(any())).thenReturn(List.<Object[]>of(
                new Object[]{LocalDate.of(2026, 9, 4), 5L}));

        assertThat(sensitiveService.getLogById(9L, 2L, request).getId()).isEqualTo(2L);
        assertThat(sensitiveService.getLogsByUserId(9L, 4L, 0, 20, request).getLogs()).hasSize(1);
        assertThat(sensitiveService.getLogsByIpAddress(9L, "127.0.0.1", 0, 20, request).getLogs()).hasSize(1);
        var statistics = sensitiveService.getStatistics(9L, request);
        assertThat(statistics.getTotalAccessLogs()).isEqualTo(20);
        assertThat(statistics.getAccessBySeverity()).containsEntry("CRITICAL", 3L);
        assertThat(statistics.getAccessByCategory()).containsEntry("TOKENS", 4L).doesNotContainKey(null);
        assertThat(statistics.getDailyStatistics()).containsEntry("2026-09-04", 5L);
        sensitiveService.deleteLog(9L, 2L, request);
        verify(sensitiveRepository).deleteById(2L);

        assertThatThrownBy(() -> sensitiveService.getLogById(9L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> sensitiveService.deleteLog(9L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void assertAuthList(dev.modularforge.audit.dto.AuthErrorLogListResponse response) {
        assertThat(response.getLogs()).hasSize(1);
    }

    private void assertSensitiveList(dev.modularforge.audit.dto.SensitiveAccessLogListResponse response) {
        assertThat(response.getLogs()).hasSize(1);
    }

    private AuthenticationErrorLog authLog(Long id) {
        return AuthenticationErrorLog.builder()
                .id(id)
                .errorType(AuthenticationErrorLog.ErrorType.INVALID_TOKEN)
                .userId(4L)
                .role("admin")
                .username("root")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .endpoint("/api/test")
                .httpMethod("GET")
                .errorMessage("invalid")
                .attemptedAction("read")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SensitiveEndpointAccessLog sensitiveLog(Long id) {
        return SensitiveEndpointAccessLog.builder()
                .id(id)
                .severity(SensitiveEndpointAccessLog.SeverityLevel.HIGH)
                .userId(4L)
                .role("admin")
                .username("root")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .endpoint("/api/admin/tokens")
                .httpMethod("GET")
                .endpointCategory("TOKENS")
                .description("read")
                .responseStatus(200)
                .emailAlertSent(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
