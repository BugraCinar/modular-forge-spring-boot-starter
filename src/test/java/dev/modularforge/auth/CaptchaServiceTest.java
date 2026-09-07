package dev.modularforge.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock RestTemplate restTemplate;

    private CaptchaService service;

    @BeforeEach
    void setUp() {
        service = new CaptchaService();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "secretKey", "secret");
        ReflectionTestUtils.setField(service, "scoreThreshold", 0.5d);
        ReflectionTestUtils.setField(service, "expectedHostname", "app.example.com");
        ReflectionTestUtils.setField(service, "expectedAction", "login");
    }

    @Test
    void validatesEveryConfigurationBoundary() {
        ReflectionTestUtils.setField(service, "enabled", false);
        service.validateConfiguration();

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "secretKey", null);
        assertThatThrownBy(service::validateConfiguration).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret key");
        ReflectionTestUtils.setField(service, "secretKey", " ");
        assertThatThrownBy(service::validateConfiguration).isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(service, "secretKey", "secret");
        ReflectionTestUtils.setField(service, "expectedHostname", null);
        assertThatThrownBy(service::validateConfiguration).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hostname");
        ReflectionTestUtils.setField(service, "expectedHostname", " ");
        assertThatThrownBy(service::validateConfiguration).isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(service, "expectedHostname", "app.example.com");
        ReflectionTestUtils.setField(service, "scoreThreshold", -0.1d);
        assertThatThrownBy(service::validateConfiguration).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 0 and 1");
        ReflectionTestUtils.setField(service, "scoreThreshold", 1.1d);
        assertThatThrownBy(service::validateConfiguration).isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(service, "scoreThreshold", 0d);
        service.validateConfiguration();
        ReflectionTestUtils.setField(service, "scoreThreshold", 1d);
        service.validateConfiguration();
    }

    @Test
    void disabledCaptchaSucceedsWithoutRemoteCall() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.verifyCaptcha(null, null)).isTrue();
        verify(restTemplate, never()).postForEntity(anyString(), any(), any(Class.class));
    }

    @Test
    void rejectsMissingTokensAndSecrets() {
        assertThat(service.verifyCaptcha(null, "ip")).isFalse();
        assertThat(service.verifyCaptcha("", "ip")).isFalse();
        ReflectionTestUtils.setField(service, "secretKey", null);
        assertThat(service.verifyCaptcha("token", "ip")).isFalse();
        ReflectionTestUtils.setField(service, "secretKey", "");
        assertThat(service.verifyCaptcha("token", "ip")).isFalse();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void sendsFormFieldsAndAcceptsMatchingScoreAndAction() throws Exception {
        Object body = response(true, "APP.EXAMPLE.COM", 0.8, "login");
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(body));
        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        assertThat(service.verifyCaptcha("token", "192.0.2.1")).isTrue();

        verify(restTemplate).postForEntity(anyString(), requestCaptor.capture(), any(Class.class));
        assertThat(requestCaptor.getValue().getBody().getFirst("secret")).isEqualTo("secret");
        assertThat(requestCaptor.getValue().getBody().getFirst("response")).isEqualTo("token");
        assertThat(requestCaptor.getValue().getBody().getFirst("remoteip")).isEqualTo("192.0.2.1");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void handlesEmptyResponseHostnameMismatchAndOptionalHostnameConfiguration() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok().build());
        assertThat(service.verifyCaptcha("token", null)).isFalse();

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(response(true, "evil.example", 0.9, "login")));
        assertThat(service.verifyCaptcha("token", "")).isFalse();

        ReflectionTestUtils.setField(service, "expectedHostname", null);
        assertThat(service.verifyCaptcha("token", null)).isTrue();
        ReflectionTestUtils.setField(service, "expectedHostname", " ");
        assertThat(service.verifyCaptcha("token", null)).isTrue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void evaluatesScoreSuccessAndActionIndependently() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(response(false, "app.example.com", 0.9, "login")),
                        (ResponseEntity) ResponseEntity.ok(response(true, "app.example.com", 0.4, "login")),
                        (ResponseEntity) ResponseEntity.ok(response(true, "app.example.com", 0.9, "signup")),
                        (ResponseEntity) ResponseEntity.ok(response(true, "app.example.com", 0.9, "login")));
        assertThat(service.verifyCaptcha("token", null)).isFalse();
        assertThat(service.verifyCaptcha("token", null)).isFalse();
        assertThat(service.verifyCaptcha("token", null)).isFalse();
        assertThat(service.verifyCaptcha("token", null)).isTrue();

        ReflectionTestUtils.setField(service, "expectedAction", null);
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(response(true, "app.example.com", 0.9, "login")));
        assertThat(service.verifyCaptcha("token", null)).isFalse();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void supportsCheckboxCaptchaWithoutScoreAndHandlesRemoteFailure() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(response(true, "app.example.com", null, null)),
                        (ResponseEntity) ResponseEntity.ok(response(false, "app.example.com", null, null)));
        assertThat(service.verifyCaptcha("token", null)).isTrue();
        assertThat(service.verifyCaptcha("token", null)).isFalse();

        when(restTemplate.postForEntity(anyString(), any(), any(Class.class)))
                .thenThrow(new IllegalStateException("network"));
        assertThat(service.verifyCaptcha("token", null)).isFalse();
    }

    private Object response(boolean success, String hostname, Double score, String action) throws Exception {
        Class<?> type = Class.forName("dev.modularforge.auth.CaptchaService$RecaptchaResponse");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object response = constructor.newInstance();
        ReflectionTestUtils.setField(response, "success", success);
        ReflectionTestUtils.setField(response, "challengeTs", "now");
        ReflectionTestUtils.setField(response, "hostname", hostname);
        ReflectionTestUtils.setField(response, "score", score);
        ReflectionTestUtils.setField(response, "action", action);
        ReflectionTestUtils.setField(response, "errorCodes", java.util.List.of("none"));
        return response;
    }
}
