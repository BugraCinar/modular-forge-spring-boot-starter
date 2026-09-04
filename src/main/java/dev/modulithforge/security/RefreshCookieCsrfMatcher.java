package dev.modulithforge.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Set;

final class RefreshCookieCsrfMatcher implements RequestMatcher {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    private final String refreshCookieName;

    RefreshCookieCsrfMatcher(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (!path.equals("/api/v1/auth/refresh") && !path.equals("/api/v1/auth/logout")) {
            return false;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }
}
