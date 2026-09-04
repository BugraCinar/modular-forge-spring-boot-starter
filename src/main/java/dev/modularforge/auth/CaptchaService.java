package dev.modularforge.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import jakarta.annotation.PostConstruct;
@Service
public class CaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(CaptchaService.class);
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Value("${recaptcha.secret-key:}")
    private String secretKey;

    @Value("${recaptcha.enabled:false}")
    private boolean enabled;

    @Value("${recaptcha.score-threshold:0.5}")
    private double scoreThreshold;

    @Value("${recaptcha.expected-hostname:}")
    private String expectedHostname;

    @Value("${recaptcha.expected-action:login}")
    private String expectedAction;

    private final RestTemplate restTemplate;

    public CaptchaService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @PostConstruct
    void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("reCAPTCHA is enabled but its secret key is missing");
        }
        if (expectedHostname == null || expectedHostname.isBlank()) {
            throw new IllegalStateException("reCAPTCHA is enabled but its expected hostname is missing");
        }
        if (scoreThreshold < 0 || scoreThreshold > 1) {
            throw new IllegalStateException("reCAPTCHA score threshold must be between 0 and 1");
        }
    }
    public boolean verifyCaptcha(String token, String remoteIp) {
        if (!enabled) {
            logger.debug("reCAPTCHA verification is disabled");
            return true;
        }

        if (token == null || token.isEmpty()) {
            logger.warn("No reCAPTCHA token provided");
            return false;
        }

        if (secretKey == null || secretKey.isEmpty()) {
            logger.error("reCAPTCHA secret key not configured");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", secretKey);
            params.add("response", token);
            if (remoteIp != null && !remoteIp.isEmpty()) {
                params.add("remoteip", remoteIp);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<RecaptchaResponse> response = restTemplate.postForEntity(
                    RECAPTCHA_VERIFY_URL,
                    request,
                    RecaptchaResponse.class
            );

            RecaptchaResponse recaptchaResponse = response.getBody();

            if (recaptchaResponse == null) {
                logger.error("Empty response from reCAPTCHA verification");
                return false;
            }

            boolean hostnameMatches = expectedHostname == null || expectedHostname.isBlank()
                    || expectedHostname.equalsIgnoreCase(recaptchaResponse.getHostname());
            if (!hostnameMatches) {
                logger.warn("reCAPTCHA response used an unexpected hostname");
                return false;
            }

            if (recaptchaResponse.getScore() != null) {
                boolean scorePass = recaptchaResponse.getScore() >= scoreThreshold;
                boolean actionMatches = expectedAction != null
                        && expectedAction.equals(recaptchaResponse.getAction());
                return recaptchaResponse.isSuccess() && scorePass && actionMatches;
            }

            return recaptchaResponse.isSuccess();

        } catch (Exception e) {
            logger.error("Error verifying reCAPTCHA", e);
            return false;
        }
    }
    @Data
    private static class RecaptchaResponse {
        private boolean success;

        @JsonProperty("challenge_ts")
        private String challengeTs;

        private String hostname;

        private Double score;

        private String action;

        @JsonProperty("error-codes")
        private List<String> errorCodes;
    }
}
