package dev.modularforge.controllers;

import dev.modularforge.admin.AdminManagementController;
import dev.modularforge.admin.dto.AdminListResponse;
import dev.modularforge.admin.dto.AdminManagementDTO;
import dev.modularforge.admin.dto.CreateAdminRequest;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminRequest;
import dev.modularforge.identity.model.Admin;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.admin.dto.*;
import dev.modularforge.security.AdminLevelAuthorizationService;
import dev.modularforge.admin.AdminManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminManagementControllerTest extends BaseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AdminManagementService adminManagementService;
    @MockitoBean AdminLevelAuthorizationService adminLevelAuthorizationService;

    @BeforeEach
    void setUp() {
        when(adminLevelAuthorizationService.isLevel0Or1()).thenReturn(true);
    }

    private AdminManagementDTO sampleDTO() {
        AdminManagementDTO dto = new AdminManagementDTO();
        dto.setId(2L);
        dto.setUsername("targetAdmin");
        dto.setEmail("target@test.com");
        dto.setLevel(1);
        dto.setIsActive(true);
        return dto;
    }

    @Test
    @DisplayName("GET /admins → 200 with paginated list")
    void getAllAdmins_returns200() throws Exception {
        AdminListResponse response = AdminListResponse.builder()
                .admins(List.of(sampleDTO()))
                .currentPage(0).totalPages(1).totalItems(1L).pageSize(10)
                .build();
        when(adminManagementService.getAllAdmins(any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/admins")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("GET /admins/{id} → 200 with DTO")
    void getAdminById_returns200() throws Exception {
        when(adminManagementService.getAdminById(eq(1L), eq(2L), any())).thenReturn(sampleDTO());

        mockMvc.perform(get("/api/v1/admin/admins/2")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("targetAdmin"));
    }

    @Test
    @DisplayName("POST /admins → 201 Created")
    void createAdmin_returns201() throws Exception {
        CreateAdminRequest req = new CreateAdminRequest();
        req.setUsername("newAdmin");
        req.setEmail("new@test.com");
        req.setPassword("StrongPassword1!");
        req.setLevel(2);

        when(adminManagementService.createAdmin(any(), any(), any())).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/v1/admin/admins")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    @DisplayName("PUT /admins/{id} → 200 OK")
    void updateAdmin_returns200() throws Exception {
        UpdateAdminRequest req = new UpdateAdminRequest();
        req.setFirstName("Updated");

        when(adminManagementService.updateAdmin(any(), any(), any(), any())).thenReturn(sampleDTO());

        mockMvc.perform(put("/api/v1/admin/admins/2")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /admins/{id} → 200 with success=true and message")
    void deleteAdmin_returns200WithSuccessBody() throws Exception {
        doNothing().when(adminManagementService).deleteAdmin(any(), any(), any());

        mockMvc.perform(delete("/api/v1/admin/admins/2")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin deactivated successfully"));
    }

    @Test
    @DisplayName("POST /admins/{id}/activate → 200 OK")
    void activateAdmin_returns200() throws Exception {
        when(adminManagementService.activateAdmin(any(), any(), any())).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/v1/admin/admins/2/activate")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /admins/{id}/deactivate → 200 OK")
    void deactivateAdmin_returns200() throws Exception {
        when(adminManagementService.deactivateAdmin(any(), any(), any())).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/v1/admin/admins/2/deactivate")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /admins/{id}/reset-password → 200 with success=true")
    void resetAdminPassword_returns200WithSuccessBody() throws Exception {
        ResetUserPasswordRequest req = new ResetUserPasswordRequest();
        req.setNewPassword("NewPassword1!");

        doNothing().when(adminManagementService).resetAdminPassword(any(), any(), any());

        mockMvc.perform(post("/api/v1/admin/admins/2/reset-password")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admins/{id}/unlock → 200 OK")
    void unlockAdmin_returns200() throws Exception {
        when(adminManagementService.unlockAdmin(any(), any())).thenReturn(sampleDTO());

        mockMvc.perform(post("/api/v1/admin/admins/2/unlock")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk());
    }
}
