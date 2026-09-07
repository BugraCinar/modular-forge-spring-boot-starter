package dev.modularforge.shared.error;

import dev.modularforge.security.JwtUtils;
import dev.modularforge.shared.audit.AuthenticationErrorAudit;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private AuthenticationErrorAudit audit;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private HttpServletRequest request;
    @Mock
    private Authentication authentication;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "authErrorLogService", audit);
        ReflectionTestUtils.setField(handler, "jwtUtils", jwtUtils);
        lenient().when(request.getRequestURI()).thenReturn("/api/items/404");
        lenient().when(request.getMethod()).thenReturn("GET");
        lenient().when(request.getRemoteAddr()).thenReturn("192.0.2.15");
        lenient().when(request.getHeader("User-Agent")).thenReturn("test-agent");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void handlesMissingPathAndResourceWithAuditing() {
        var missingPath = handler.handleNoHandlerFoundException(new Exception("missing"), request);
        assertError(missingPath.getStatusCode().value(), missingPath.getBody(), HttpStatus.NOT_FOUND,
                "The requested path does not exist");
        verify(audit).log404(null, null, null, "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "Path not found");

        reset(audit);
        var missingResource = handler.handleResourceNotFoundException(
                new ResourceNotFoundException("record missing"), request);
        assertError(missingResource.getStatusCode().value(), missingResource.getBody(), HttpStatus.NOT_FOUND,
                "record missing");
        verify(audit).log404(null, null, null, "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "record missing");

        reset(audit);
        var otherMissingResource = handler.handleResourceNotFoundException(
                new NotFoundException("also missing"), request);
        assertThat(otherMissingResource.getBody().getMessage()).isEqualTo("also missing");
    }

    @Test
    void handlesAuthorizationAuthenticationAndBadRequests() {
        var forbidden = handler.handleUnauthorizedException(new ForbiddenException("forbidden"), request);
        assertError(forbidden.getStatusCode().value(), forbidden.getBody(), HttpStatus.FORBIDDEN, "forbidden");
        verify(audit).log403(null, null, null, "192.0.2.15", "test-agent", "/api/items/404",
                "GET", "forbidden", "Access to protected resource");

        reset(audit);
        var unauthorized = handler.handleUnauthorizedException(new UnauthorizedException("unauthorized"), request);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        reset(audit);
        var denied = handler.handleAccessDeniedException(new AccessDeniedException("policy"), request);
        assertError(denied.getStatusCode().value(), denied.getBody(), HttpStatus.FORBIDDEN, "Access denied");
        verify(audit).logAccessDenied(null, null, null, "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "policy");

        reset(audit);
        var auth = handler.handleAuthenticationException(new BadCredentialsException("bad"), request);
        assertError(auth.getStatusCode().value(), auth.getBody(), HttpStatus.UNAUTHORIZED, "Authentication required");
        verify(audit).log401("192.0.2.15", "test-agent", "/api/items/404", "GET", "bad");

        reset(audit);
        var bad = handler.handleBadRequestException(new BadRequestException("invalid input"), request);
        assertError(bad.getStatusCode().value(), bad.getBody(), HttpStatus.BAD_REQUEST, "invalid input");
        verify(audit).log400(null, null, null, "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "invalid input");
    }

    @Test
    void returnsFieldValidationErrors() {
        MethodArgumentNotValidException exception = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        BindingResult result = org.mockito.Mockito.mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(result);
        when(result.getAllErrors()).thenReturn(List.of(
                new FieldError("request", "email", "must be valid"),
                new FieldError("request", "password", "must not be blank")));

        var response = handler.handleValidationExceptions(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Validation Failed")
                .containsEntry("path", "/api/items/404");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertThat(errors)
                .containsEntry("email", "must be valid")
                .containsEntry("password", "must not be blank");
        verify(audit).log400(null, null, null, "192.0.2.15", "test-agent", "/api/items/404",
                "GET", "Validation failed: {password=must not be blank, email=must be valid}");
    }

    @Test
    void genericErrorsNeverExposeInternalMessages() {
        var withMessage = handler.handleGenericException(new IllegalStateException("database password leaked"), request);
        assertError(withMessage.getStatusCode().value(), withMessage.getBody(), HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        assertThat(withMessage.getBody().getMessage()).doesNotContain("database password");
        verify(audit).log500(null, null, null, "192.0.2.15", "test-agent", "/api/items/404",
                "GET", "database password leaked");

        reset(audit);
        var withoutMessage = handler.handleGenericException(new RuntimeException(), request);
        assertThat(withoutMessage.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(audit).log500(null, null, null, "192.0.2.15", "test-agent", "/api/items/404",
                "GET", "RuntimeException");
    }

    @Test
    void extractsIdentityFromSecurityContextAndJwtFallback() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn(77L);
        when(authentication.getPrincipal()).thenReturn("context-user");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtils.extractRole("token")).thenReturn("ADMIN");

        handler.handleBadRequestException(new BadRequestException("bad"), request);

        verify(audit).log400(77L, "ADMIN", "context-user", "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "bad");
        verify(jwtUtils, never()).extractUsername(anyString());
        verify(jwtUtils, never()).extractUserIdAsLong(anyString());

        reset(audit, authentication, jwtUtils);
        when(authentication.isAuthenticated()).thenReturn(false);
        when(request.getHeader("Authorization")).thenReturn("Bearer fallback-token");
        when(jwtUtils.extractUsername("fallback-token")).thenReturn("jwt-user");
        when(jwtUtils.extractUserIdAsLong("fallback-token")).thenReturn(88L);
        when(jwtUtils.extractRole("fallback-token")).thenReturn("USER");

        handler.handleBadRequestException(new BadRequestException("bad"), request);
        verify(audit).log400(88L, "USER", "jwt-user", "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "bad");
    }

    @Test
    void toleratesInvalidJwtAndEveryAuditFailure() {
        when(request.getHeader("Authorization")).thenReturn("Basic credentials");
        handler.handleBadRequestException(new BadRequestException("first"), request);
        verify(jwtUtils, never()).extractUsername(anyString());

        when(request.getHeader("Authorization")).thenReturn("Bearer broken");
        when(jwtUtils.extractUsername("broken")).thenThrow(new IllegalArgumentException("bad jwt"));
        handler.handleBadRequestException(new BadRequestException("second"), request);

        doThrow(new IllegalStateException("audit down")).when(audit)
                .log404(any(), any(), any(), any(), any(), any(), any(), any());
        handler.handleNoHandlerFoundException(new Exception(), request);
        handler.handleResourceNotFoundException(new NotFoundException("missing"), request);

        doThrow(new IllegalStateException("audit down")).when(audit)
                .log403(any(), any(), any(), any(), any(), any(), any(), any(), any());
        handler.handleUnauthorizedException(new ForbiddenException("no"), request);

        doThrow(new IllegalStateException("audit down")).when(audit)
                .logAccessDenied(any(), any(), any(), any(), any(), any(), any(), any());
        handler.handleAccessDeniedException(new AccessDeniedException("no"), request);

        doThrow(new IllegalStateException("audit down")).when(audit)
                .log401(any(), any(), any(), any(), any());
        handler.handleAuthenticationException(new BadCredentialsException("no"), request);

        doThrow(new IllegalStateException("audit down")).when(audit)
                .log400(any(), any(), any(), any(), any(), any(), any(), any());
        handler.handleBadRequestException(new BadRequestException("no"), request);

        MethodArgumentNotValidException validation = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        BindingResult binding = org.mockito.Mockito.mock(BindingResult.class);
        when(validation.getBindingResult()).thenReturn(binding);
        when(binding.getAllErrors()).thenReturn(List.of());
        handler.handleValidationExceptions(validation, request);

        doThrow(new IllegalStateException("audit down")).when(audit)
                .log500(any(), any(), any(), any(), any(), any(), any(), any());
        handler.handleGenericException(new RuntimeException("boom"), request);
    }

    @Test
    void unexpectedSecurityContextFailureDoesNotBreakErrorHandling() {
        SecurityContext context = org.mockito.Mockito.mock(SecurityContext.class);
        when(context.getAuthentication()).thenThrow(new IllegalStateException("context unavailable"));
        SecurityContextHolder.setContext(context);

        var response = handler.handleBadRequestException(new BadRequestException("bad"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(audit).log400(null, null, null, "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "bad");
    }

    @Test
    void nonLongDetailsAndNonStringPrincipalAreIgnored() {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getDetails()).thenReturn("details");
        when(authentication.getPrincipal()).thenReturn(new Object());

        handler.handleBadRequestException(new BadRequestException("bad"), request);

        verify(audit).log400(null, null, null, "192.0.2.15", "test-agent",
                "/api/items/404", "GET", "bad");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/.env", "/wp-includes/a", "/wp-content/a", "/wordpress/admin", "/wp/login",
            "/xmlrpc.php", "/sitemap.xml", "/config.yml", "/config.yaml", "/web.config", "/.git/config"
    })
    void ignoresCommonBotScannerPaths(String path) {
        when(request.getRequestURI()).thenReturn(path);

        var response = handler.handleNoHandlerFoundException(new Exception(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(audit, never()).log404(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void botRequestsSkipAuditingInEverySecurityExceptionHandler() {
        when(request.getRequestURI()).thenReturn("/.env");

        handler.handleResourceNotFoundException(new NotFoundException("missing"), request);
        handler.handleUnauthorizedException(new ForbiddenException("forbidden"), request);
        handler.handleAccessDeniedException(new AccessDeniedException("denied"), request);
        handler.handleAuthenticationException(new BadCredentialsException("bad"), request);

        verify(audit, never()).log404(any(), any(), any(), any(), any(), any(), any(), any());
        verify(audit, never()).log403(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(audit, never()).logAccessDenied(any(), any(), any(), any(), any(), any(), any(), any());
        verify(audit, never()).log401(any(), any(), any(), any(), any());
    }

    private void assertError(int actualStatus, ErrorResponse body, HttpStatus expected, String messagePart) {
        assertThat(actualStatus).isEqualTo(expected.value());
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(expected.value());
        assertThat(body.getMessage()).contains(messagePart);
        assertThat(body.getPath()).isEqualTo("/api/items/404");
        assertThat(body.getTimestamp()).isNotNull();
    }
}
