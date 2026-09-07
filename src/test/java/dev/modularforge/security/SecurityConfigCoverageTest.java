package dev.modularforge.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCoverageTest {

    @Test
    void csrfCookieUsesSecureStrictSiteWideSettings() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "secureCookies", true);
        CookieCsrfTokenRepository repository = config.csrfTokenRepository();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("Path=/", "Secure")
                .doesNotContain("HttpOnly");
        assertThat(response.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Strict");
    }
}
