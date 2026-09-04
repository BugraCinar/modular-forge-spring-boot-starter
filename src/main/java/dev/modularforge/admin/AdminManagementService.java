package dev.modularforge.admin;

import dev.modularforge.admin.dto.AdminListResponse;
import dev.modularforge.admin.dto.AdminManagementDTO;
import dev.modularforge.admin.dto.CreateAdminRequest;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.admin.dto.UpdateAdminRequest;
import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.shared.PaginationUtils;

import dev.modularforge.admin.dto.*;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.shared.error.UnauthorizedException;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.modules.admin-management", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AdminManagementService {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "username", "email", "firstName", "lastName",
        "level", "isActive", "createdAt", "updatedAt", "lastLoginAt"
    );

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AdminActivityAudit activityLogger;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;
    private void validateAdminPermission(Admin requestingAdmin, Integer targetLevel, String action) {
        int requestingLevel = requestingAdmin.getLevel();

        log.debug("Permission check - Requesting admin level: {}, Target level: {}, Action: {}",
            requestingLevel, targetLevel, action);
        if (requestingLevel == 0) {
            return;
        }
        if (requestingLevel == 1 && targetLevel == 2) {
            return;
        }
        throw new UnauthorizedException(
            String.format("Admin level %d cannot %s admin level %d. Insufficient permissions.",
                requestingLevel, action, targetLevel));
    }
    private void validateAdminPermission(Admin requestingAdmin, Admin targetAdmin, String action) {
        int requestingLevel = requestingAdmin.getLevel();
        int targetLevel = targetAdmin.getLevel();

        log.debug("Permission check - Requesting admin level: {}, Target level: {}, Action: {}",
            requestingLevel, targetLevel, action);
        if (requestingAdmin.getId().equals(targetAdmin.getId()) &&
            (action.equals("delete") || action.equals("deactivate"))) {
            throw new BadRequestException("Cannot " + action + " your own account");
        }
        if (requestingLevel == 0) {
            if (targetLevel == 0 && !requestingAdmin.getId().equals(targetAdmin.getId())) {
                if (action.equals("delete") || action.equals("deactivate")) {
                    throw new BadRequestException("Cannot " + action + " another super admin account");
                }
            }
            return;
        }
        if (requestingLevel == 1 && targetLevel == 2) {
            return;
        }
        throw new UnauthorizedException(
            String.format("Admin level %d cannot %s admin level %d. Insufficient permissions.",
                requestingLevel, action, targetLevel));
    }
    @Transactional(readOnly = true)
    public AdminListResponse getAllAdmins(Long requestingAdminId, int page, int size,
                                          String sortBy, String sortDirection, HttpServletRequest httpRequest) {
        log.info("Admin {} fetching admins - page: {}, size: {}, sortBy: {}, direction: {}",
            requestingAdminId, page, size, sortBy, sortDirection);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + requestingAdminId));
        String validatedSortBy = validateSortField(sortBy, "createdAt");

        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
            Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by(direction, validatedSortBy));
        Page<Admin> adminPage;
        if (requestingAdmin.getLevel() == 0) {
            adminPage = adminRepository.findAll(pageable);
        }
        else if (requestingAdmin.getLevel() == 1) {
            adminPage = adminRepository.findByLevelGreaterThanEqual(1, pageable);
        }
        else {
            adminPage = adminRepository.findByLevel(2, pageable);
        }

        List<AdminManagementDTO> admins = adminPage.getContent().stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        details.put("resultCount", admins.size());
        details.put("totalItems", adminPage.getTotalElements());
        activityLogger.logActivity(requestingAdminId, "READ", "Admin", "list", details, httpRequest);

        return AdminListResponse.builder()
            .admins(admins)
            .currentPage(adminPage.getNumber())
            .totalPages(adminPage.getTotalPages())
            .totalItems(adminPage.getTotalElements())
            .pageSize(adminPage.getSize())
            .build();
    }
    @Transactional(readOnly = true)
    public AdminManagementDTO getAdminById(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} fetching admin {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        if (requestingAdmin.getLevel() > targetAdmin.getLevel() &&
            !requestingAdmin.getId().equals(targetAdmin.getId())) {
            throw new UnauthorizedException("You don't have permission to view this admin");
        }
        Map<String, Object> details = new HashMap<>();
        details.put("targetAdminLevel", targetAdmin.getLevel());
        details.put("targetAdminUsername", targetAdmin.getUsername());
        activityLogger.logActivity(requestingAdminId, "READ", "Admin", targetAdminId.toString(), details, httpRequest);

        return mapToDTO(targetAdmin);
    }
    @Transactional
    public AdminManagementDTO createAdmin(Long requestingAdminId, CreateAdminRequest request, HttpServletRequest httpRequest) {
        log.info("Admin {} creating new admin with level {}", requestingAdminId, request.getLevel());

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));
        validateAdminPermission(requestingAdmin, request.getLevel(), "create");
        if (request.getLevel() == 0) {
            throw new BadRequestException("Cannot create super admin via API. Super admins must be created manually.");
        }
        if (userRepository.existsByUsername(request.getUsername()) ||
            adminRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail()) ||
            adminRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }
        String salt = passwordService.generateSalt();
        String hashedPassword = passwordService.hashPassword(request.getPassword(), salt);

        Admin admin = new Admin();
        admin.setUsername(request.getUsername());
        admin.setEmail(request.getEmail());
        admin.setPasswordHash(hashedPassword);
        admin.setSalt(salt);
        admin.setFirstName(request.getFirstName());
        admin.setLastName(request.getLastName());
        admin.setProfilePicture(request.getProfilePicture());
        admin.setLevel(request.getLevel());
        admin.setPermissions(request.getPermissions());
        admin.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        admin.setCreatedBy(requestingAdminId);

        admin = adminRepository.save(admin);
        log.info("Admin created successfully with ID: {}", admin.getId());
        Map<String, Object> details = new HashMap<>();
        details.put("username", admin.getUsername());
        details.put("email", admin.getEmail());
        details.put("level", admin.getLevel());
        activityLogger.logCreate(requestingAdminId, "admin", admin.getId().toString(), details, httpRequest);

        return mapToDTO(admin);
    }
    @Transactional
    public AdminManagementDTO updateAdmin(Long requestingAdminId, Long targetAdminId,
                                          UpdateAdminRequest request, HttpServletRequest httpRequest) {
        log.info("Admin {} updating admin {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        validateAdminPermission(requestingAdmin, targetAdmin, "update");
        Map<String, Object> changes = new HashMap<>();
        if (request.getEmail() != null && !request.getEmail().equals(targetAdmin.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail()) ||
                adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            changes.put("email", Map.of("old", targetAdmin.getEmail(), "new", request.getEmail()));
            targetAdmin.setEmail(request.getEmail());
        }
        if (request.getFirstName() != null) {
            changes.put("firstName", Map.of("old", targetAdmin.getFirstName(), "new", request.getFirstName()));
            targetAdmin.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            changes.put("lastName", Map.of("old", targetAdmin.getLastName(), "new", request.getLastName()));
            targetAdmin.setLastName(request.getLastName());
        }

        if (request.getProfilePicture() != null) {
            targetAdmin.setProfilePicture(request.getProfilePicture());
        }
        if (request.getLevel() != null && !request.getLevel().equals(targetAdmin.getLevel())) {
            if (request.getLevel() == 0 || targetAdmin.getLevel() == 0) {
                throw new BadRequestException("Cannot change super admin level via API");
            }
            validateAdminPermission(requestingAdmin, request.getLevel(), "assign level to");
            changes.put("level", Map.of("old", targetAdmin.getLevel(), "new", request.getLevel()));
            targetAdmin.setLevel(request.getLevel());
        }

        if (request.getPermissions() != null) {
            targetAdmin.setPermissions(request.getPermissions());
        }

        boolean deactivatedByUpdate = Boolean.TRUE.equals(targetAdmin.getIsActive())
                && Boolean.FALSE.equals(request.getIsActive());
        if (request.getIsActive() != null) {
            changes.put("isActive", Map.of("old", targetAdmin.getIsActive(), "new", request.getIsActive()));
            targetAdmin.setIsActive(request.getIsActive());
        }
        if (deactivatedByUpdate) {
            targetAdmin.invalidateAccessTokens();
        }

        targetAdmin = adminRepository.save(targetAdmin);
        if (Boolean.FALSE.equals(targetAdmin.getIsActive())) {
            int revoked = refreshTokenService.revokeAllUserTokens(targetAdminId, "admin");
            log.info("Admin {} was updated to inactive; {} refresh tokens revoked", targetAdminId, revoked);
        }
        log.info("Admin updated successfully: {}", targetAdminId);
        activityLogger.logUpdate(requestingAdminId, "admin", targetAdminId.toString(), changes, httpRequest);

        return mapToDTO(targetAdmin);
    }
    @Transactional
    public void deleteAdmin(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} deleting (deactivating) admin {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        validateAdminPermission(requestingAdmin, targetAdmin, "delete");
        if (targetAdmin.getLevel() == 0) {
            throw new BadRequestException("Cannot delete super admin account");
        }

        targetAdmin.setIsActive(false);
        targetAdmin.invalidateAccessTokens();
        adminRepository.save(targetAdmin);
        int revoked = refreshTokenService.revokeAllUserTokens(targetAdminId, "admin");

        log.info("Admin deactivated successfully: {}; {} refresh tokens revoked", targetAdminId, revoked);
        activityLogger.logDelete(requestingAdminId, "admin", targetAdminId.toString(), httpRequest);
    }
    @Transactional
    public AdminManagementDTO activateAdmin(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} activating admin {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        validateAdminPermission(requestingAdmin, targetAdmin, "activate");

        targetAdmin.setIsActive(true);
        targetAdmin.setLoginAttempts(0);
        targetAdmin.setLockedUntil(null);

        targetAdmin = adminRepository.save(targetAdmin);
        log.info("Admin activated successfully: {}", targetAdminId);
        activityLogger.logActivate(requestingAdminId, "admin", targetAdminId.toString(), httpRequest);

        return mapToDTO(targetAdmin);
    }
    @Transactional
    public AdminManagementDTO deactivateAdmin(Long requestingAdminId, Long targetAdminId, HttpServletRequest httpRequest) {
        log.info("Admin {} deactivating admin {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        validateAdminPermission(requestingAdmin, targetAdmin, "deactivate");
        if (targetAdmin.getLevel() == 0) {
            throw new BadRequestException("Cannot deactivate super admin account");
        }

        targetAdmin.setIsActive(false);
        targetAdmin.invalidateAccessTokens();
        targetAdmin = adminRepository.save(targetAdmin);
        int revoked = refreshTokenService.revokeAllUserTokens(targetAdminId, "admin");
        log.info("Admin deactivated successfully: {}; {} refresh tokens revoked", targetAdminId, revoked);
        activityLogger.logDeactivate(requestingAdminId, "admin", targetAdminId.toString(), httpRequest);

        return mapToDTO(targetAdmin);
    }
    @Transactional
    public void resetAdminPassword(Long requestingAdminId, Long targetAdminId,
                                   ResetUserPasswordRequest request) {
        log.info("Admin {} resetting password for admin {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        validateAdminPermission(requestingAdmin, targetAdmin, "reset password for");
        String newSalt = passwordService.generateSalt();
        String newHashedPassword = passwordService.hashPassword(request.getNewPassword(), newSalt);

        targetAdmin.setSalt(newSalt);
        targetAdmin.setPasswordHash(newHashedPassword);
        targetAdmin.setLoginAttempts(0);
        targetAdmin.setLockedUntil(null);
        targetAdmin.invalidateAccessTokens();

        adminRepository.save(targetAdmin);
        int revoked = refreshTokenService.revokeAllUserTokens(targetAdminId, "admin");
        log.info("Password reset successfully for admin: {}; {} refresh tokens revoked", targetAdminId, revoked);
    }
    @Transactional
    public AdminManagementDTO unlockAdmin(Long requestingAdminId, Long targetAdminId) {
        log.info("Admin {} unlocking admin account {}", requestingAdminId, targetAdminId);

        Admin requestingAdmin = adminRepository.findById(requestingAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Requesting admin not found"));

        Admin targetAdmin = adminRepository.findById(targetAdminId)
            .orElseThrow(() -> new ResourceNotFoundException("Admin not found with ID: " + targetAdminId));
        validateAdminPermission(requestingAdmin, targetAdmin, "unlock");

        targetAdmin.setLoginAttempts(0);
        targetAdmin.setLockedUntil(null);

        targetAdmin = adminRepository.save(targetAdmin);
        log.info("Admin account unlocked successfully: {}", targetAdminId);

        return mapToDTO(targetAdmin);
    }
    private AdminManagementDTO mapToDTO(Admin admin) {
        return AdminManagementDTO.builder()
            .id(admin.getId())
            .username(admin.getUsername())
            .email(admin.getEmail())
            .firstName(admin.getFirstName())
            .lastName(admin.getLastName())
            .profilePicture(admin.getProfilePicture())
            .level(admin.getLevel())
            .permissions(admin.getPermissions() == null ? List.of() : List.copyOf(admin.getPermissions()))
            .isActive(admin.getIsActive())
            .loginAttempts(admin.getLoginAttempts())
            .lockedUntil(admin.getLockedUntil())
            .createdBy(admin.getCreatedBy())
            .createdAt(admin.getCreatedAt())
            .updatedAt(admin.getUpdatedAt())
            .lastLoginAt(admin.getLastLoginAt())
            .build();
    }
    private String validateSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }

        return sortBy;
    }
}
