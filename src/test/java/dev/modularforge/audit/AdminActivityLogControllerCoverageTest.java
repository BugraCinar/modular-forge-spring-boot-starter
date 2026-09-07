package dev.modularforge.audit;

import dev.modularforge.audit.dto.AdminActivityLogDTO;
import dev.modularforge.audit.dto.AdminActivityLogListResponse;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActivityLogControllerCoverageTest {

    @Mock AdminActivityLogService service;
    @Mock AdminRepository admins;
    @Mock JwtUtils jwtUtils;
    @Mock HttpServletRequest request;

    private AdminActivityLogController controller;
    private Admin admin;

    @BeforeEach
    void setUp() {
        controller = new AdminActivityLogController(service, admins, jwtUtils);
        admin = new Admin();
        admin.setId(7L);
        admin.setLevel(0);
        when(jwtUtils.extractUserId("jwt")).thenReturn(7);
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
    }

    @Test
    void returnsSuccessfulListAndItem() {
        AdminActivityLogListResponse list = new AdminActivityLogListResponse();
        AdminActivityLogDTO item = new AdminActivityLogDTO();
        item.setId(4L);
        when(service.getAllActivityLogs(null, null, null, null, 0, 20,
                "createdAt", "desc", 7L, request)).thenReturn(list);
        when(service.getActivityLogById(4L, 7L, request)).thenReturn(item);

        assertThat(controller.getAllActivityLogs("Bearer jwt", null, null, null, null,
                0, 20, "createdAt", "desc", request).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.getActivityLogById("Bearer jwt", 4L, request).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsNonSuperAdminForBothOperations() {
        admin.setLevel(1);

        assertThat(controller.getAllActivityLogs("Bearer jwt", null, null, null, null,
                0, 20, "createdAt", "desc", request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.getActivityLogById("Bearer jwt", 4L, request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void mapsItemLookupFailuresWithoutLeakingDetails() throws Exception {
        when(service.getActivityLogById(1L, 7L, request)).thenThrow(new RuntimeException("entry not found"));
        when(service.getActivityLogById(2L, 7L, request)).thenThrow(new RuntimeException("database detail"));
        when(service.getActivityLogById(3L, 7L, request)).thenThrow(new RuntimeException());
        doAnswer(invocation -> { throw new IOException("socket detail"); })
                .when(service).getActivityLogById(4L, 7L, request);

        assertThat(controller.getActivityLogById("Bearer jwt", 1L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.getActivityLogById("Bearer jwt", 2L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getActivityLogById("Bearer jwt", 3L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getActivityLogById("Bearer jwt", 4L, request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void missingAdminIsMappedToGenericErrors() {
        when(admins.findById(7L)).thenReturn(Optional.empty());

        assertThat(controller.getAllActivityLogs("Bearer jwt", null, null, null, null,
                0, 20, "createdAt", "desc", request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(controller.getActivityLogById("Bearer jwt", 4L, request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
