package dev.modulithforge.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Module catalog integration")
class ModuleCatalogIntegrationTest extends BaseIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> MODULE_LIST =
            new ParameterizedTypeReference<>() {
            };

    @Test
    @DisplayName("public catalog reports module state, dependencies, and removal guide")
    void catalogIsPublicAndDescribesRemovableModules() {
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                "/api/v1/modules", HttpMethod.GET, null, MODULE_LIST);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(14);

        Map<String, Object> twoFactor = response.getBody().stream()
                .filter(module -> "two-factor".equals(module.get("id")))
                .findFirst()
                .orElseThrow();

        assertThat(twoFactor)
                .containsEntry("kind", "OPTIONAL")
                .containsEntry("enabled", false)
                .containsEntry("toggle", "app.modules.two-factor.enabled")
                .containsEntry("removalGuide", "docs/modules/two-factor.md");
        assertThat(twoFactor.get("dependsOn")).isEqualTo(List.of("authentication", "identity"));
    }
}
