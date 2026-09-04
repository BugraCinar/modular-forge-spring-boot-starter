package dev.modulithforge.services;

import dev.modulithforge.audit.AdminActivityLogger;
import dev.modulithforge.audit.UserActivityLogService;
import dev.modulithforge.shared.PaginationUtils;

import dev.modulithforge.identity.model.User;
import dev.modulithforge.audit.UserActivityLog;
import dev.modulithforge.audit.UserActivityLogRepository;
import dev.modulithforge.identity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceTest {

    @Mock
    private UserActivityLogRepository activityLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminActivityLogger adminActivityLogger;
    @Mock
    private HttpServletRequest request;

    @SuppressWarnings("unchecked")
    @Test
    void boundsClientPaginationAndBatchLoadsUsersForAnActivityPage() {
        UserActivityLog first = activityLog(1L, 10L);
        UserActivityLog second = activityLog(2L, 11L);
        User firstUser = user(10L, "first");
        User secondUser = user(11L, "second");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(activityLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(userRepository.findAllById(any())).thenReturn(List.of(firstUser, secondUser));

        UserActivityLogService service = new UserActivityLogService(
                activityLogRepository, userRepository, adminActivityLogger);
        var response = service.getAllUserActivityLogs(
                null, null, null, null, null, null, null, null,
                -5, 10_000, "createdAt", "desc", 99L, request);

        verify(activityLogRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(PaginationUtils.MAX_PAGE_SIZE);
        assertThat(response.getLogs()).extracting(log -> log.getUsername())
                .containsExactly("first", "second");
        verify(userRepository).findAllById(any());
        verify(userRepository, never()).findById(any());
    }

    private UserActivityLog activityLog(Long id, Long userId) {
        UserActivityLog log = new UserActivityLog();
        log.setId(id);
        log.setUserId(userId);
        log.setRole("user");
        log.setAction("LOGIN");
        log.setSuccess(true);
        return log;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        return user;
    }
}
