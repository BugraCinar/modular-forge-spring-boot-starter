package dev.modulithforge.controllers;

import dev.modulithforge.audit.AdminActivityLogController;
import dev.modulithforge.security.JwtUtils;

import dev.modulithforge.audit.dto.AdminActivityLogDTO;
import dev.modulithforge.audit.dto.AdminActivityLogListResponse;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.audit.AdminActivityLogService;
import dev.modulithforge.security.AdminLevelAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AdminActivityLogController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminActivityLogControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminActivityLogService activityLogService;

    @MockitoBean
    private AdminRepository adminRepository;
    @MockitoBean
    private AdminLevelAuthorizationService adminLevelAuthorizationService;

    private static final String AUTH_HEADER = "Bearer fake-token";

    @BeforeEach
    void stubDefaults() {
        when(adminLevelAuthorizationService.isLevel0()).thenReturn(true);
        when(jwtUtils.extractUserId("fake-token")).thenReturn(1);
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setLevel(0);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    @Test
    void getAllLogs_level0Admin_returns200WithSuccessTrue() throws Exception {
        AdminActivityLogListResponse serviceResponse = new AdminActivityLogListResponse();
        serviceResponse.setLogs(Collections.emptyList());
        serviceResponse.setCurrentPage(0);
        serviceResponse.setTotalPages(0);
        serviceResponse.setTotalElements(0);
        serviceResponse.setPageSize(20);

        when(activityLogService.getAllActivityLogs(
                isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(20), eq("createdAt"), eq("desc"),
                eq(1L), any()))
                .thenReturn(serviceResponse);

        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void getAllLogs_level1Admin_returns403WithAccessDeniedMessage() throws Exception {
        Admin level1Admin = new Admin();
        level1Admin.setId(1L);
        level1Admin.setLevel(1);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(level1Admin));

        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Access denied. Only Level 0 Super Admins can view activity logs."));
    }

    @Test
    void getAllLogs_serviceThrowsGenericException_returns500() throws Exception {
        when(activityLogService.getAllActivityLogs(
                any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString(),
                anyLong(), any()))
                .thenThrow(new RuntimeException("DB connection failed"));

        mockMvc.perform(get("/api/v1/admin/activity-logs")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getLogById_level0Admin_returns200WithSuccessTrue() throws Exception {
        AdminActivityLogDTO logDTO = new AdminActivityLogDTO();
        logDTO.setId(42L);

        when(activityLogService.getActivityLogById(eq(42L), eq(1L), any()))
                .thenReturn(logDTO);

        mockMvc.perform(get("/api/v1/admin/activity-logs/42")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42));
    }

    @Test
    void getLogById_notFoundException_returns404WithSuccessFalse() throws Exception {
        when(activityLogService.getActivityLogById(eq(999L), eq(1L), any()))
                .thenThrow(new RuntimeException("Activity log not found"));

        mockMvc.perform(get("/api/v1/admin/activity-logs/999")
                        .header("Authorization", AUTH_HEADER)
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
