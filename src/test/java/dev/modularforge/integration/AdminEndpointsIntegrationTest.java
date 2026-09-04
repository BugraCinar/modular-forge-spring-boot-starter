package dev.modularforge.integration;

import dev.modularforge.identity.model.Admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
@DisplayName("Admin Endpoints Integration")
class AdminEndpointsIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("admin login returns 200 with accessToken in body")
    void adminLogin_returns200_withAccessToken() {
        createAdmin("adminlogin", "adminlogin@test.com", "Admin1234!", 0);

        ResponseEntity<Map<String, Object>> loginResp = login("adminlogin", "Admin1234!", "admin");

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody().get("success")).isEqualTo(true);

        String accessToken = extractAccessToken(loginResp);
        assertThat(accessToken).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("GET /api/v1/admin/admins with valid level-0 admin token returns 200")
    void adminProtectedEndpoint_withValidToken_returns200() {
        createAdmin("levelzero", "levelzero@test.com", "Admin1234!", 0);

        ResponseEntity<Map<String, Object>> loginResp = login("levelzero", "Admin1234!", "admin");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String token = extractAccessToken(loginResp);
        assertThat(token).isNotNull();

        ResponseEntity<Map<String, Object>> adminResp = authenticatedGet("/api/v1/admin/admins", token);

        assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /api/v1/admin/admins without auth token returns 401")
    void adminProtectedEndpoint_withNoToken_returns401() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/admin/admins", HttpMethod.GET, null, MAP_TYPE_REF);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
