package dev.modularforge.admin;

import dev.modularforge.identity.model.Admin;

import dev.modularforge.admin.dto.AdminProfileDTO;
import dev.modularforge.shared.dto.ChangePasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminProfileRequest;
import dev.modularforge.admin.AdminProfileService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "app.modules.admin-management", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/admin/profile")
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0Or1Or2()")
public class AdminProfileController {

    @Autowired
    private AdminProfileService adminProfileService;
    @Autowired
    private dev.modularforge.auth.EmailChangeService emailChanges;

    @PostMapping("/change-email")
    public ResponseEntity<Map<String, Object>> changeEmail(
            @Valid @RequestBody dev.modularforge.shared.dto.ChangeEmailRequest request,
            Authentication authentication) {
        emailChanges.request((Long) authentication.getDetails(), "admin", request.getCurrentPassword(), request.getNewEmail());
        return ResponseEntity.ok(Map.of("success", true, "message", "Verification email sent"));
    }
    @GetMapping
    public ResponseEntity<AdminProfileDTO> getAdminProfile(Authentication authentication) {
        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching their profile", adminId);

        AdminProfileDTO profile = adminProfileService.getAdminProfile(adminId);
        return ResponseEntity.ok(profile);
    }
    @PutMapping
    public ResponseEntity<AdminProfileDTO> updateAdminProfile(
            @Valid @RequestBody UpdateAdminProfileRequest request,
            Authentication authentication) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} updating their profile", adminId);

        AdminProfileDTO profile = adminProfileService.updateAdminProfile(adminId, request);
        return ResponseEntity.ok(profile);
    }
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} changing their password", adminId);

        adminProfileService.changePassword(adminId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Password changed successfully");

        return ResponseEntity.ok(response);
    }
    @PostMapping("/{adminId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateAccount(
            @PathVariable Long adminId,
            Authentication authentication) {

        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} deactivating account of admin {}", requestingAdminId, adminId);

        adminProfileService.deactivateAccount(adminId, requestingAdminId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin account deactivated successfully");

        return ResponseEntity.ok(response);
    }
    @PostMapping("/{adminId}/reactivate")
    public ResponseEntity<Map<String, Object>> reactivateAccount(
            @PathVariable Long adminId,
            Authentication authentication) {

        Long requestingAdminId = (Long) authentication.getDetails();
        log.info("Admin {} reactivating account of admin {}", requestingAdminId, adminId);

        adminProfileService.reactivateAccount(adminId, requestingAdminId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admin account reactivated successfully");

        return ResponseEntity.ok(response);
    }
}
