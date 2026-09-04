package dev.modularforge.auth.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
@Service
public class RefreshTokenCookieService {

    @Value("${app.refresh-token.cookie-name:refreshToken}")
    private String cookieName;

    @Value("${app.refresh-token.cookie-max-age:2592000}")
    private int cookieMaxAge;

    @Value("${app.refresh-token.use-cookies:true}")
    private boolean useCookies;

    @Value("${app.refresh-token.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${app.refresh-token.cookie-path:/api/v1/auth}")
    private String cookiePath;

    public boolean useCookies() {
        return useCookies;
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(cookieMaxAge)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
