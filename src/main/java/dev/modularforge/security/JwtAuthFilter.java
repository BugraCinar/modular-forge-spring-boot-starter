package dev.modularforge.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Autowired
    public JwtAuthFilter(JwtUtils jwtUtils, UserRepository userRepository, AdminRepository adminRepository) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);

            if (jwtUtils.validateToken(token)) {
                String username = jwtUtils.extractUsername(token);
                Long userId = jwtUtils.extractUserIdAsLong(token);
                String role = jwtUtils.extractRole(token);
                String userType = jwtUtils.extractUserType(token);
                Long tokenAuthVersion = jwtUtils.extractAuthVersion(token);

                if (!isCurrentActiveAccount(userId, role, tokenAuthVersion)) {
                    logger.warn("Rejected access token for inactive, locked, deleted, or invalidated account");
                    filterChain.doFilter(request, response);
                    return;
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ENGLISH)))
                );
                authentication.setDetails(userId);
                request.setAttribute("userId", userId);
                request.setAttribute("role", role);
                request.setAttribute("userType", userType);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.debug("Ignoring invalid bearer token");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCurrentActiveAccount(Long userId, String role, Long tokenAuthVersion) {
        if (userId == null || role == null || tokenAuthVersion == null) {
            return false;
        }

        return switch (role.toLowerCase(Locale.ROOT)) {
            case "user" -> userRepository.findById(userId)
                    .map(user -> isActiveAndUnlocked(user.getIsActive(), user.getLockedUntil())
                            && user.currentAuthVersion() == tokenAuthVersion)
                    .orElse(false);
            case "admin" -> adminRepository.findById(userId)
                    .map(admin -> isActiveAndUnlocked(admin.getIsActive(), admin.getLockedUntil())
                            && admin.currentAuthVersion() == tokenAuthVersion)
                    .orElse(false);
            default -> false;
        };
    }

    private boolean isActiveAndUnlocked(Boolean active, LocalDateTime lockedUntil) {
        return Boolean.TRUE.equals(active)
                && (lockedUntil == null || !lockedUntil.isAfter(LocalDateTime.now()));
    }
}
