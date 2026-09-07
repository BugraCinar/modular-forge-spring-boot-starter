package dev.modularforge.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    void registersTrimmedCredentialedCorsRulesForApiAndActuator() {
        CorsConfig config = configured(" https://app.example.com, ,https://admin.example.com ",
                "GET, POST", "Authorization, Content-Type");
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration api = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/profile"));
        CorsConfiguration actuator = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/actuator/health"));

        assertThat(api).isNotNull();
        assertThat(api.getAllowedOrigins()).containsExactly("https://app.example.com", "https://admin.example.com");
        assertThat(api.getAllowedMethods()).containsExactly("GET", "POST");
        assertThat(api.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
        assertThat(api.getAllowCredentials()).isTrue();
        assertThat(api.getMaxAge()).isEqualTo(3_600L);
        assertThat(actuator).isNotNull();
    }

    @Test
    void rejectsWildcardOriginWithCredentials() {
        CorsConfig config = configured("*", "GET", "Authorization");

        assertThatThrownBy(config::corsConfigurationSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard CORS origins");
    }

    private CorsConfig configured(String origins, String methods, String headers) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        ReflectionTestUtils.setField(config, "allowedMethods", methods);
        ReflectionTestUtils.setField(config, "allowedHeaders", headers);
        return config;
    }
}
