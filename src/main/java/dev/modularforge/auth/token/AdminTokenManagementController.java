package dev.modularforge.auth.token;


import dev.modularforge.auth.token.dto.PasswordResetTokenDTO;
import dev.modularforge.auth.token.dto.TokenListResponse;
import dev.modularforge.auth.token.dto.VerificationTokenDTO;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.security.JwtUtils;
import dev.modularforge.auth.token.AdminTokenManagementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/tokens")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN') and @adminLevelAuthorizationService.isLevel0()")
public class AdminTokenManagementController {

    private final AdminTokenManagementService tokenManagementService;
    private final AdminRepository adminRepository;
    private final JwtUtils jwtUtils;
    @GetMapping("/password-reset")
    public ResponseEntity<?> getAllPasswordResetTokens(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "true") Boolean includeExpired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view password reset tokens."
                ));
            }

            TokenListResponse<PasswordResetTokenDTO> response = tokenManagementService.getAllPasswordResetTokens(
                    role, includeExpired, page, size, sortBy, sortDirection, currentAdminId, httpRequest
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching password reset tokens", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch password reset tokens"
            ));
        }
    }
    @GetMapping("/password-reset/{tokenId}")
    public ResponseEntity<?> getPasswordResetTokenById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view password reset tokens."
                ));
            }

            PasswordResetTokenDTO tokenDTO = tokenManagementService.getPasswordResetTokenById(tokenId, currentAdminId, httpRequest);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tokenDTO
            ));

        } catch (RuntimeException e) {
            log.error("Error fetching password reset token by ID: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching password reset token by ID: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch password reset token"
            ));
        }
    }
    @DeleteMapping("/password-reset/{tokenId}")
    public ResponseEntity<?> deletePasswordResetToken(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can delete password reset tokens."
                ));
            }

            tokenManagementService.deletePasswordResetToken(tokenId, currentAdminId, httpRequest);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Password reset token deleted successfully"
            ));

        } catch (RuntimeException e) {
            log.error("Error deleting password reset token: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error deleting password reset token: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete password reset token"
            ));
        }
    }
    @GetMapping("/verification")
    public ResponseEntity<?> getAllVerificationTokens(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String role,
            @RequestParam(required = false, defaultValue = "true") Boolean includeExpired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view verification tokens."
                ));
            }

            TokenListResponse<VerificationTokenDTO> response = tokenManagementService.getAllVerificationTokens(
                    role, includeExpired, page, size, sortBy, sortDirection, currentAdminId, httpRequest
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));

        } catch (Exception e) {
            log.error("Error fetching verification tokens", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch verification tokens"
            ));
        }
    }
    @GetMapping("/verification/{tokenId}")
    public ResponseEntity<?> getVerificationTokenById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can view verification tokens."
                ));
            }

            VerificationTokenDTO tokenDTO = tokenManagementService.getVerificationTokenById(tokenId, currentAdminId, httpRequest);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", tokenDTO
            ));

        } catch (RuntimeException e) {
            log.error("Error fetching verification token by ID: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error fetching verification token by ID: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to fetch verification token"
            ));
        }
    }
    @DeleteMapping("/verification/{tokenId}")
    public ResponseEntity<?> deleteVerificationToken(
            @RequestHeader("Authorization") String token,
            @PathVariable Long tokenId,
            HttpServletRequest httpRequest
    ) {
        try {
            Long currentAdminId = Long.valueOf(jwtUtils.extractUserId(token.substring(7)));
            Admin admin = adminRepository.findById(currentAdminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            if (admin.getLevel() != 0) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Access denied. Only Level 0 Super Admins can delete verification tokens."
                ));
            }

            tokenManagementService.deleteVerificationToken(tokenId, currentAdminId, httpRequest);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Verification token deleted successfully"
            ));

        } catch (RuntimeException e) {
            log.error("Error deleting verification token: {}", tokenId, e);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error deleting verification token: {}", tokenId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to delete verification token"
            ));
        }
    }
}
