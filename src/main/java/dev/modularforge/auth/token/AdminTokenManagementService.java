package dev.modularforge.auth.token;

import dev.modularforge.shared.audit.AdminActivityAudit;
import dev.modularforge.shared.PaginationUtils;

import dev.modularforge.auth.token.dto.PasswordResetTokenDTO;
import dev.modularforge.auth.token.dto.TokenListResponse;
import dev.modularforge.auth.token.dto.VerificationTokenDTO;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.auth.token.PasswordResetToken;
import dev.modularforge.identity.model.User;
import dev.modularforge.auth.token.VerificationToken;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.auth.token.PasswordResetTokenRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.auth.token.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTokenManagementService {
    private static final Set<String> ALLOWED_PASSWORD_RESET_SORT_FIELDS = Set.of(
        "id", "token", "tokenHash", "tokenPreview", "userId", "role", "expiryDate",
        "createdDate", "attemptCount", "requestingIp"
    );

    private static final Set<String> ALLOWED_VERIFICATION_SORT_FIELDS = Set.of(
        "id", "token", "tokenHash", "tokenPreview", "userId", "role", "expiryDate",
        "createdDate", "usedDate", "used"
    );

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final TokenHashService tokenHashService;

    @Autowired
    private AdminActivityAudit adminActivityLogger;
    @Transactional(readOnly = true)
    public TokenListResponse<PasswordResetTokenDTO> getAllPasswordResetTokens(
            String role,
            Boolean includeExpired,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long adminId,
            HttpServletRequest httpRequest
    ) {
        String validatedSortBy = validatePasswordResetSortField(sortBy, "createdDate");

        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by(direction, validatedSortBy));

        Page<PasswordResetToken> tokenPage;

        if (role != null && !role.isEmpty()) {
            tokenPage = passwordResetTokenRepository.findByRoleOrderByCreatedDateDesc(role, pageable);
        } else if (Boolean.FALSE.equals(includeExpired)) {
            tokenPage = passwordResetTokenRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(
                    LocalDateTime.now(), pageable);
        } else {
            tokenPage = passwordResetTokenRepository.findAllByOrderByCreatedDateDesc(pageable);
        }

        List<PasswordResetTokenDTO> tokenDTOs = tokenPage.getContent().stream()
                .map(this::convertPasswordResetTokenToDTO)
                .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (role != null) details.put("role", role);
        if (includeExpired != null) details.put("includeExpired", includeExpired);
        details.put("resultCount", tokenDTOs.size());
        details.put("totalElements", tokenPage.getTotalElements());

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "PasswordResetToken",
                "list",
                details,
                httpRequest
        );

        return TokenListResponse.<PasswordResetTokenDTO>builder()
                .tokens(tokenDTOs)
                .currentPage(tokenPage.getNumber())
                .totalPages(tokenPage.getTotalPages())
                .totalElements(tokenPage.getTotalElements())
                .pageSize(tokenPage.getSize())
                .hasNext(tokenPage.hasNext())
                .hasPrevious(tokenPage.hasPrevious())
                .build();
    }
    @Transactional(readOnly = true)
    public PasswordResetTokenDTO getPasswordResetTokenById(Long tokenId, Long adminId, HttpServletRequest httpRequest) {
        PasswordResetToken token = passwordResetTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Password reset token not found with ID: " + tokenId));
        Map<String, Object> details = new HashMap<>();
        details.put("tokenId", tokenId);
        details.put("userId", token.getUserId());
        details.put("role", token.getRole());
        details.put("isExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "PasswordResetToken",
                tokenId.toString(),
                details,
                httpRequest
        );

        return convertPasswordResetTokenToDTO(token);
    }
    @Transactional(readOnly = true)
    public TokenListResponse<VerificationTokenDTO> getAllVerificationTokens(
            String role,
            Boolean includeExpired,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            Long adminId,
            HttpServletRequest httpRequest
    ) {
        String validatedSortBy = validateVerificationSortField(sortBy, "createdDate");

        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PaginationUtils.pageRequest(page, size, Sort.by(direction, validatedSortBy));

        Page<VerificationToken> tokenPage;

        if (role != null && !role.isEmpty()) {
            tokenPage = verificationTokenRepository.findByRoleOrderByCreatedDateDesc(role, pageable);
        } else if (Boolean.FALSE.equals(includeExpired)) {
            tokenPage = verificationTokenRepository.findByExpiryDateBeforeOrderByCreatedDateDesc(
                    LocalDateTime.now(), pageable);
        } else {
            tokenPage = verificationTokenRepository.findAllByOrderByCreatedDateDesc(pageable);
        }

        List<VerificationTokenDTO> tokenDTOs = tokenPage.getContent().stream()
                .map(this::convertVerificationTokenToDTO)
                .collect(Collectors.toList());
        Map<String, Object> details = new HashMap<>();
        details.put("page", page);
        details.put("size", size);
        details.put("sortBy", sortBy);
        details.put("sortDirection", sortDirection);
        if (role != null) details.put("role", role);
        if (includeExpired != null) details.put("includeExpired", includeExpired);
        details.put("resultCount", tokenDTOs.size());
        details.put("totalElements", tokenPage.getTotalElements());

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "VerificationToken",
                "list",
                details,
                httpRequest
        );

        return TokenListResponse.<VerificationTokenDTO>builder()
                .tokens(tokenDTOs)
                .currentPage(tokenPage.getNumber())
                .totalPages(tokenPage.getTotalPages())
                .totalElements(tokenPage.getTotalElements())
                .pageSize(tokenPage.getSize())
                .hasNext(tokenPage.hasNext())
                .hasPrevious(tokenPage.hasPrevious())
                .build();
    }
    @Transactional(readOnly = true)
    public VerificationTokenDTO getVerificationTokenById(Long tokenId, Long adminId, HttpServletRequest httpRequest) {
        VerificationToken token = verificationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Verification token not found with ID: " + tokenId));
        Map<String, Object> details = new HashMap<>();
        details.put("tokenId", tokenId);
        details.put("userId", token.getUserId());
        details.put("role", token.getRole());
        details.put("isExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));

        adminActivityLogger.logActivity(
                adminId,
                "READ",
                "VerificationToken",
                tokenId.toString(),
                details,
                httpRequest
        );

        return convertVerificationTokenToDTO(token);
    }
    @Transactional
    public void deletePasswordResetToken(Long tokenId, Long adminId, HttpServletRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Password reset token not found with ID: " + tokenId));
        Map<String, Object> details = new HashMap<>();
        details.put("role", token.getRole());
        details.put("userId", token.getUserId());
        details.put("expiryDate", token.getExpiryDate().toString());
        details.put("wasExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));

        passwordResetTokenRepository.deleteById(tokenId);
        adminActivityLogger.logActivity(adminId, "DELETE", "PasswordResetToken", tokenId.toString(), details, request);

        log.info("Deleted password reset token with ID: {} by admin {}", tokenId, adminId);
    }
    @Transactional
    public void deleteVerificationToken(Long tokenId, Long adminId, HttpServletRequest request) {
        VerificationToken token = verificationTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Verification token not found with ID: " + tokenId));
        Map<String, Object> details = new HashMap<>();
        details.put("role", token.getRole());
        details.put("userId", token.getUserId());
        details.put("expiryDate", token.getExpiryDate().toString());
        details.put("wasExpired", token.getExpiryDate().isBefore(LocalDateTime.now()));

        verificationTokenRepository.deleteById(tokenId);
        adminActivityLogger.logActivity(adminId, "DELETE", "VerificationToken", tokenId.toString(), details, request);

        log.info("Deleted verification token with ID: {} by admin {}", tokenId, adminId);
    }
    @Transactional
    public int deleteExpiredPasswordResetTokens(Long adminId, HttpServletRequest request) {
        Page<PasswordResetToken> expiredTokens = passwordResetTokenRepository
                .findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime.now(),
                        PageRequest.of(0, 1000));

        int count = expiredTokens.getContent().size();
        passwordResetTokenRepository.deleteAll(expiredTokens.getContent());
        Map<String, Object> details = new HashMap<>();
        details.put("tokensDeleted", count);
        details.put("operation", "bulk_cleanup_expired");
        adminActivityLogger.logActivity(adminId, "DELETE", "PasswordResetToken", "bulk", details, request);

        log.info("Deleted {} expired password reset tokens by admin {}", count, adminId);
        return count;
    }
    @Transactional
    public int deleteExpiredVerificationTokens(Long adminId, HttpServletRequest request) {
        Page<VerificationToken> expiredTokens = verificationTokenRepository
                .findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime.now(),
                        PageRequest.of(0, 1000));

        int count = expiredTokens.getContent().size();
        verificationTokenRepository.deleteAll(expiredTokens.getContent());
        Map<String, Object> details = new HashMap<>();
        details.put("tokensDeleted", count);
        details.put("operation", "bulk_cleanup_expired");
        adminActivityLogger.logActivity(adminId, "DELETE", "VerificationToken", "bulk", details, request);

        log.info("Deleted {} expired verification tokens by admin {}", count, adminId);
        return count;
    }
    private PasswordResetTokenDTO convertPasswordResetTokenToDTO(PasswordResetToken token) {
        String username = "Unknown";
        String email = "Unknown";

        if ("user".equals(token.getRole())) {
            User user = userRepository.findById(token.getUserId()).orElse(null);
            if (user != null) {
                username = user.getUsername();
                email = user.getEmail();
            }
        } else if ("admin".equals(token.getRole())) {
            Admin admin = adminRepository.findById(token.getUserId()).orElse(null);
            if (admin != null) {
                username = admin.getUsername();
                email = admin.getEmail();
            }
        }

        return PasswordResetTokenDTO.builder()
                .id(token.getId())
                .token(displayTokenPreview(token.getTokenPreview(), token.getStoredToken()))
                .userId(token.getUserId())
                .role(token.getRole())
                .username(username)
                .email(email)
                .expiryDate(token.getExpiryDate())
                .createdDate(token.getCreatedDate())
                .attemptCount(token.getAttemptCount())
                .requestingIp(token.getRequestingIp())
                .expired(token.isExpired())
                .build();
    }
    private VerificationTokenDTO convertVerificationTokenToDTO(VerificationToken token) {
        String username = "Unknown";
        String email = "Unknown";

        if ("user".equals(token.getRole())) {
            User user = userRepository.findById(token.getUserId()).orElse(null);
            if (user != null) {
                username = user.getUsername();
                email = user.getEmail();
            }
        } else if ("admin".equals(token.getRole())) {
            Admin admin = adminRepository.findById(token.getUserId()).orElse(null);
            if (admin != null) {
                username = admin.getUsername();
                email = admin.getEmail();
            }
        }

        return VerificationTokenDTO.builder()
                .id(token.getId())
                .token(displayTokenPreview(token.getTokenPreview(), token.getStoredToken()))
                .userId(token.getUserId())
                .role(token.getRole())
                .username(username)
                .email(email)
                .expiryDate(token.getExpiryDate())
                .createdDate(token.getCreatedDate())
                .expired(token.isExpired())
                .build();
    }
    private String validatePasswordResetSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }

        if (!ALLOWED_PASSWORD_RESET_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid password reset sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }

        return sortBy;
    }
    private String validateVerificationSortField(String sortBy, String defaultField) {
        if (sortBy == null || sortBy.isEmpty()) {
            return defaultField;
        }

        if (!ALLOWED_VERIFICATION_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid verification sort field attempted: '{}'. Using default: '{}'", sortBy, defaultField);
            return defaultField;
        }

        return sortBy;
    }

    private String displayTokenPreview(String tokenPreview, String legacyToken) {
        return tokenPreview != null && !tokenPreview.isBlank()
                ? tokenPreview
                : tokenHashService.preview(legacyToken);
    }
}
