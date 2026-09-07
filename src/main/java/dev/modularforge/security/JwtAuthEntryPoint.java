package dev.modularforge.security;

import dev.modularforge.identity.model.User;

import tools.jackson.databind.ObjectMapper;
import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthEntryPoint.class);

    @Autowired
    private AuthenticationErrorAudit authErrorLogService;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        logger.error("Unauthorized error: {}", authException.getMessage());
        if (!isBotScannerRequest(request)) {
            try {
                authErrorLogService.log401(
                    getClientIP(request),
                    request.getHeader("User-Agent"),
                    request.getRequestURI(),
                    request.getMethod(),
                    authException.getMessage()
                );
            } catch (Exception e) {
                logger.error("Failed to log 401 error: {}", e.getMessage());
            }
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        final Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", "Authentication required to access this resource");
        body.put("path", request.getServletPath());
        body.put("timestamp", System.currentTimeMillis());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }

    private String getClientIP(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
    private boolean isBotScannerRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("/.")) {
            return true;
        }
        String lowerUri = uri.toLowerCase();
        return lowerUri.contains("wp-includes") ||
               lowerUri.contains("wp-content") ||
               lowerUri.contains("/wordpress/") ||
               lowerUri.contains("/wp/") ||
               lowerUri.endsWith("xmlrpc.php") ||
               lowerUri.endsWith(".xml") ||
               lowerUri.endsWith(".yml") ||
               lowerUri.endsWith(".yaml") ||
               lowerUri.endsWith("web.config");
    }

}
