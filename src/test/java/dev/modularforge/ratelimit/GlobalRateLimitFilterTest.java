package dev.modularforge.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalRateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private FilterChain filterChain;

    private GlobalRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GlobalRateLimitFilter();
        ReflectionTestUtils.setField(filter, "rateLimitService", rateLimitService);
    }

    @Test
    void continuesWhenClientIsWithinLimit() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimitService.isGlobalRateLimitExceeded("203.0.113.10")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void returnsJsonTooManyRequestsWhenLimitIsExceeded() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimitService.isGlobalRateLimitExceeded("203.0.113.10")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("Too many requests", "Request limit exceeded");
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/profile");
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
