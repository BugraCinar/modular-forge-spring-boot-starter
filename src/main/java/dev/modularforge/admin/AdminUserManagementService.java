package dev.modularforge.admin;

import dev.modularforge.admin.dto.AdminCreateUserRequest;
import dev.modularforge.admin.dto.AdminUpdateUserRequest;
import dev.modularforge.admin.dto.AdminUserDTO;
import dev.modularforge.admin.dto.AdminUserListResponse;
import dev.modularforge.admin.dto.ResetUserPasswordRequest;
import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.auth.PasswordService;
import dev.modularforge.auth.token.RefreshTokenService;
import dev.modularforge.auth.token.TokenHashService;
import dev.modularforge.shared.notification.NotificationGateway;
import dev.modularforge.shared.PaginationUtils;

import dev.modularforge.admin.dto.*;
import dev.modularforge.shared.error.BadRequestException;
import dev.modularforge.shared.error.ResourceNotFoundException;
import dev.modularforge.identity.model.User;
import dev.modularforge.auth.token.VerificationToken;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.token.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
@Service
@ConditionalOnProperty(prefix = "app.modules.admin-management", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AdminUserManagementService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "username", "email", "firstName", "lastName",
            "isActive", "emailVerified", "userType", "createdAt", "updatedAt", "lastLoginAt");

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordService passwordService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final NotificationGateway emailService;
    private final AdminActivityAudit activityLogger;
    private final TokenHashService tokenHashService;
    public AdminUserListResponse getUsers(
            Long adminId,
            int page, int size,
            String sortBy, String sortDirection,
            String search,
            Boolean isActive, Boolean emailVerified, String userType,
            HttpServletRequest httpRequest) {

        String validatedSortBy = validateSortField(sortBy, "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by(direction, validatedSortBy));

        UserType userTypeEnum = null;
        if (userType != null && !userType.isBlank()) {
            try {
                userTypeEnum = UserType.valueOf(userType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid userType value: " + userType);
            }
        }

        String searchTrimmed = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<User> userPage = userRepository.findWithFilters(
                searchTrimmed, isActive, emailVerified, userTypeEnum, pageable);

        List<AdminUserDTO> dtos = userPage.getContent().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("search", search);
        details.put("isActive", isActive);
        details.put("emailVerified", emailVerified);
        details.put("userType", userType);
        details.put("resultCount", dtos.size());
        details.put("totalItems", userPage.getTotalElements());
        activityLogger.logActivity(adminId, "READ", "User", "list", details, httpRequest);

        return AdminUserListResponse.builder()
                .users(dtos)
                .currentPage(userPage.getNumber())
                .totalPages(userPage.getTotalPages())
                .totalItems(userPage.getTotalElements())
                .pageSize(userPage.getSize())
                .build();
    }

    public AdminUserDTO getUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        Map<String, Object> details = new HashMap<>();
        details.put("targetUsername", user.getUsername());
        activityLogger.logActivity(adminId, "READ", "User", targetUserId.toString(), details, httpRequest);

        return mapToDTO(user);
    }

    @Transactional
    public AdminUserDTO createUser(Long adminId, AdminCreateUserRequest request, HttpServletRequest httpRequest) {
        if (userRepository.existsByUsername(request.getUsername())
                || adminRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())
                || adminRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(request.getPassword(), salt);

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(hash);
        user.setSalt(salt);
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setBio(request.getBio());
        user.setUserType(request.getUserType() != null ? request.getUserType() : UserType.APP_USER);
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setAdminDeactivated(false);

        boolean skipVerification = Boolean.TRUE.equals(request.getSkipEmailVerification());
        user.setEmailVerified(skipVerification);

        user = userRepository.save(user);
        if (!skipVerification) {
            VerificationToken token = createVerificationToken(user.getId(), "user");
            verificationTokenRepository.save(token);
            String displayName = user.getFirstName() != null ? user.getFirstName() : user.getUsername();
            emailService.sendVerificationEmail(user.getEmail(), token.getToken(), displayName);
        }

        log.info("User created by admin {} with userId={}", adminId, user.getId());

        Map<String, Object> details = new HashMap<>();
        details.put("username", user.getUsername());
        details.put("email", user.getEmail());
        details.put("userType", user.getUserType().name());
        activityLogger.logCreate(adminId, "user", user.getId().toString(), details, httpRequest);

        return mapToDTO(user);
    }

    @Transactional
    public AdminUserDTO updateUser(Long adminId, Long targetUserId,
            AdminUpdateUserRequest request,
            HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        Map<String, Object> changes = new HashMap<>();

        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())
                    || adminRepository.existsByEmail(request.getEmail())) {
                throw new BadRequestException("Email is already in use");
            }
            changes.put("email", Map.of("old", user.getEmail(), "new", request.getEmail()));
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);
            user.setPendingEmail(null);
        }

        if (request.getFirstName() != null) {
            changes.put("firstName", Map.of("old", nullSafe(user.getFirstName()), "new", request.getFirstName()));
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            changes.put("lastName", Map.of("old", nullSafe(user.getLastName()), "new", request.getLastName()));
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getUserType() != null) {
            changes.put("userType", Map.of("old", user.getUserType().name(), "new", request.getUserType().name()));
            user.setUserType(request.getUserType());
        }
        if (request.getEmailVerified() != null) {
            changes.put("emailVerified", Map.of("old", user.getEmailVerified(), "new", request.getEmailVerified()));
            user.setEmailVerified(request.getEmailVerified());
        }

        user = userRepository.save(user);
        log.info("User {} updated by admin {}", targetUserId, adminId);

        activityLogger.logUpdate(adminId, "user", targetUserId.toString(), changes, httpRequest);
        return mapToDTO(user);
    }
    @Transactional
    public AdminUserDTO deactivateUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        if (!user.getIsActive()) {
            throw new BadRequestException("User account is already inactive");
        }

        user.setIsActive(false);
        user.setAdminDeactivated(true);
        user.setDeactivatedAt(LocalDateTime.now());
        user.invalidateAccessTokens();
        userRepository.save(user);

        int revoked = refreshTokenService.revokeAllUserTokens(targetUserId, "user");
        log.info("User {} deactivated by admin {}; {} refresh tokens revoked", targetUserId, adminId, revoked);

        activityLogger.logDeactivate(adminId, "user", targetUserId.toString(), httpRequest);
        return mapToDTO(user);
    }
    @Transactional
    public AdminUserDTO reactivateUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);

        if (user.getIsActive()) {
            throw new BadRequestException("User account is already active");
        }

        user.setIsActive(true);
        user.setAdminDeactivated(false);
        user.setDeactivatedAt(null);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user = userRepository.save(user);
        log.info("User {} reactivated by admin {}", targetUserId, adminId);

        activityLogger.logActivate(adminId, "user", targetUserId.toString(), httpRequest);
        return mapToDTO(user);
    }
    @Transactional
    public void hardDeleteUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        refreshTokenService.revokeAllUserTokens(targetUserId, "user");
        verificationTokenRepository.deleteByUserIdAndRole(targetUserId, "user");
        verificationTokenRepository.deleteByUserIdAndRole(targetUserId, "email_change");

        userRepository.delete(user);
        log.warn("User {} HARD-DELETED by admin {}", targetUserId, adminId);

        activityLogger.logDelete(adminId, "user", targetUserId.toString(), httpRequest);
    }

    @Transactional
    public void resetUserPassword(Long adminId, Long targetUserId,
            ResetUserPasswordRequest request) {
        User user = findUserById(targetUserId);

        String newSalt = passwordService.generateSalt();
        String newHash = passwordService.hashPassword(request.getNewPassword(), newSalt);
        user.setSalt(newSalt);
        user.setPasswordHash(newHash);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.invalidateAccessTokens();

        userRepository.save(user);
        int revoked = refreshTokenService.revokeAllUserTokens(targetUserId, "user");
        log.info("Password reset for userId={} by admin {}; {} refresh tokens revoked", targetUserId, adminId,
            revoked);
    }

    @Transactional
    public AdminUserDTO unlockUser(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user = userRepository.save(user);
        log.info("User {} unlocked by admin {}", targetUserId, adminId);

        Map<String, Object> details = new HashMap<>();
        details.put("action", "unlocked");
        activityLogger.logActivity(adminId, "UNLOCK", "User", targetUserId.toString(), details, httpRequest);
        return mapToDTO(user);
    }

    @Transactional
    public AdminUserDTO toggleEmailVerified(Long adminId, Long targetUserId, HttpServletRequest httpRequest) {
        User user = findUserById(targetUserId);
        boolean newValue = !Boolean.TRUE.equals(user.getEmailVerified());
        user.setEmailVerified(newValue);
        user = userRepository.save(user);
        log.info("emailVerified toggled to {} for userId={} by admin {}", newValue, targetUserId, adminId);

        Map<String, Object> details = new HashMap<>();
        details.put("emailVerified", newValue);
        activityLogger.logActivity(adminId, "UPDATE", "User", targetUserId.toString(), details, httpRequest);
        return mapToDTO(user);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    public AdminUserDTO mapToDTO(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .profilePicture(user.getProfilePicture())
                .phone(user.getPhone())
                .bio(user.getBio())
                .userType(user.getUserType() != null
                        ? user.getUserType().name().toLowerCase(Locale.ROOT)
                        : null)
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .adminDeactivated(user.getAdminDeactivated())
                .loginAttempts(user.getLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .deactivatedAt(user.getDeactivatedAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private String validateSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isBlank())
            return defaultField;
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }
        return sortBy;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private VerificationToken createVerificationToken(Long userId, String role) {
        String rawToken = tokenHashService.generateToken();
        VerificationToken token = new VerificationToken(userId, role);
        token.storeTokenMetadata(rawToken, tokenHashService.hashToken(rawToken), tokenHashService.preview(rawToken));
        return token;
    }
}
