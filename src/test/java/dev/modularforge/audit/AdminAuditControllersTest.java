package dev.modularforge.audit;

import dev.modularforge.audit.dto.AuthErrorLogListResponse;
import dev.modularforge.audit.dto.AuthErrorLogResponse;
import dev.modularforge.audit.dto.AuthErrorStatisticsResponse;
import dev.modularforge.audit.dto.SensitiveAccessLogListResponse;
import dev.modularforge.audit.dto.SensitiveAccessLogResponse;
import dev.modularforge.audit.dto.SensitiveAccessStatisticsResponse;
import dev.modularforge.audit.dto.UserActivityLogDTO;
import dev.modularforge.audit.dto.UserActivityLogListResponse;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditControllersTest {

    @Mock AdminSensitiveAccessService sensitiveService;
    @Mock AdminAuthErrorService authErrorService;
    @Mock UserActivityLogService activityService;
    @Mock AdminRepository admins;
    @Mock JwtUtils jwtUtils;
    @Mock HttpServletRequest request;

    private AdminSensitiveAccessController sensitiveController;
    private AdminAuthErrorController authErrorController;
    private AdminUserActivityLogController activityController;
    private Admin admin;

    @BeforeEach
    void setUp() {
        sensitiveController = new AdminSensitiveAccessController();
        ReflectionTestUtils.setField(sensitiveController, "adminSensitiveAccessService", sensitiveService);
        ReflectionTestUtils.setField(sensitiveController, "adminRepository", admins);
        ReflectionTestUtils.setField(sensitiveController, "jwtUtils", jwtUtils);

        authErrorController = new AdminAuthErrorController();
        ReflectionTestUtils.setField(authErrorController, "adminAuthErrorService", authErrorService);
        ReflectionTestUtils.setField(authErrorController, "adminRepository", admins);
        ReflectionTestUtils.setField(authErrorController, "jwtUtils", jwtUtils);

        activityController = new AdminUserActivityLogController(activityService, admins, jwtUtils);
        admin = new Admin();
        admin.setId(7L);
        admin.setLevel(0);
        lenient().when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        lenient().when(admins.findById(7L)).thenReturn(Optional.of(admin));
    }

    @Test
    void sensitiveAccessControllerHandlesAllSuccessfulOperations() {
        SensitiveAccessLogListResponse list = org.mockito.Mockito.mock(SensitiveAccessLogListResponse.class);
        SensitiveAccessLogResponse item = org.mockito.Mockito.mock(SensitiveAccessLogResponse.class);
        SensitiveAccessStatisticsResponse stats = org.mockito.Mockito.mock(SensitiveAccessStatisticsResponse.class);
        when(sensitiveService.getAllLogs(anyLong(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(list);
        when(sensitiveService.getLogById(7L, 1L, request)).thenReturn(item);
        when(sensitiveService.getLogsByUserId(7L, 11L, 0, 20, request)).thenReturn(list);
        when(sensitiveService.getLogsByIpAddress(7L, "192.0.2.1", 0, 20, request)).thenReturn(list);
        when(sensitiveService.getStatistics(7L, request)).thenReturn(stats);

        assertOk(sensitiveController.getAllLogs("Bearer jwt", 0, 20, "createdAt", "desc",
                11L, "user", "HIGH", "AUTH", "192.0.2.1", LocalDateTime.now(), request));
        assertOk(sensitiveController.getLogById("Bearer jwt", 1L, request));
        assertOk(sensitiveController.getLogsByUserId("Bearer jwt", 11L, 0, 20, request));
        assertOk(sensitiveController.getLogsByIpAddress("Bearer jwt", "192.0.2.1", 0, 20, request));
        assertOk(sensitiveController.getStatistics("Bearer jwt", request));
        assertOk(sensitiveController.deleteLog("Bearer jwt", 1L, request));
    }

    @Test
    void sensitiveAccessControllerMapsEveryFailureBranch() {
        when(sensitiveService.getAllLogs(anyLong(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("failure"));
        assertServerError(sensitiveController.getAllLogs("Bearer jwt", 0, 20, "createdAt", "desc",
                null, null, null, null, null, null, request));

        when(sensitiveService.getLogById(7L, 1L, request)).thenThrow(new RuntimeException("log not found"));
        assertThat(sensitiveController.getLogById("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        when(sensitiveService.getLogById(7L, 2L, request)).thenThrow(new RuntimeException());
        assertServerError(sensitiveController.getLogById("Bearer jwt", 2L, request));

        when(sensitiveService.getLogsByUserId(7L, 11L, 0, 20, request)).thenThrow(new IllegalStateException("failure"));
        when(sensitiveService.getLogsByIpAddress(7L, "ip", 0, 20, request)).thenThrow(new IllegalStateException("failure"));
        when(sensitiveService.getStatistics(7L, request)).thenThrow(new IllegalStateException("failure"));
        assertServerError(sensitiveController.getLogsByUserId("Bearer jwt", 11L, 0, 20, request));
        assertServerError(sensitiveController.getLogsByIpAddress("Bearer jwt", "ip", 0, 20, request));
        assertServerError(sensitiveController.getStatistics("Bearer jwt", request));

        admin.setLevel(1);
        assertThat(sensitiveController.deleteLog("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        admin.setLevel(0);
        doThrow(new RuntimeException("log not found")).when(sensitiveService).deleteLog(7L, 3L, request);
        assertThat(sensitiveController.deleteLog("Bearer jwt", 3L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        doThrow(new RuntimeException()).when(sensitiveService).deleteLog(7L, 4L, request);
        assertServerError(sensitiveController.deleteLog("Bearer jwt", 4L, request));
    }

    @Test
    void authErrorControllerHandlesAllSuccessfulOperations() {
        AuthErrorLogListResponse list = org.mockito.Mockito.mock(AuthErrorLogListResponse.class);
        AuthErrorLogResponse item = org.mockito.Mockito.mock(AuthErrorLogResponse.class);
        AuthErrorStatisticsResponse stats = org.mockito.Mockito.mock(AuthErrorStatisticsResponse.class);
        when(authErrorService.getAllLogs(anyLong(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(list);
        when(authErrorService.getLogById(7L, 1L, request)).thenReturn(item);
        when(authErrorService.getLogsByUserId(7L, 11L, 0, 20, request)).thenReturn(list);
        when(authErrorService.getLogsByIpAddress(7L, "192.0.2.1", 0, 20, request)).thenReturn(list);
        when(authErrorService.getStatistics(7L, request)).thenReturn(stats);

        assertOk(authErrorController.getAllLogs("Bearer jwt", 0, 20, "createdAt", "desc",
                11L, "user", "UNAUTHORIZED_401", "192.0.2.1", LocalDateTime.now(), request));
        assertOk(authErrorController.getLogById("Bearer jwt", 1L, request));
        assertOk(authErrorController.getLogsByUserId("Bearer jwt", 11L, 0, 20, request));
        assertOk(authErrorController.getLogsByIpAddress("Bearer jwt", "192.0.2.1", 0, 20, request));
        assertOk(authErrorController.getStatistics("Bearer jwt", request));
        assertOk(authErrorController.deleteLog("Bearer jwt", 1L, request));
    }

    @Test
    void authErrorControllerMapsEveryFailureBranch() {
        when(authErrorService.getAllLogs(anyLong(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("failure"));
        assertServerError(authErrorController.getAllLogs("Bearer jwt", 0, 20, "createdAt", "desc",
                null, null, null, null, null, request));
        when(authErrorService.getLogById(7L, 1L, request)).thenThrow(new RuntimeException("entry not found"));
        when(authErrorService.getLogById(7L, 2L, request)).thenThrow(new RuntimeException());
        assertThat(authErrorController.getLogById("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertServerError(authErrorController.getLogById("Bearer jwt", 2L, request));

        when(authErrorService.getLogsByUserId(7L, 11L, 0, 20, request)).thenThrow(new IllegalStateException("failure"));
        when(authErrorService.getLogsByIpAddress(7L, "ip", 0, 20, request)).thenThrow(new IllegalStateException("failure"));
        when(authErrorService.getStatistics(7L, request)).thenThrow(new IllegalStateException("failure"));
        assertServerError(authErrorController.getLogsByUserId("Bearer jwt", 11L, 0, 20, request));
        assertServerError(authErrorController.getLogsByIpAddress("Bearer jwt", "ip", 0, 20, request));
        assertServerError(authErrorController.getStatistics("Bearer jwt", request));

        admin.setLevel(2);
        assertThat(authErrorController.deleteLog("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        admin.setLevel(0);
        doThrow(new RuntimeException("entry not found")).when(authErrorService).deleteLog(7L, 3L, request);
        doThrow(new RuntimeException()).when(authErrorService).deleteLog(7L, 4L, request);
        assertThat(authErrorController.deleteLog("Bearer jwt", 3L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertServerError(authErrorController.deleteLog("Bearer jwt", 4L, request));
    }

    @Test
    void activityControllerHandlesEverySuccessfulOperationAndDefaultPeriod() {
        UserActivityLogListResponse list = org.mockito.Mockito.mock(UserActivityLogListResponse.class);
        UserActivityLogDTO item = org.mockito.Mockito.mock(UserActivityLogDTO.class);
        when(activityService.getAllUserActivityLogs(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), anyLong(), any())).thenReturn(list);
        when(activityService.getUserActivityLogById(1L, 7L, request)).thenReturn(item);
        when(activityService.getUserActivityLogsByUser(11L, "user", 0, 20, "createdAt", "desc", 7L, request))
                .thenReturn(list);
        when(activityService.getActivityStatistics(any())).thenReturn(new HashMap<>(Map.of("total", 3)));

        assertOk(activityController.getAllUserActivityLogs("Bearer jwt", 11L, "user", "LOGIN", "SESSION",
                true, LocalDateTime.now().minusDays(1), LocalDateTime.now(), "ip", 0, 20,
                "createdAt", "desc", request));
        assertOk(activityController.getUserActivityLogById("Bearer jwt", 1L, request));
        assertOk(activityController.getUserActivityLogsByUser("Bearer jwt", 11L, "user", 0, 20,
                "createdAt", "desc", request));
        assertOk(activityController.getActivityStatistics("Bearer jwt", null));
        when(activityService.getActivityStatistics(any())).thenReturn(new HashMap<>());
        assertOk(activityController.getActivityStatistics("Bearer jwt", LocalDateTime.now().minusDays(5)));
    }

    @Test
    void activityControllerRejectsNonSuperAdminAcrossAllOperations() {
        admin.setLevel(1);
        assertThat(activityController.getAllUserActivityLogs("Bearer jwt", null, null, null, null,
                null, null, null, null, 0, 20, "createdAt", "desc", request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(activityController.getUserActivityLogById("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(activityController.getUserActivityLogsByUser("Bearer jwt", 1L, "user", 0, 20,
                "createdAt", "desc", request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(activityController.getActivityStatistics("Bearer jwt", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void activityControllerMapsEveryFailureBranch() {
        when(activityService.getAllUserActivityLogs(any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), anyLong(), any())).thenThrow(new IllegalStateException("failure"));
        assertServerError(activityController.getAllUserActivityLogs("Bearer jwt", null, null, null, null,
                null, null, null, null, 0, 20, "createdAt", "desc", request));

        when(activityService.getUserActivityLogById(1L, 7L, request)).thenThrow(new RuntimeException("log not found"));
        when(activityService.getUserActivityLogById(2L, 7L, request)).thenThrow(new RuntimeException());
        assertThat(activityController.getUserActivityLogById("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertServerError(activityController.getUserActivityLogById("Bearer jwt", 2L, request));
        doAnswer(invocation -> { throw new IOException("io"); })
                .when(activityService).getUserActivityLogById(3L, 7L, request);
        assertServerError(activityController.getUserActivityLogById("Bearer jwt", 3L, request));

        when(activityService.getUserActivityLogsByUser(11L, "user", 0, 20, "createdAt", "desc", 7L, request))
                .thenThrow(new IllegalStateException("failure"));
        when(activityService.getActivityStatistics(any())).thenThrow(new IllegalStateException("failure"));
        assertServerError(activityController.getUserActivityLogsByUser("Bearer jwt", 11L, "user", 0, 20,
                "createdAt", "desc", request));
        assertServerError(activityController.getActivityStatistics("Bearer jwt", LocalDateTime.now()));
    }

    @Test
    void missingAdminsAndNonMatchingMessagesExerciseControllerFallbacks() {
        when(admins.findById(7L)).thenReturn(Optional.empty());

        assertThat(sensitiveController.deleteLog("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(authErrorController.deleteLog("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertServerError(activityController.getAllUserActivityLogs("Bearer jwt", null, null, null, null,
                null, null, null, null, 0, 20, "createdAt", "desc", request));
        assertThat(activityController.getUserActivityLogById("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertServerError(activityController.getUserActivityLogsByUser("Bearer jwt", 1L, "user", 0, 20,
                "createdAt", "desc", request));
        assertServerError(activityController.getActivityStatistics("Bearer jwt", null));

        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(sensitiveService.getLogById(7L, 8L, request)).thenThrow(new RuntimeException("other failure"));
        when(authErrorService.getLogById(7L, 8L, request)).thenThrow(new RuntimeException("other failure"));
        when(activityService.getUserActivityLogById(8L, 7L, request)).thenThrow(new RuntimeException("other failure"));
        assertServerError(sensitiveController.getLogById("Bearer jwt", 8L, request));
        assertServerError(authErrorController.getLogById("Bearer jwt", 8L, request));
        assertServerError(activityController.getUserActivityLogById("Bearer jwt", 8L, request));

        doThrow(new RuntimeException("other failure")).when(sensitiveService).deleteLog(7L, 9L, request);
        doThrow(new RuntimeException("other failure")).when(authErrorService).deleteLog(7L, 9L, request);
        assertServerError(sensitiveController.deleteLog("Bearer jwt", 9L, request));
        assertServerError(authErrorController.deleteLog("Bearer jwt", 9L, request));
    }

    private void assertOk(org.springframework.http.ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void assertServerError(org.springframework.http.ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
