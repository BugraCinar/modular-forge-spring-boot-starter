package dev.modularforge.auth.token;

import dev.modularforge.auth.token.dto.RefreshTokenRequest;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.model.UserType;
import dev.modularforge.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenControllerCoverageTest {

    @Mock RefreshTokenService tokens;
    @Mock JwtUtils jwtUtils;
    @Mock AdminRepository admins;
    @Mock UserRepository users;
    @Mock RefreshTokenCookieService cookies;

    private RefreshTokenController controller;

    @BeforeEach
    void setUp() {
        controller = new RefreshTokenController();
        ReflectionTestUtils.setField(controller, "refreshTokenService", tokens);
        ReflectionTestUtils.setField(controller, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(controller, "adminRepository", admins);
        ReflectionTestUtils.setField(controller, "userRepository", users);
        ReflectionTestUtils.setField(controller, "cookieName", "refreshToken");
        ReflectionTestUtils.setField(controller, "refreshTokenCookieService", cookies);
    }

    @Test
    void refreshesUserFromCookieWithoutReturningRefreshTokenInBody() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "ignored"), new Cookie("refreshToken", "old"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshTokenRequest body = body("body-token");
        RefreshToken old = token(1L, "user", "old");
        RefreshToken fresh = token(1L, "user", "new");
        User user = activeUser();
        when(tokens.verifyRefreshToken("old")).thenReturn(Optional.of(old));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(tokens.rotateRefreshToken(old, request)).thenReturn(Optional.of(fresh));
        when(jwtUtils.generateUserToken("alice", 1L, "app_user", 0L)).thenReturn("access");
        when(cookies.useCookies()).thenReturn(true);

        var result = controller.refresh(body, request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseBody(result)).doesNotContainKey("refreshToken");
        verify(cookies).setRefreshTokenCookie(response, "new");
    }

    @Test
    void refreshesUserFromBodyAndReturnsRotatedToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshTokenRequest body = body("old");
        RefreshToken old = token(1L, "user", "old");
        RefreshToken fresh = token(1L, "user", "new");
        User user = activeUser();
        when(tokens.verifyRefreshToken("old")).thenReturn(Optional.of(old));
        when(users.findById(1L)).thenReturn(Optional.of(user), Optional.empty());
        when(tokens.rotateRefreshToken(old, request)).thenReturn(Optional.of(fresh));
        when(jwtUtils.generateUserToken("unknown_user_1", 1L, "app_user", 0L)).thenReturn("access");
        when(cookies.useCookies()).thenReturn(true);

        var result = controller.refresh(body, request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseBody(result)).containsEntry("refreshToken", "new");
    }

    @Test
    void refreshesAdminWithoutCookiesAndUsesUnknownNameAfterLookupFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshTokenRequest body = body("old");
        RefreshToken old = token(2L, "admin", "old");
        RefreshToken fresh = token(2L, "admin", "new");
        Admin admin = new Admin();
        admin.setId(2L);
        admin.setUsername("root");
        admin.setIsActive(true);
        admin.setLevel(2);
        admin.setAuthVersion(4L);
        when(tokens.verifyRefreshToken("old")).thenReturn(Optional.of(old));
        when(admins.findById(2L)).thenReturn(Optional.of(admin)).thenThrow(new IllegalStateException("db"));
        when(tokens.rotateRefreshToken(old, request)).thenReturn(Optional.of(fresh));
        when(jwtUtils.generateAdminToken("unknown_2", 2L, 2, 4L)).thenReturn("admin-access");
        when(cookies.useCookies()).thenReturn(false);

        var result = controller.refresh(body, request, response);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseBody(result)).containsEntry("accessToken", "admin-access")
                .containsEntry("refreshToken", "new");
    }

    @Test
    void rejectsMissingInactiveAndLockedAccounts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken userToken = token(1L, "user", "old");
        when(tokens.verifyRefreshToken("old")).thenReturn(Optional.of(userToken));
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThat(controller.refresh(body("old"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        User inactive = activeUser();
        inactive.setIsActive(false);
        when(users.findById(1L)).thenReturn(Optional.of(inactive));
        assertThat(controller.refresh(body("old"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        inactive.setIsActive(true);
        inactive.setLockedUntil(LocalDateTime.now().plusMinutes(1));
        assertThat(controller.refresh(body("old"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        RefreshToken adminToken = token(2L, "admin", "old-admin");
        when(tokens.verifyRefreshToken("old-admin")).thenReturn(Optional.of(adminToken));
        when(admins.findById(2L)).thenReturn(Optional.empty());
        assertThat(controller.refresh(body("old-admin"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Admin inactiveAdmin = new Admin();
        inactiveAdmin.setIsActive(false);
        when(admins.findById(2L)).thenReturn(Optional.of(inactiveAdmin));
        assertThat(controller.refresh(body("old-admin"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Admin lockedAdmin = new Admin();
        lockedAdmin.setIsActive(true);
        lockedAdmin.setLockedUntil(LocalDateTime.now().plusMinutes(1));
        when(admins.findById(2L)).thenReturn(Optional.of(lockedAdmin));
        assertThat(controller.refresh(body("old-admin"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRotationRaceAndMapsUnexpectedRefreshFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RefreshToken old = token(1L, "user", "old");
        when(tokens.verifyRefreshToken("old")).thenReturn(Optional.of(old));
        when(users.findById(1L)).thenReturn(Optional.of(activeUser()));
        when(tokens.rotateRefreshToken(old, request)).thenReturn(Optional.empty());
        assertThat(controller.refresh(body("old"), request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(tokens.verifyRefreshToken("broken")).thenThrow(new IllegalStateException("db"));
        assertThat(controller.refresh(body("broken"), request, response).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void logoutHandlesBodyCookieRevocationOutcomesAndExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokens.revokeRefreshToken("body")).thenReturn(true, false);
        assertThat(controller.logout(body("body"), request, response).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.logout(body("body"), request, response).getStatusCode()).isEqualTo(HttpStatus.OK);

        request.setCookies(new Cookie("refreshToken", "cookie"));
        when(tokens.revokeRefreshToken("cookie")).thenThrow(new IllegalStateException("db"));
        assertThat(controller.logout(null, request, response).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(cookies, org.mockito.Mockito.atLeast(3)).clearRefreshTokenCookie(response);
    }

    @Test
    void logoutAllRejectsMalformedInvalidAndIncompleteAccessTokens() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        request.addHeader("Authorization", "Basic value");
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        request.removeHeader("Authorization");
        request.addHeader("Authorization", "Bearer access");
        when(jwtUtils.validateToken("access")).thenReturn(false, true, true);
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        when(jwtUtils.extractUserIdAsLong("access")).thenReturn((Long) null, 1L);
        when(jwtUtils.extractRole("access")).thenReturn("user", (String) null);
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutAllRevokesSessionsAndMapsFailures() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtUtils.validateToken("access")).thenReturn(true);
        when(jwtUtils.extractUserIdAsLong("access")).thenReturn(1L);
        when(jwtUtils.extractRole("access")).thenReturn("user");
        when(tokens.revokeAllUserTokens(1L, "user")).thenReturn(3);
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.OK);

        doThrow(new IllegalStateException("db")).when(tokens).revokeAllUserTokens(1L, "user");
        assertThat(controller.logoutAll(request, response).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void refreshBodyDetectionHandlesBlankBodyAndBlankCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(controller, "isRefreshTokenFromRequestBody", request, null)).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(controller, "isRefreshTokenFromRequestBody", request, body(null))).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(controller, "isRefreshTokenFromRequestBody", request, body(" "))).isFalse();
        request.setCookies(new Cookie("refreshToken", " "), new Cookie("other", "value"));
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(controller, "isRefreshTokenFromRequestBody", request, body("body"))).isTrue();

        Cookie nullValue = new Cookie("refreshToken", "temporary");
        nullValue.setValue(null);
        request.setCookies(nullValue);
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(controller, "isRefreshTokenFromRequestBody", request, body("body"))).isTrue();

        request.setCookies(new Cookie[0]);
        assertThat(ReflectionTestUtils.<String>invokeMethod(controller, "extractRefreshToken", request, body(null))).isNull();
        jakarta.servlet.http.HttpServletRequest emptyCookies = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        when(emptyCookies.getCookies()).thenReturn(new Cookie[0]);
        assertThat(ReflectionTestUtils.<String>invokeMethod(controller, "extractRefreshToken", emptyCookies, body(null))).isNull();
        assertThat(controller.refresh(body(""), new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.logout(body(""), new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(controller, "isLocked", LocalDateTime.now().minusMinutes(1)))
                .isFalse();
    }

    private RefreshTokenRequest body(String token) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseBody(org.springframework.http.ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    private RefreshToken token(Long userId, String role, String value) {
        RefreshToken token = new RefreshToken(userId, role, 30L);
        token.setPlaintextToken(value);
        return token;
    }

    private User activeUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setIsActive(true);
        user.setUserType(UserType.APP_USER);
        user.setAuthVersion(0L);
        return user;
    }
}
