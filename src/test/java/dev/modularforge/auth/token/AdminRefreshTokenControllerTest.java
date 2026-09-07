package dev.modularforge.auth.token;

import dev.modularforge.auth.token.dto.RefreshTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AdminRefreshTokenControllerTest {

    private RefreshTokenService service;
    private AdminRefreshTokenController controller;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(RefreshTokenService.class);
        controller = new AdminRefreshTokenController(service);
    }

    @Test
    void everyEndpointReturnsSuccessfulAndNotFoundResponses() {
        RefreshTokenResponse token = org.mockito.Mockito.mock(RefreshTokenResponse.class);
        when(service.getFilteredTokens("user", 1L, false, "127.0.0.1")).thenReturn(List.of(token));
        when(service.getTokenById(1L)).thenReturn(Optional.of(token));
        when(service.getTokenById(2L)).thenReturn(Optional.empty());
        when(service.getActiveTokensForUser(1L, "user")).thenReturn(List.of(token));
        when(service.getTokenStatistics()).thenReturn(Map.of("totalTokens", 1));
        when(service.revokeTokenById(1L)).thenReturn(true);
        when(service.revokeTokenById(2L)).thenReturn(false);
        when(service.revokeAllUserTokens(1L, "user")).thenReturn(3);
        when(service.deleteTokenById(1L)).thenReturn(true);
        when(service.deleteTokenById(2L)).thenReturn(false);
        when(service.cleanupExpiredTokens()).thenReturn(4);

        assertThat(controller.getAllTokens("user", 1L, false, "127.0.0.1").getStatusCode().value()).isEqualTo(200);
        assertThat(controller.getTokenById(1L).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.getTokenById(2L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.getActiveTokensForUser(1L, "user").getStatusCode().value()).isEqualTo(200);
        assertThat(controller.getTokenStatistics().getStatusCode().value()).isEqualTo(200);
        assertThat(controller.revokeToken(1L).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.revokeToken(2L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.revokeAllUserTokens(1L, "user").getStatusCode().value()).isEqualTo(200);
        assertThat(controller.deleteToken(1L).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.deleteToken(2L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.triggerCleanup().getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void everyEndpointContainsUnexpectedServiceFailures() {
        when(service.getFilteredTokens(any(), any(), any(), any())).thenAnswer(call -> { throw new IOException("down"); });
        when(service.getTokenById(any())).thenAnswer(call -> { throw new IOException("down"); });
        when(service.getActiveTokensForUser(any(), any())).thenAnswer(call -> { throw new IOException("down"); });
        when(service.getTokenStatistics()).thenAnswer(call -> { throw new IOException("down"); });
        when(service.revokeTokenById(any())).thenAnswer(call -> { throw new IOException("down"); });
        when(service.revokeAllUserTokens(any(), any())).thenAnswer(call -> { throw new IOException("down"); });
        when(service.deleteTokenById(any())).thenAnswer(call -> { throw new IOException("down"); });
        when(service.cleanupExpiredTokens()).thenAnswer(call -> { throw new IOException("down"); });

        assertThat(controller.getAllTokens(null, null, null, null).getStatusCode().value()).isEqualTo(500);
        assertThat(controller.getTokenById(1L).getStatusCode().value()).isEqualTo(500);
        assertThat(controller.getActiveTokensForUser(1L, "user").getStatusCode().value()).isEqualTo(500);
        assertThat(controller.getTokenStatistics().getStatusCode().value()).isEqualTo(500);
        assertThat(controller.revokeToken(1L).getStatusCode().value()).isEqualTo(500);
        assertThat(controller.revokeAllUserTokens(1L, "user").getStatusCode().value()).isEqualTo(500);
        assertThat(controller.deleteToken(1L).getStatusCode().value()).isEqualTo(500);
        assertThat(controller.triggerCleanup().getStatusCode().value()).isEqualTo(500);
    }
}
