package dev.modularforge.security;

import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.User;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String TOKEN = "valid-access-token";

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtils, userRepository, adminRepository);
        SecurityContextHolder.clearContext();

        lenient().when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
        lenient().when(jwtUtils.extractUsername(TOKEN)).thenReturn("user");
        lenient().when(jwtUtils.extractUserIdAsLong(TOKEN)).thenReturn(1L);
        lenient().when(jwtUtils.extractRole(TOKEN)).thenReturn("user");
        lenient().when(jwtUtils.extractUserType(TOKEN)).thenReturn("app_user");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentActiveUserToken_establishesAuthentication() throws Exception {
        User user = activeUser(4L);
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(4L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        doAnswer(invocation -> {
            observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(observedAuthentication.get()).isNotNull();
        assertThat(observedAuthentication.get().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void staleAuthorizationVersion_doesNotEstablishAuthentication() throws Exception {
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(3L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser(4L)));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void inactiveOrLockedAdminToken_doesNotEstablishAuthentication() throws Exception {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setIsActive(false);
        admin.setAuthVersion(2L);
        admin.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(jwtUtils.extractRole(TOKEN)).thenReturn("admin");
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(2L);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void legacyTokenWithoutAuthorizationVersion_doesNotEstablishAuthentication() throws Exception {
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(null);

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingBasicAndInvalidBearerHeadersPassThroughUnauthenticated() throws Exception {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        filter.doFilter(missing, new MockHttpServletResponse(), filterChain);

        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic value");
        filter.doFilter(basic, new MockHttpServletResponse(), filterChain);

        when(jwtUtils.validateToken("invalid")).thenReturn(false);
        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.addHeader("Authorization", "Bearer invalid");
        filter.doFilter(invalid, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedBearerTokenIsContained() throws Exception {
        when(jwtUtils.validateToken("broken")).thenThrow(new IllegalArgumentException("bad token"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingClaimsUnknownRolesAndUnknownAccountsFailClosed() throws Exception {
        when(jwtUtils.extractUserIdAsLong(TOKEN)).thenReturn((Long) null, 1L, 1L, 1L);
        when(jwtUtils.extractRole(TOKEN)).thenReturn("user", null, "service", "user");
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(1L);

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void activeUnlockedAdminTokenEstablishesAuthentication() throws Exception {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setIsActive(true);
        admin.setAuthVersion(2L);
        admin.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(jwtUtils.extractRole(TOKEN)).thenReturn("admin");
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(2L);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void staleActiveAdminTokenFailsClosed() throws Exception {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setIsActive(true);
        admin.setAuthVersion(3L);
        when(jwtUtils.extractRole(TOKEN)).thenReturn("admin");
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(2L);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingAdminAndLockedUserTokensFailClosed() throws Exception {
        when(jwtUtils.extractRole(TOKEN)).thenReturn("admin");
        when(jwtUtils.extractAuthVersion(TOKEN)).thenReturn(2L);
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        when(jwtUtils.extractRole(TOKEN)).thenReturn("user");
        User locked = activeUser(2L);
        locked.setLockedUntil(LocalDateTime.now().plusMinutes(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(locked));
        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private User activeUser(long authVersion) {
        User user = new User();
        user.setId(1L);
        user.setIsActive(true);
        user.setAuthVersion(authVersion);
        return user;
    }
}
