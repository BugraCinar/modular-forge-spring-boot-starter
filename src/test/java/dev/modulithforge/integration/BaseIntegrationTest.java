package dev.modulithforge.integration;

import dev.modulithforge.auth.token.RefreshToken;
import dev.modulithforge.identity.model.Role;

import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.User;
import dev.modulithforge.identity.model.UserType;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.auth.token.RefreshTokenRepository;
import dev.modulithforge.identity.UserRepository;
import dev.modulithforge.auth.token.VerificationTokenRepository;
import dev.modulithforge.notification.EmailService;
import dev.modulithforge.auth.PasswordService;
import dev.modulithforge.ratelimit.RateLimitStore;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("integration")
public abstract class BaseIntegrationTest {

    protected static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF = new ParameterizedTypeReference<>() {
    };
    @MockitoBean
    protected EmailService emailService;

    @MockitoBean
    protected RateLimitStore rateLimitStore;
    @Autowired
    protected TestRestTemplate restTemplate;
    @Autowired
    protected AdminRepository adminRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected VerificationTokenRepository verificationTokenRepository;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        when(rateLimitStore.incrementWithTtl(anyString(), anyLong())).thenReturn(1L);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            refreshTokenRepository.deleteAll();
            verificationTokenRepository.deleteAll();
            userRepository.deleteAll();
            adminRepository.deleteAll();
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    protected Admin createAdmin(String username, String email, String password, int level) {
        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(password, salt);

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(hash);
        admin.setSalt(salt);
        admin.setLevel(level);
        admin.setIsActive(true);

        return adminRepository.save(admin);
    }

    protected User createVerifiedUser(String username, String email, String password) {
        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(password, salt);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(hash);
        user.setSalt(salt);
        user.setEmailVerified(true);
        user.setIsActive(true);
        user.setUserType(UserType.APP_USER);

        return userRepository.save(user);
    }

    protected ResponseEntity<Map<String, Object>> login(String username, String password, String role) {
        Map<String, String> body = Map.of(
                "username", username,
                "password", password,
                "role", role);
        return restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(body), MAP_TYPE_REF);
    }

    protected String extractAccessToken(ResponseEntity<Map<String, Object>> response) {
        Object token = response.getBody().get("accessToken");
        return token != null ? token.toString() : null;
    }

    protected String extractRefreshToken(ResponseEntity<Map<String, Object>> response) {
        Object token = response.getBody().get("refreshToken");
        return token != null ? token.toString() : null;
    }

    protected HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        return headers;
    }

    protected ResponseEntity<Map<String, Object>> authenticatedGet(String url, String accessToken) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(accessToken));
        return restTemplate.exchange(url, HttpMethod.GET, entity, MAP_TYPE_REF);
    }
}
