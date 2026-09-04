package dev.modulithforge.controllers;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.profile.UserProfileController;

import tools.jackson.databind.ObjectMapper;
import dev.modulithforge.admin.dto.ChangePasswordRequest;
import dev.modulithforge.profile.dto.DeactivateAccountRequest;
import dev.modulithforge.profile.dto.UpdateUserProfileRequest;
import dev.modulithforge.profile.dto.UserProfileDTO;
import dev.modulithforge.profile.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserProfileControllerTest extends BaseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean UserProfileService userProfileService;

    private UserProfileDTO sampleProfile() {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(1L);
        dto.setUsername("testUser");
        dto.setEmail("user@test.com");
        dto.setIsActive(true);
        return dto;
    }

    @Test
    @DisplayName("GET /profile → 200 with profile DTO")
    void getProfile_returns200() throws Exception {
        when(userProfileService.getProfile(1L)).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/profile")
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testUser"))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @DisplayName("PUT /profile → 200 with updated DTO")
    void updateProfile_returns200() throws Exception {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setFirstName("UpdatedName");

        UserProfileDTO updated = sampleProfile();
        updated.setFirstName("UpdatedName");
        when(userProfileService.updateProfile(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/profile")
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("UpdatedName"));
    }

    @Test
    @DisplayName("POST /change-password → 200 with success=true and correct message")
    void changePassword_returns200WithSuccessMessage() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest(
                "oldPass1!", "NewStrongPassword1!", "NewStrongPassword1!");
        doNothing().when(userProfileService).changePassword(eq(1L), any());

        mockMvc.perform(post("/api/v1/profile/change-password")
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
    }

    @Test
    @DisplayName("DELETE /profile → 200 with success=true and grace-period message")
    void deactivateAccount_returns200WithGracePeriodMessage() throws Exception {
        DeactivateAccountRequest req = new DeactivateAccountRequest();
        req.setPassword("MyPassword1!");
        doNothing().when(userProfileService).deactivateAccount(eq(1L), any());

        mockMvc.perform(delete("/api/v1/profile")
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Account deactivated. You may reactivate it by logging in within 30 days."));
    }
}
