package dev.modulithforge.security;

import dev.modulithforge.identity.model.User;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.AdminRepository;

import java.time.LocalDateTime;
@Service
@Slf4j
public class AdminLevelAuthorizationService {

    private final AdminRepository adminRepository;

    @Autowired
    public AdminLevelAuthorizationService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }
    public boolean isLevel0() {
        return hasLevel(0);
    }
    public boolean isLevel0Or1() {
        return hasMaxLevel(1);
    }
    public boolean isLevel0Or1Or2() {
        return hasMaxLevel(2);
    }
    public boolean hasLevel(int level) {
        Integer adminLevel = getCurrentAdminLevel();
        if (adminLevel == null) {
            log.warn("Could not determine admin level for authorization check");
            return false;
        }
        return adminLevel == level;
    }
    public boolean hasMaxLevel(int maxLevel) {
        Integer adminLevel = getCurrentAdminLevel();
        if (adminLevel == null) {
            log.warn("Could not determine admin level for authorization check");
            return false;
        }
        return adminLevel <= maxLevel;
    }
    private Integer getCurrentAdminLevel() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("No authenticated user found");
                return null;
            }
            Long adminId = (Long) authentication.getDetails();
            if (adminId == null) {
                log.warn("No admin ID found in authentication details");
                return null;
            }
            Admin admin = adminRepository.findById(adminId).orElse(null);
            if (admin == null) {
                log.warn("Admin not found with ID: {}", adminId);
                return null;
            }

            if (!Boolean.TRUE.equals(admin.getIsActive())
                    || (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(LocalDateTime.now()))) {
                log.warn("Inactive or locked admin attempted an admin-level authorization check: {}", adminId);
                return null;
            }

            return admin.getLevel();
        } catch (Exception e) {
            log.error("Error getting admin level: {}", e.getMessage(), e);
            return null;
        }
    }
}
