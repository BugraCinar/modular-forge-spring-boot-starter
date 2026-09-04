package dev.modulithforge.controllers;

import dev.modulithforge.admin.AdminUserManagementController;
import dev.modulithforge.admin.dto.AdminCreateUserRequest;
import dev.modulithforge.admin.dto.AdminUpdateUserRequest;
import dev.modulithforge.admin.dto.AdminUserDTO;
import dev.modulithforge.admin.dto.AdminUserListResponse;
import dev.modulithforge.admin.dto.ResetUserPasswordRequest;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;

import tools.jackson.databind.ObjectMapper;
import dev.modulithforge.admin.dto.*;
import dev.modulithforge.identity.model.UserType;
import dev.modulithforge.security.AdminLevelAuthorizationService;
import dev.modulithforge.admin.AdminUserManagementService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminUserManagementControllerTest extends BaseControllerTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;

        @MockitoBean
        AdminUserManagementService adminUserManagementService;
        @MockitoBean
        AdminLevelAuthorizationService adminLevelAuthorizationService;

        @BeforeEach
        void setUp() {
                when(adminLevelAuthorizationService.isLevel0Or1()).thenReturn(true);
        }

        private AdminUserDTO sampleDTO() {
                AdminUserDTO dto = new AdminUserDTO();
                dto.setId(10L);
                dto.setUsername("johndoe");
                dto.setEmail("john@example.com");
                dto.setFirstName("John");
                dto.setLastName("Doe");
                dto.setIsActive(true);
                dto.setEmailVerified(true);
                dto.setAdminDeactivated(false);
                dto.setUserType("app_user");
                return dto;
        }

        @Test
        @DisplayName("GET /users → 200 with paginated list")
        void getUsers_returns200() throws Exception {
                AdminUserListResponse response = new AdminUserListResponse();
                response.setUsers(List.of(sampleDTO()));
                response.setCurrentPage(0);
                response.setTotalPages(1);
                response.setTotalItems(1L);
                response.setPageSize(20);

                when(adminUserManagementService.getUsers(
                                any(), anyInt(), anyInt(), any(), any(),
                                any(), any(), any(), any(), any()))
                                .thenReturn(response);

                mockMvc.perform(get("/api/v1/admin/users")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalItems").value(1))
                                .andExpect(jsonPath("$.users[0].username").value("johndoe"));
        }

        @Test
        @DisplayName("GET /users/{id} → 200 with DTO")
        void getUser_returns200() throws Exception {
                when(adminUserManagementService.getUser(eq(1L), eq(10L), any()))
                                .thenReturn(sampleDTO());

                mockMvc.perform(get("/api/v1/admin/users/10")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value("johndoe"));
        }

        @Test
        @DisplayName("POST /users → 201 Created")
        void createUser_returns201() throws Exception {
                AdminCreateUserRequest req = new AdminCreateUserRequest();
                req.setUsername("johndoe");
                req.setEmail("john@example.com");
                req.setPassword("StrongPassword1!");
                req.setUserType(UserType.APP_USER);

                when(adminUserManagementService.createUser(any(), any(), any()))
                                .thenReturn(sampleDTO());

                mockMvc.perform(post("/api/v1/admin/users")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("PUT /users/{id} → 200 OK")
        void updateUser_returns200() throws Exception {
                AdminUpdateUserRequest req = new AdminUpdateUserRequest();
                req.setFirstName("Updated");

                when(adminUserManagementService.updateUser(any(), eq(10L), any(), any()))
                                .thenReturn(sampleDTO());

                mockMvc.perform(put("/api/v1/admin/users/10")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("POST /users/{id}/deactivate → 200 OK")
        void deactivateUser_returns200() throws Exception {
                AdminUserDTO dto = sampleDTO();
                dto.setIsActive(false);
                dto.setAdminDeactivated(true);

                when(adminUserManagementService.deactivateUser(any(), eq(10L), any()))
                                .thenReturn(dto);

                mockMvc.perform(post("/api/v1/admin/users/10/deactivate")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isActive").value(false))
                                .andExpect(jsonPath("$.adminDeactivated").value(true));
        }

        @Test
        @DisplayName("POST /users/{id}/reactivate → 200 OK")
        void reactivateUser_returns200() throws Exception {
                when(adminUserManagementService.reactivateUser(any(), eq(10L), any()))
                                .thenReturn(sampleDTO());

                mockMvc.perform(post("/api/v1/admin/users/10/reactivate")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isActive").value(true));
        }

        @Test
        @DisplayName("DELETE /users/{id} → 200 with success=true and message")
        void hardDeleteUser_returns200WithSuccessBody() throws Exception {
                doNothing().when(adminUserManagementService).hardDeleteUser(any(), eq(10L), any());

                mockMvc.perform(delete("/api/v1/admin/users/10")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.message").value("User permanently deleted"));
        }

        @Test
        @DisplayName("POST /users/{id}/reset-password → 200 with success=true")
        void resetPassword_returns200WithSuccessBody() throws Exception {
                ResetUserPasswordRequest req = new ResetUserPasswordRequest();
                req.setNewPassword("NewPassword1!");

                doNothing().when(adminUserManagementService).resetUserPassword(any(), eq(10L), any());

                mockMvc.perform(post("/api/v1/admin/users/10/reset-password")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("POST /users/{id}/unlock → 200 OK")
        void unlockUser_returns200() throws Exception {
                when(adminUserManagementService.unlockUser(any(), eq(10L), any()))
                                .thenReturn(sampleDTO());

                mockMvc.perform(post("/api/v1/admin/users/10/unlock")
                                .principal(makeAdminAuth(1L))
                                .with(authentication(makeAdminAuth(1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(10));
        }
}
