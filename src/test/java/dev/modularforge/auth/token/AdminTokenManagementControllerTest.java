package dev.modularforge.auth.token;

import dev.modularforge.auth.token.dto.PasswordResetTokenDTO;
import dev.modularforge.auth.token.dto.TokenListResponse;
import dev.modularforge.auth.token.dto.VerificationTokenDTO;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTokenManagementControllerTest {

    @Mock AdminTokenManagementService service;
    @Mock AdminRepository admins;
    @Mock JwtUtils jwtUtils;
    @Mock HttpServletRequest request;

    private AdminTokenManagementController controller;
    private Admin admin;

    @BeforeEach
    void setUp() {
        controller = new AdminTokenManagementController(service, admins, jwtUtils);
        admin = new Admin();
        admin.setId(7L);
        admin.setLevel(0);
        when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
    }

    @Test
    void handlesEverySuccessfulTokenOperation() {
        TokenListResponse<PasswordResetTokenDTO> resetList = org.mockito.Mockito.mock(TokenListResponse.class);
        PasswordResetTokenDTO resetToken = org.mockito.Mockito.mock(PasswordResetTokenDTO.class);
        TokenListResponse<VerificationTokenDTO> verificationList = org.mockito.Mockito.mock(TokenListResponse.class);
        VerificationTokenDTO verificationToken = org.mockito.Mockito.mock(VerificationTokenDTO.class);
        when(service.getAllPasswordResetTokens(any(), any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(resetList);
        when(service.getPasswordResetTokenById(1L, 7L, request)).thenReturn(resetToken);
        when(service.getAllVerificationTokens(any(), any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(verificationList);
        when(service.getVerificationTokenById(2L, 7L, request)).thenReturn(verificationToken);

        assertThat(controller.getAllPasswordResetTokens("Bearer jwt", "user", true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.getPasswordResetTokenById("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.deletePasswordResetToken("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.getAllVerificationTokens("Bearer jwt", "admin", false, 1, 10,
                "expiryDate", "asc", request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.getVerificationTokenById("Bearer jwt", 2L, request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.deleteVerificationToken("Bearer jwt", 2L, request).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsEveryOperationForNonLevelZeroAdmin() {
        admin.setLevel(1);

        assertThat(controller.getAllPasswordResetTokens("Bearer jwt", null, true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.getPasswordResetTokenById("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.deletePasswordResetToken("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.getAllVerificationTokens("Bearer jwt", null, true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.getVerificationTokenById("Bearer jwt", 2L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.deleteVerificationToken("Bearer jwt", 2L, request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void mapsListAndRuntimeFailuresToExpectedStatuses() {
        when(service.getAllPasswordResetTokens(any(), any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("failure"));
        when(service.getAllVerificationTokens(any(), any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("failure"));
        assertThat(controller.getAllPasswordResetTokens("Bearer jwt", null, true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getAllVerificationTokens("Bearer jwt", null, true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        when(service.getPasswordResetTokenById(1L, 7L, request)).thenThrow(new RuntimeException("reset not found"));
        when(service.getVerificationTokenById(2L, 7L, request)).thenThrow(new RuntimeException("verification not found"));
        doThrow(new RuntimeException("reset not found")).when(service).deletePasswordResetToken(1L, 7L, request);
        doThrow(new RuntimeException("verification not found")).when(service).deleteVerificationToken(2L, 7L, request);
        assertThat(controller.getPasswordResetTokenById("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.getVerificationTokenById("Bearer jwt", 2L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.deletePasswordResetToken("Bearer jwt", 1L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.deleteVerificationToken("Bearer jwt", 2L, request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mapsUnexpectedCheckedFailuresToServerErrors() {
        doAnswer(invocation -> { throw new IOException("io"); })
                .when(service).getPasswordResetTokenById(1L, 7L, request);
        doAnswer(invocation -> { throw new IOException("io"); })
                .when(service).getVerificationTokenById(2L, 7L, request);
        assertThat(controller.getPasswordResetTokenById("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getVerificationTokenById("Bearer jwt", 2L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        reset(service);
        doAnswer(invocation -> { throw new IOException("io"); })
                .when(service).deletePasswordResetToken(1L, 7L, request);
        doAnswer(invocation -> { throw new IOException("io"); })
                .when(service).deleteVerificationToken(2L, 7L, request);
        assertThat(controller.deletePasswordResetToken("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.deleteVerificationToken("Bearer jwt", 2L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void missingCurrentAdminIsHandledByEveryEndpoint() {
        when(admins.findById(7L)).thenReturn(Optional.empty());

        assertThat(controller.getAllPasswordResetTokens("Bearer jwt", null, true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getPasswordResetTokenById("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.deletePasswordResetToken("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.getAllVerificationTokens("Bearer jwt", null, true, 0, 20,
                "createdDate", "desc", request).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getVerificationTokenById("Bearer jwt", 2L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.deleteVerificationToken("Bearer jwt", 2L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
