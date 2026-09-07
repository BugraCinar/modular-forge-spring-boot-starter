package dev.modularforge.audit;

import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.model.Admin;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminActivityLogServiceTest {

    @Mock AdminActivityLogRepository repository;
    @Mock AdminRepository adminRepository;
    @Mock AdminActivityLogger activityLogger;
    @Mock HttpServletRequest request;
    private AdminActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new AdminActivityLogService(repository, adminRepository, activityLogger);
    }

    @Test
    void listsFilteredAndUnfilteredLogsWithKnownAndUnknownAdmins() {
        AdminActivityLog known = log(1L, 10L);
        AdminActivityLog unknown = log(2L, 11L);
        var page = new PageImpl<>(List.of(known, unknown));
        Admin admin = new Admin();
        admin.setId(10L);
        admin.setUsername("root");
        admin.setEmail("root@example.com");
        when(adminRepository.findAllById(any())).thenReturn(List.of(admin));
        when(repository.findByAdminIdOrderByCreatedAtDesc(eq(10L), any(Pageable.class))).thenReturn(page);
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(page);

        var filtered = service.getAllActivityLogs(10L, "READ", "User", LocalDateTime.now(),
                -1, 1000, "id", "asc", 1L, request);
        var all = service.getAllActivityLogs(null, null, null, null,
                0, 20, "unsafe", "desc", 1L, request);
        service.getAllActivityLogs(null, null, null, null,
                0, 20, null, "desc", 1L, request);
        service.getAllActivityLogs(null, null, null, null,
                0, 20, "", "desc", 1L, request);

        assertThat(filtered.getLogs()).extracting("adminUsername").containsExactly("root", "Unknown");
        assertThat(all.getLogs()).hasSize(2);
    }

    @Test
    void getsDeletesCleansAndCountsLogs() {
        AdminActivityLog log = log(1L, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(adminRepository.findAllById(any())).thenReturn(List.of());
        when(repository.existsById(1L)).thenReturn(true);
        when(repository.existsById(99L)).thenReturn(false);
        when(repository.findByCreatedAtAfterOrderByCreatedAtDesc(any())).thenReturn(List.of(log));
        when(repository.countByAdminIdAndCreatedAtAfter(eq(10L), any())).thenReturn(7L);

        assertThat(service.getActivityLogById(1L, 9L, request).getAdminUsername()).isEqualTo("Unknown");
        service.deleteActivityLog(1L);
        assertThat(service.deleteOldActivityLogs(LocalDateTime.now())).isEqualTo(1);
        assertThat(service.getActivityCountForAdmin(10L, LocalDateTime.now())).isEqualTo(7);
        verify(repository).deleteById(1L);
        verify(repository).deleteAll(List.of(log));

        assertThatThrownBy(() -> service.getActivityLogById(99L, 9L, request)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.deleteActivityLog(99L)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void emptyLogPageDoesNotQueryAdminOwners() {
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.getAllActivityLogs(null, null, null, null,
                0, 20, "createdAt", "desc", 1L, request).getLogs()).isEmpty();

        org.mockito.Mockito.verifyNoInteractions(adminRepository);
    }

    private AdminActivityLog log(Long id, Long adminId) {
        AdminActivityLog log = new AdminActivityLog();
        log.setId(id);
        log.setAdminId(adminId);
        log.setAction("READ");
        log.setResourceType("User");
        log.setResourceId("4");
        log.setDetails("{}");
        log.setIpAddress("127.0.0.1");
        log.setUserAgent("JUnit");
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}
