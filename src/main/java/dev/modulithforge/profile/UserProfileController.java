package dev.modulithforge.profile;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.admin.dto.ChangePasswordRequest;
import dev.modulithforge.profile.dto.ChangeEmailRequest;
import dev.modulithforge.profile.dto.DeactivateAccountRequest;
import dev.modulithforge.profile.dto.UpdateUserProfileRequest;
import dev.modulithforge.profile.dto.UserProfileDTO;
import dev.modulithforge.profile.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@ConditionalOnProperty(prefix = "app.modules.user-profile", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER')")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileDTO> getProfile(Authentication authentication) {
        Long userId = (Long) authentication.getDetails();
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }

    @PutMapping
    public ResponseEntity<UserProfileDTO> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        return ResponseEntity.ok(userProfileService.updateProfile(userId, request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        userProfileService.changePassword(userId, request);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
    }
    @PostMapping("/change-email")
    public ResponseEntity<Map<String, Object>> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        userProfileService.requestEmailChange(userId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Verification email sent to " + request.getNewEmail()
                        + ". Please click the link to confirm the change."));
    }
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deactivateAccount(
            @Valid @RequestBody DeactivateAccountRequest request,
            Authentication authentication) {

        Long userId = (Long) authentication.getDetails();
        log.info("User {} requested self-deactivation", userId);
        userProfileService.deactivateAccount(userId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Account deactivated. You may reactivate it by logging in within 30 days."
        ));
    }
}
