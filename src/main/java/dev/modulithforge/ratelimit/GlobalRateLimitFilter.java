package dev.modulithforge.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import dev.modulithforge.ratelimit.RateLimitService;

import java.io.IOException;
@Slf4j
@Component
@Order(1)
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        if (rateLimitService.isGlobalRateLimitExceeded(clientIp)) {
            log.warn("Global rate limit exceeded for IP: {}, URI: {}", clientIp, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again later.\",\"message\":\"Request limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
