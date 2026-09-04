package dev.modularforge.shared.error;

import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import dev.modularforge.security.JwtUtils;
import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired
    private AuthenticationErrorAudit authErrorLogService;

    @Autowired
    private JwtUtils jwtUtils;
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Path not found: {} {}", request.getMethod(), request.getRequestURI());
        if (!isBotScannerRequest(request)) {
            UserInfo userInfo = extractUserInfo(request);
            try {
                authErrorLogService.log404(
                    userInfo.userId,
                    userInfo.role,
                    userInfo.username,
                    getClientIP(request),
                    request.getHeader("User-Agent"),
                    request.getRequestURI(),
                    request.getMethod(),
                    "Path not found"
                );
            } catch (Exception e) {
                log.error("Failed to log 404 error: {}", e.getMessage());
            }
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                "The requested path does not exist: " + request.getRequestURI(),
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    @ExceptionHandler({ResourceNotFoundException.class, NotFoundException.class})
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Resource not found: {}", ex.getMessage());
        if (!isBotScannerRequest(request)) {
            UserInfo userInfo = extractUserInfo(request);
            try {
                authErrorLogService.log404(
                    userInfo.userId,
                    userInfo.role,
                    userInfo.username,
                    getClientIP(request),
                    request.getHeader("User-Agent"),
                    request.getRequestURI(),
                    request.getMethod(),
                    ex.getMessage()
                );
            } catch (Exception e) {
                log.error("Failed to log 404 error: {}", e.getMessage());
            }
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    @ExceptionHandler({UnauthorizedException.class, ForbiddenException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unauthorized/Forbidden access: {}", ex.getMessage());
        if (!isBotScannerRequest(request)) {
            UserInfo userInfo = extractUserInfo(request);
            try {
                authErrorLogService.log403(
                    userInfo.userId,
                    userInfo.role,
                    userInfo.username,
                    getClientIP(request),
                    request.getHeader("User-Agent"),
                    request.getRequestURI(),
                    request.getMethod(),
                    ex.getMessage(),
                    "Access to protected resource"
                );
            } catch (Exception e) {
                log.error("Failed to log 403 error: {}", e.getMessage());
            }
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.error("Access denied: {}", ex.getMessage());
        if (!isBotScannerRequest(request)) {
            UserInfo userInfo = extractUserInfo(request);
            try {
                authErrorLogService.logAccessDenied(
                    userInfo.userId,
                    userInfo.role,
                    userInfo.username,
                    getClientIP(request),
                    request.getHeader("User-Agent"),
                    request.getRequestURI(),
                    request.getMethod(),
                    ex.getMessage()
                );
            } catch (Exception e) {
                log.error("Failed to log access denied error: {}", e.getMessage());
            }
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Access denied. You do not have permission to access this resource.",
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.error("Authentication error: {}", ex.getMessage());
        if (!isBotScannerRequest(request)) {
            try {
                authErrorLogService.log401(
                    getClientIP(request),
                    request.getHeader("User-Agent"),
                    request.getRequestURI(),
                    request.getMethod(),
                    ex.getMessage()
                );
            } catch (Exception e) {
                log.error("Failed to log 401 error: {}", e.getMessage());
            }
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication required to access this resource.",
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex,
            HttpServletRequest request) {

        log.error("Bad request: {}", ex.getMessage());
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log400(
                userInfo.userId,
                userInfo.role,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to log 400 error: {}", e.getMessage());
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("errors", errors);
        response.put("path", request.getRequestURI());
        response.put("timestamp", LocalDateTime.now());

        log.error("Validation failed: {}", errors);
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log400(
                userInfo.userId,
                userInfo.role,
                userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                "Validation failed: " + errors
            );
        } catch (Exception e) {
            log.error("Failed to log validation error: {}", e.getMessage());
        }

        return ResponseEntity.badRequest().body(response);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Internal server error: ", ex);
        UserInfo userInfo = extractUserInfo(request);
        try {
            authErrorLogService.log500(
                    userInfo.userId,
                    userInfo.role,
                    userInfo.username,
                getClientIP(request),
                request.getHeader("User-Agent"),
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()
            );
        } catch (Exception e) {
            log.error("Failed to log 500 error: {}", e.getMessage());
        }

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    private String getClientIP(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
    private UserInfo extractUserInfo(HttpServletRequest request) {
        UserInfo userInfo = new UserInfo();

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                if (authentication.getDetails() instanceof Long) {
                    userInfo.userId = (Long) authentication.getDetails();
                }
                if (authentication.getPrincipal() instanceof String) {
                    userInfo.username = (String) authentication.getPrincipal();
                }
            }
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    if (userInfo.username == null) {
                        userInfo.username = jwtUtils.extractUsername(token);
                    }
                    if (userInfo.userId == null) {
                        userInfo.userId = jwtUtils.extractUserIdAsLong(token);
                    }
                    if (userInfo.role == null) {
                        userInfo.role = jwtUtils.extractRole(token);
                    }
                } catch (Exception e) {
                    log.debug("Could not extract user info from JWT token: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting user info: {}", e.getMessage());
        }

        return userInfo;
    }
    private static class UserInfo {
        Long userId;
        String role;
        String username;
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
               lowerUri.endsWith("web.config") ||
               lowerUri.contains("/.git/");
    }

}
