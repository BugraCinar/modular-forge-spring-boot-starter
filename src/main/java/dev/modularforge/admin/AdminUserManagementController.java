package dev.modularforge.admin;

import dev.modularforge.admin.dto.AdminCreateUserRequest;
import dev.modularforge.admin.dto.AdminUpdateUserRequest;
import dev.modularforge.admin.dto.AdminUserDTO;
import dev.modularforge.admin.dto.AdminUserListResponse;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;

import dev.modularforge.admin.dto.*;
import dev.modularforge.admin.AdminUserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@ConditionalOnProperty(prefix = "app.modules.admin-management", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0Or1()")
public class AdminUserManagementController {

    private final AdminUserManagementService adminUserManagementService;
    @GetMapping
    public ResponseEntity<AdminUserListResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean emailVerified,
            @RequestParam(required = false) String userType,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} listing users — page={}, size={}, search={}", adminId, page, size, search);

        AdminUserListResponse response = adminUserManagementService.getUsers(
                adminId, page, size, sortBy, sortDirection,
                search, isActive, emailVerified, userType, httpRequest);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDTO> getUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} fetching user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.getUser(adminId, userId, httpRequest));
    }

    @PostMapping
    public ResponseEntity<AdminUserDTO> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} creating user with username={}", adminId, request.getUsername());
        AdminUserDTO dto = adminUserManagementService.createUser(adminId, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<AdminUserDTO> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUpdateUserRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} updating user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.updateUser(adminId, userId, request, httpRequest));
    }
    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<AdminUserDTO> deactivateUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} deactivating user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.deactivateUser(adminId, userId, httpRequest));
    }

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<AdminUserDTO> reactivateUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} reactivating user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.reactivateUser(adminId, userId, httpRequest));
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> hardDeleteUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.warn("Admin {} performing hard-delete on user {}", adminId, userId);
        adminUserManagementService.hardDeleteUser(adminId, userId, httpRequest);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User permanently deleted"));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ResetUserPasswordRequest request,
            Authentication authentication) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} resetting password for user {}", adminId, userId);
        adminUserManagementService.resetUserPassword(adminId, userId, request);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password reset successfully"));
    }

    @PostMapping("/{userId}/unlock")
    public ResponseEntity<AdminUserDTO> unlockUser(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} unlocking user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.unlockUser(adminId, userId, httpRequest));
    }
    @PostMapping("/{userId}/toggle-email-verified")
    public ResponseEntity<AdminUserDTO> toggleEmailVerified(
            @PathVariable Long userId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        Long adminId = (Long) authentication.getDetails();
        log.info("Admin {} toggling emailVerified for user {}", adminId, userId);
        return ResponseEntity.ok(adminUserManagementService.toggleEmailVerified(adminId, userId, httpRequest));
    }
}
