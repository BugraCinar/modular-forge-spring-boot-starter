package dev.modularforge.audit;

import dev.modularforge.audit.dto.UserActivityLogListResponse;
import dev.modularforge.identity.UserRepository;
import dev.modularforge.identity.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceCoverageTest {

    @Mock
    private UserActivityLogRepository repository;
    @Mock
    private UserRepository users;
    @Mock
    private AdminActivityLogger adminLogger;
    @Mock
    private HttpServletRequest request;

    private UserActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new UserActivityLogService(repository, users, adminLogger);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void appliesEveryListFilterAndBuildsDtosForKnownAndUnknownUsers() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now();
        UserActivityLog known = log(1L, 10L, "user");
        UserActivityLog unknown = log(2L, 11L, "USER");
        UserActivityLog admin = log(3L, 12L, "admin");
        User user = new User();
        user.setId(10L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        ArgumentCaptor<Specification<UserActivityLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(known, unknown, admin), PageRequest.of(0, 10), 13));
        when(users.findAllById(any())).thenReturn(List.of(user));

        UserActivityLogListResponse result = service.getAllUserActivityLogs(
                10L, "USER", "LOGIN", "SESSION", false, start, end, "192.0.2.1",
                0, 10, "action", "asc", 99L, request);

        verify(repository).findAll(specCaptor.capture(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("action").getDirection().isAscending()).isTrue();
        assertThat(result.getLogs()).extracting("username").containsExactly("alice", "Unknown", "Unknown");
        assertThat(result.getTotalElements()).isEqualTo(13);

        Root<UserActivityLog> root = org.mockito.Mockito.mock(Root.class);
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        Expression expression = org.mockito.Mockito.mock(Expression.class);
        Predicate predicate = org.mockito.Mockito.mock(Predicate.class);
        lenient().when(root.get(any(String.class))).thenReturn(path);
        lenient().when(cb.lower(any(Expression.class))).thenReturn(expression);
        lenient().when(cb.equal(any(Expression.class), any())).thenReturn(predicate);
        lenient().when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        lenient().when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        assertThat(specCaptor.getValue().toPredicate(root, query, cb)).isSameAs(predicate);
        verify(cb).and(any(Predicate[].class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void supportsEmptyFiltersInvalidSortAndDescendingDirection() {
        ArgumentCaptor<Specification<UserActivityLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        UserActivityLogListResponse result = service.getAllUserActivityLogs(
                null, "", "", "", null, null, null, "",
                0, 20, "not-a-field", "anything", 99L, request);

        verify(repository).findAll(specCaptor.capture(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection().isDescending()).isTrue();
        assertThat(result.getLogs()).isEmpty();

        Root<UserActivityLog> root = org.mockito.Mockito.mock(Root.class);
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        Predicate predicate = org.mockito.Mockito.mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        assertThat(specCaptor.getValue().toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void specificationAcceptsNullTextFilters() {
        ArgumentCaptor<Specification<UserActivityLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAllUserActivityLogs(null, null, null, null, null, null, null, null,
                0, 20, "createdAt", "desc", 99L, request);

        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));
        Root<UserActivityLog> root = org.mockito.Mockito.mock(Root.class);
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        Predicate predicate = org.mockito.Mockito.mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        assertThat(specCaptor.getValue().toPredicate(root, query, cb)).isSameAs(predicate);
    }

    @Test
    void readsSingleLogAndReportsMissingLog() {
        UserActivityLog activity = log(7L, 10L, "user");
        User user = new User();
        user.setId(10L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        when(repository.findById(7L)).thenReturn(Optional.of(activity));
        when(users.findAllById(any())).thenReturn(List.of(user));

        assertThat(service.getUserActivityLogById(7L, 99L, request).getUsername()).isEqualTo("alice");
        verify(adminLogger).logActivity(eq(99L), eq("READ"), eq("UserActivityLog"), eq("7"), any(), eq(request));

        when(repository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUserActivityLogById(8L, 99L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("8");
    }

    @Test
    void listsLogsForUserWithSafeSortFallbacks() {
        UserActivityLog activity = log(1L, 10L, "admin");
        when(repository.findByUserIdAndRoleOrderByCreatedAtDesc(eq(10L), eq("admin"), any()))
                .thenReturn(new PageImpl<>(List.of(activity)));

        assertThat(service.getUserActivityLogsByUser(10L, "admin", 0, 20,
                null, "asc", 99L, request).getLogs()).hasSize(1);

        when(repository.findByUserIdAndRoleOrderByCreatedAtDesc(eq(10L), eq("admin"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.getUserActivityLogsByUser(10L, "admin", 0, 20,
                "", "desc", 99L, request).getLogs()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deletesOldLogsAndCalculatesStatistics() {
        UserActivityLog first = log(1L, 10L, "user");
        UserActivityLog second = log(2L, 11L, "user");
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(first, second));
        LocalDateTime before = LocalDateTime.now().minusDays(30);

        assertThat(service.deleteOldActivityLogs(before)).isEqualTo(2);
        verify(repository).deleteAll(List.of(first, second));

        ArgumentCaptor<Specification<UserActivityLog>> deleteSpec = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(deleteSpec.capture());
        Root<UserActivityLog> root = org.mockito.Mockito.mock(Root.class);
        CriteriaQuery<?> query = org.mockito.Mockito.mock(CriteriaQuery.class);
        CriteriaBuilder cb = org.mockito.Mockito.mock(CriteriaBuilder.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        Predicate predicate = org.mockito.Mockito.mock(Predicate.class);
        when(root.get("createdAt")).thenReturn(path);
        when(cb.lessThan(path, before)).thenReturn(predicate);
        assertThat(deleteSpec.getValue().toPredicate(root, query, cb)).isSameAs(predicate);

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        when(repository.countByActionSince("LOGIN", since)).thenReturn(1L);
        when(repository.countByActionSince("REGISTER", since)).thenReturn(2L);
        when(repository.countByActionSince("PASSWORD_RESET_COMPLETE", since)).thenReturn(3L);
        when(repository.countByActionSince("PROFILE_UPDATE", since)).thenReturn(4L);
        when(repository.countBySuccessAndCreatedAtAfter(true, since)).thenReturn(5L);
        when(repository.countBySuccessAndCreatedAtAfter(false, since)).thenReturn(6L);

        assertThat(service.getActivityStatistics(since))
                .containsEntry("totalLogins", 1L)
                .containsEntry("totalRegistrations", 2L)
                .containsEntry("totalPasswordResets", 3L)
                .containsEntry("totalProfileUpdates", 4L)
                .containsEntry("successfulActions", 5L)
                .containsEntry("failedActions", 6L);
    }

    private UserActivityLog log(Long id, Long userId, String role) {
        UserActivityLog log = new UserActivityLog();
        log.setId(id);
        log.setUserId(userId);
        log.setRole(role);
        log.setAction("LOGIN");
        log.setResourceType("SESSION");
        log.setResourceId("r-1");
        log.setDetails("{}");
        log.setIpAddress("192.0.2.1");
        log.setUserAgent("browser");
        log.setSuccess(true);
        log.setFailureReason(null);
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}
