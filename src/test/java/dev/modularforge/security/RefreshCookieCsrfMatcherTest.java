package dev.modularforge.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieCsrfMatcherTest {

    private final RefreshCookieCsrfMatcher matcher = new RefreshCookieCsrfMatcher("refresh_token");

    @Test
    void safeMethodsAndUnrelatedRoutesDoNotRequireCsrf() {
        for (String method : new String[]{"GET", "HEAD", "TRACE", "OPTIONS"}) {
            assertThat(matcher.matches(request(method, "/api/v1/auth/refresh"))).isFalse();
        }
        assertThat(matcher.matches(request("POST", "/api/v1/profile"))).isFalse();
    }

    @Test
    void refreshAndLogoutRequireCsrfOnlyForNonBlankRefreshCookie() {
        MockHttpServletRequest noCookies = request("POST", "/api/v1/auth/refresh");
        assertThat(matcher.matches(noCookies)).isFalse();

        MockHttpServletRequest wrongCookie = request("POST", "/api/v1/auth/refresh");
        wrongCookie.setCookies(new Cookie("other", "value"));
        assertThat(matcher.matches(wrongCookie)).isFalse();

        for (String value : new String[]{"", " "}) {
            MockHttpServletRequest blank = request("POST", "/api/v1/auth/logout");
            blank.setCookies(new Cookie("refresh_token", value));
            assertThat(matcher.matches(blank)).isFalse();
        }

        MockHttpServletRequest valid = request("POST", "/api/v1/auth/logout");
        valid.setCookies(new Cookie("other", "value"), new Cookie("refresh_token", "raw-token"));
        assertThat(matcher.matches(valid)).isTrue();

        Cookie nullValue = new Cookie("refresh_token", "temporary");
        nullValue.setValue(null);
        MockHttpServletRequest nullCookie = request("POST", "/api/v1/auth/refresh");
        nullCookie.setCookies(nullValue);
        assertThat(matcher.matches(nullCookie)).isFalse();
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
