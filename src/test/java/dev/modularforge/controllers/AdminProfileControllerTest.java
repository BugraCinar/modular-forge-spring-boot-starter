package dev.modularforge.controllers;

import dev.modularforge.admin.AdminProfileController;
import dev.modularforge.identity.model.Admin;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.admin.dto.AdminProfileDTO;
import dev.modularforge.admin.dto.ChangePasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminProfileRequest;
import dev.modularforge.security.AdminLevelAuthorizationService;
import dev.modularforge.admin.AdminProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProfileControllerTest extends BaseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AdminProfileService adminProfileService;
    @MockitoBean AdminLevelAuthorizationService adminLevelAuthorizationService;

    @BeforeEach
    void setUp() {
        when(adminLevelAuthorizationService.isLevel0Or1Or2()).thenReturn(true);
    }

    private AdminProfileDTO sampleProfile() {
        AdminProfileDTO dto = new AdminProfileDTO();
        dto.setId(1L);
        dto.setUsername("testAdmin");
        dto.setEmail("admin@test.com");
        dto.setLevel(1);
        dto.setIsActive(true);
        return dto;
    }

    @Test
    @DisplayName("GET /profile → 200 with profile DTO")
    void getAdminProfile_returns200() throws Exception {
        when(adminProfileService.getAdminProfile(1L)).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/admin/profile")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testAdmin"));
    }

    @Test
    @DisplayName("PUT /profile → 200 with updated DTO")
    void updateAdminProfile_returns200() throws Exception {
        UpdateAdminProfileRequest req = new UpdateAdminProfileRequest();
        req.setFirstName("Updated");
        when(adminProfileService.updateAdminProfile(any(), any())).thenReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/admin/profile")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /change-password → 200 with success=true and correct message")
    void changePassword_returns200WithSuccessMessage() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest("oldPassword", "NewPassword1!", "NewPassword1!");
        doNothing().when(adminProfileService).changePassword(any(), any());

        mockMvc.perform(post("/api/v1/admin/profile/change-password")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @DisplayName("POST /{id}/deactivate → 200 with success=true")
    void deactivateAccount_returns200WithSuccessBody() throws Exception {
        doNothing().when(adminProfileService).deactivateAccount(any(), any());

        mockMvc.perform(post("/api/v1/admin/profile/2/deactivate")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin account deactivated successfully"));
    }

    @Test
    @DisplayName("POST /{id}/reactivate → 200 with success=true")
    void reactivateAccount_returns200WithSuccessBody() throws Exception {
        doNothing().when(adminProfileService).reactivateAccount(any(), any());

        mockMvc.perform(post("/api/v1/admin/profile/2/reactivate")
                        .principal(makeAdminAuth(1L))
                        .with(authentication(makeAdminAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin account reactivated successfully"));
    }
}
