package dev.modularforge.storage.r2;

import dev.modularforge.admin.AdminProfileService;
import dev.modularforge.admin.dto.AdminProfileDTO;
import dev.modularforge.profile.UserProfileService;
import dev.modularforge.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageControllersCoverageTest {

    @Mock ImageUploadService images;
    @Mock AdminProfileService adminProfiles;
    @Mock UserProfileService userProfiles;
    @Mock JwtUtils jwtUtils;
    @Mock MultipartFile file;
    @Mock Authentication authentication;

    private AdminImageController adminController;
    private UserImageController userController;

    @BeforeEach
    void setUp() {
        adminController = new AdminImageController(images, adminProfiles, jwtUtils);
        userController = new UserImageController(images, userProfiles);
    }

    @Test
    void adminUploadMapsSuccessValidationAndUnexpectedFailures() throws Exception {
        when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        when(images.uploadProfileImage(file, "ADMIN", 7L)).thenReturn("new-url");
        assertThat(adminController.uploadProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);

        doThrow(new IllegalArgumentException("bad image")).when(images).uploadProfileImage(file, "ADMIN", 7L);
        assertThat(adminController.uploadProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new IllegalStateException("storage down")).when(images).uploadProfileImage(file, "ADMIN", 7L);
        assertThat(adminController.uploadProfileImage(file, "Bearer jwt").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void adminUpdateCleansOldImageAndToleratesCleanupFailure() throws Exception {
        when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        AdminProfileDTO profile = new AdminProfileDTO();
        profile.setProfilePicture("old-url");
        when(adminProfiles.getAdminProfile(7L)).thenReturn(profile);
        when(images.uploadProfileImage(file, "ADMIN", 7L)).thenReturn("new-url");

        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(images).deleteImage("old-url");

        doThrow(new IllegalStateException("delete failed")).when(images).deleteImage("old-url");
        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminUpdateSkipsMissingBlankAndSameOldImagesAndMapsErrors() throws Exception {
        when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        when(images.uploadProfileImage(file, "ADMIN", 7L)).thenReturn("new-url");
        AdminProfileDTO profile = new AdminProfileDTO();
        when(adminProfiles.getAdminProfile(7L)).thenReturn(profile);
        profile.setProfilePicture(null);
        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);
        profile.setProfilePicture("  ");
        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);
        profile.setProfilePicture("new-url");
        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(images, never()).deleteImage(anyString());

        doThrow(new IllegalArgumentException("bad")).when(images).uploadProfileImage(file, "ADMIN", 7L);
        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(adminProfiles.getAdminProfile(7L)).thenThrow(new IllegalStateException("db"));
        assertThat(adminController.updateProfileImage(file, "Bearer jwt").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void adminDeleteEnforcesOwnershipAndMapsFailures() throws Exception {
        when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        when(adminProfiles.verifyProfileImageOwnership(7L, "url")).thenReturn(false, true);
        assertThat(adminController.deleteProfileImage("url", "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(adminController.deleteProfileImage("url", "Bearer jwt").getStatusCode()).isEqualTo(HttpStatus.OK);

        when(adminProfiles.verifyProfileImageOwnership(7L, "url")).thenThrow(new IllegalStateException("db"));
        assertThat(adminController.deleteProfileImage("url", "Bearer jwt").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void userUploadMapsSuccessValidationAndUnexpectedFailures() throws Exception {
        when(authentication.getDetails()).thenReturn(9L);
        when(images.uploadProfileImage(file, "USER", 9L)).thenReturn("new-url");
        assertThat(userController.uploadProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.OK);

        doThrow(new IllegalArgumentException("bad")).when(images).uploadProfileImage(file, "USER", 9L);
        assertThat(userController.uploadProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        doThrow(new IllegalStateException("storage")).when(images).uploadProfileImage(file, "USER", 9L);
        assertThat(userController.uploadProfileImage(file, authentication).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void userUpdateCoversReplacementGuardsCleanupFailureAndErrors() throws Exception {
        when(authentication.getDetails()).thenReturn(9L);
        when(images.uploadProfileImage(file, "USER", 9L)).thenReturn("new-url");
        when(userProfiles.getProfilePictureUrl(9L)).thenReturn("old-url", null, " ", "new-url", "old-url");
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(images).deleteImage("old-url");
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.OK);
        doThrow(new IllegalStateException("cleanup")).when(images).deleteImage("old-url");
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.OK);

        reset(images, userProfiles);
        when(authentication.getDetails()).thenReturn(9L);
        when(userProfiles.getProfilePictureUrl(9L)).thenReturn("old-url");
        doThrow(new IllegalArgumentException("bad")).when(images).uploadProfileImage(file, "USER", 9L);
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(userProfiles.getProfilePictureUrl(9L)).thenThrow(new IllegalStateException("db"));
        assertThat(userController.updateProfileImage(file, authentication).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void userDeleteEnforcesExactOwnershipAndMapsFailures() throws Exception {
        when(authentication.getDetails()).thenReturn(9L);
        when(userProfiles.getProfilePictureUrl(9L)).thenReturn(null, "other-url", "url", "url");
        assertThat(userController.deleteProfileImage("url", authentication).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userController.deleteProfileImage("url", authentication).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userController.deleteProfileImage("url", authentication).getStatusCode()).isEqualTo(HttpStatus.OK);
        doThrow(new IllegalStateException("storage")).when(images).deleteImage("url");
        assertThat(userController.deleteProfileImage("url", authentication).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
