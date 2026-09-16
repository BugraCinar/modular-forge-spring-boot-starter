package dev.modularforge.controllers;

import dev.modularforge.identity.model.User;
import dev.modularforge.storage.r2.UserImageController;

import dev.modularforge.profile.dto.UserProfileDTO;
import dev.modularforge.storage.r2.ImageUploadService;
import dev.modularforge.profile.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserImageController.class, properties = {
        "app.modules.image-storage.enabled=true",
        "app.modules.user-profile.enabled=true"
})
@AutoConfigureMockMvc(addFilters = false)
class UserImageControllerTest extends BaseControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean ImageUploadService imageUploadService;
    @MockitoBean UserProfileService userProfileService;

    private static final String IMAGE_URL = "https://cdn.example.com/users/1/profile.jpg";

    private UserProfileDTO sampleProfile(String imageUrl) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(1L);
        dto.setUsername("testUser");
        dto.setProfilePicture(imageUrl);
        return dto;
    }

    @Test
    @DisplayName("POST /profile/image → 200 with imageUrl in response")
    void uploadProfileImage_returns200WithImageUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        when(imageUploadService.uploadProfileImage(any(), eq("USER"), eq(1L))).thenReturn(IMAGE_URL);
        when(userProfileService.updateProfilePicture(eq(1L), eq(IMAGE_URL))).thenReturn(sampleProfile(IMAGE_URL));

        mockMvc.perform(multipart("/api/v1/profile/image")
                        .file(file)
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.imageUrl").value(IMAGE_URL))
                .andExpect(jsonPath("$.message").value("Profile image uploaded successfully"));
    }

    @Test
    @DisplayName("POST /profile/image → 400 when ImageUploadService rejects file")
    void uploadProfileImage_returns400WhenFileInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.php", "image/jpeg", "<?php echo 'hack'; ?>".getBytes());

        when(imageUploadService.uploadProfileImage(any(), eq("USER"), eq(1L)))
                .thenThrow(new IllegalArgumentException("Invalid file type"));

        mockMvc.perform(multipart("/api/v1/profile/image")
                        .file(file)
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid file type"));
    }

    @Test
    @DisplayName("PUT /profile/image → 200 with new imageUrl")
    void updateProfileImage_returns200() throws Exception {
        String newUrl = "https://cdn.example.com/users/1/profile-v2.jpg";
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo2.jpg", "image/jpeg", "new-image-bytes".getBytes());

        when(userProfileService.getProfilePictureUrl(eq(1L))).thenReturn(IMAGE_URL);
        when(imageUploadService.uploadProfileImage(any(), eq("USER"), eq(1L))).thenReturn(newUrl);
        when(userProfileService.updateProfilePicture(eq(1L), eq(newUrl))).thenReturn(sampleProfile(newUrl));
        doNothing().when(imageUploadService).deleteProfileImage(IMAGE_URL, "user", 1L);

        mockMvc.perform(multipart("/api/v1/profile/image")
                        .file(file)
                        .param("oldImageUrl", IMAGE_URL)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.imageUrl").value(newUrl));

        verify(imageUploadService).deleteProfileImage(IMAGE_URL, "user", 1L);
    }

    @Test
    @DisplayName("DELETE /profile/image → 200 with success=true")
    void deleteProfileImage_returns200() throws Exception {
        when(userProfileService.getProfilePictureUrl(eq(1L))).thenReturn(IMAGE_URL);
        doNothing().when(imageUploadService).deleteProfileImage(IMAGE_URL, "user", 1L);
        when(userProfileService.updateProfilePicture(eq(1L), isNull())).thenReturn(sampleProfile(null));

        mockMvc.perform(delete("/api/v1/profile/image")
                        .param("imageUrl", IMAGE_URL)
                        .principal(makeUserAuth(1L))
                        .with(authentication(makeUserAuth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile image removed successfully"));
    }
}
