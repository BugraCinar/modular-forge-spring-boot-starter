package dev.modulithforge.storage.r2;

import dev.modulithforge.security.AdminLevelAuthorizationService;
import dev.modulithforge.identity.model.Admin;

import dev.modulithforge.security.JwtUtils;
import dev.modulithforge.admin.AdminProfileService;
import dev.modulithforge.storage.r2.ImageUploadService;
import dev.modulithforge.shared.web.ApiRoutes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping(ApiRoutes.ADMIN_IMAGE)
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0Or1Or2()")
@ConditionalOnExpression("${app.modules.image-storage.enabled:false} and ${app.modules.admin-management.enabled:true}")
public class AdminImageController {

    private final ImageUploadService imageUploadService;
    private final AdminProfileService adminProfileService;
    private final JwtUtils jwtUtils;
    @PostMapping("/profile")
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        try {
            Long adminId = jwtUtils.extractUserId(token.substring(7)).longValue();

            String imageUrl = imageUploadService.uploadProfileImage(file, "ADMIN", adminId);
            adminProfileService.updateProfilePicture(adminId, imageUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil resmi başarıyla yüklendi",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading admin profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Profil resmi yüklenirken hata oluştu"
            ));
        }
    }
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        try {
            Long adminId = jwtUtils.extractUserId(token.substring(7)).longValue();
            String oldImageUrl = adminProfileService.getAdminProfile(adminId).getProfilePicture();
            String imageUrl = imageUploadService.uploadProfileImage(file, "ADMIN", adminId);
            adminProfileService.updateProfilePicture(adminId, imageUrl);
            deleteOldImageAfterSuccessfulReplacement(oldImageUrl, imageUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil resmi başarıyla güncellendi",
                    "imageUrl", imageUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error updating admin profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Profil resmi güncellenirken hata oluştu"
            ));
        }
    }

    private void deleteOldImageAfterSuccessfulReplacement(String oldImageUrl, String newImageUrl) {
        if (oldImageUrl == null || oldImageUrl.isBlank() || oldImageUrl.equals(newImageUrl)) {
            return;
        }
        try {
            imageUploadService.deleteImage(oldImageUrl);
        } catch (Exception e) {
            log.warn("Could not delete replaced admin profile image: {}", oldImageUrl, e);
        }
    }
    @DeleteMapping("/profile")
    public ResponseEntity<?> deleteProfileImage(
            @RequestParam("imageUrl") String imageUrl,
            @RequestHeader("Authorization") String token) {
        try {
            Long adminId = jwtUtils.extractUserId(token.substring(7)).longValue();
            if (!adminProfileService.verifyProfileImageOwnership(adminId, imageUrl)) {
                log.warn("Admin {} attempted to delete profile image they don't own: {}", adminId, imageUrl);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "success", false,
                        "message", "Bu profil resmini silme yetkiniz yok"
                ));
            }

            imageUploadService.deleteImage(imageUrl);
            adminProfileService.updateProfilePicture(adminId, null);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil resmi başarıyla silindi"
            ));
        } catch (Exception e) {
            log.error("Error deleting admin profile image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Profil resmi silinirken hata oluştu"
            ));
        }
    }
}
