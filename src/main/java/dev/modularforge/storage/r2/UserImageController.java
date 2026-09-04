package dev.modularforge.storage.r2;

import dev.modularforge.identity.model.User;

import dev.modularforge.shared.error.ForbiddenException;
import dev.modularforge.storage.r2.ImageUploadService;
import dev.modularforge.profile.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
@RestController
@RequestMapping("/api/v1/profile/image")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER')")
@ConditionalOnExpression("${app.modules.image-storage.enabled:false} and ${app.modules.user-profile.enabled:true}")
public class UserImageController {

    private final ImageUploadService imageUploadService;
    private final UserProfileService userProfileService;

    @PostMapping
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getDetails();
            String imageUrl = imageUploadService.uploadProfileImage(file, "USER", userId);
            userProfileService.updateProfilePicture(userId, imageUrl);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile image uploaded successfully",
                    "imageUrl", imageUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading user profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to upload profile image"));
        }
    }

    @PutMapping
    public ResponseEntity<?> updateProfileImage(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getDetails();
            String oldImageUrl = userProfileService.getProfilePictureUrl(userId);
            String imageUrl = imageUploadService.uploadProfileImage(file, "USER", userId);
            userProfileService.updateProfilePicture(userId, imageUrl);
            deleteOldImageAfterSuccessfulReplacement(oldImageUrl, imageUrl);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile image updated successfully",
                    "imageUrl", imageUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating user profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to update profile image"));
        }
    }

    private void deleteOldImageAfterSuccessfulReplacement(String oldImageUrl, String newImageUrl) {
        if (oldImageUrl == null || oldImageUrl.isBlank() || oldImageUrl.equals(newImageUrl)) {
            return;
        }
        try {
            imageUploadService.deleteImage(oldImageUrl);
        } catch (Exception e) {
            log.warn("Could not delete replaced profile image: {}", oldImageUrl, e);
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteProfileImage(
            @RequestParam(value = "imageUrl") String imageUrl,
            Authentication authentication) {
        try {
            Long userId = (Long) authentication.getDetails();
            String currentImageUrl = userProfileService.getProfilePictureUrl(userId);
            if (currentImageUrl == null || !currentImageUrl.equals(imageUrl)) {
                throw new ForbiddenException("You can only delete your own profile image");
            }

            imageUploadService.deleteImage(imageUrl);
            userProfileService.updateProfilePicture(userId, null);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile image removed successfully"));
        } catch (ForbiddenException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting user profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to delete profile image"));
        }
    }
}
