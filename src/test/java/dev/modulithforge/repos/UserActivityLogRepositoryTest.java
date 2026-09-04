package dev.modulithforge.repos;

import dev.modulithforge.audit.UserActivityLogRepository;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.audit.UserActivityLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserActivityLogRepository")
class UserActivityLogRepositoryTest {

    @Autowired
    private UserActivityLogRepository logRepository;

    private final LocalDateTime NOW = LocalDateTime.now();

    private UserActivityLog buildLog(Long userId, String role, String action,
                                     boolean success, String ipAddress) {
        UserActivityLog log = new UserActivityLog();
        log.setUserId(userId);
        log.setRole(role);
        log.setAction(action);
        log.setSuccess(success);
        log.setIpAddress(ipAddress);
        return log;
    }

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        logRepository.saveAll(List.of(
            buildLog(1L, "user", "LOGIN",          true,  "1.1.1.1"),
            buildLog(1L, "user", "PROFILE_UPDATE", true,  "1.1.1.1"),
            buildLog(1L, "user", "LOGIN",          false, "2.2.2.2"),  // failed attempt
            buildLog(2L, "user",  "LOGIN",          true,  "3.3.3.3"),
            buildLog(2L, "user",  "LOGOUT",         true,  "3.3.3.3")
        ));
    }

    @Nested
    @DisplayName("findByUserIdAndRole")
    class FindByUserTests {

        @Test
        @DisplayName("returns all logs for given user (paginated)")
        void paginatedForUser() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndRoleOrderByCreatedAtDesc(1L, "user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns all logs for given user (list)")
        void listForUser() {
            List<UserActivityLog> logs = logRepository
                    .findByUserIdAndRoleOrderByCreatedAtDesc(1L, "user");
            assertThat(logs).hasSize(3);
        }

        @Test
        @DisplayName("returns empty for unknown user")
        void emptyForUnknownUser() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndRoleOrderByCreatedAtDesc(99L, "user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByRole")
    class FindByRoleTests {

        @Test
        @DisplayName("returns logs for given role")
        void returnsForRole() {
            Page<UserActivityLog> page = logRepository
                    .findByRoleOrderByCreatedAtDesc("user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("findByAction")
    class FindByActionTests {

        @Test
        @DisplayName("returns logs for given action")
        void returnsForAction() {
            Page<UserActivityLog> page = logRepository
                    .findByActionOrderByCreatedAtDesc("LOGIN", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns empty for unknown action")
        void returnsEmptyForUnknownAction() {
            Page<UserActivityLog> page = logRepository
                    .findByActionOrderByCreatedAtDesc("UNKNOWN_ACTION", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByActionAndRole")
    class FindByActionAndRoleTests {

        @Test
        @DisplayName("filters by action AND role")
        void filtersCorrectly() {
            Page<UserActivityLog> page = logRepository
                    .findByActionAndRoleOrderByCreatedAtDesc("LOGIN", "user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
            assertThat(page.getContent().get(0).getUserId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findAllByOrderByCreatedAtDesc")
    class FindAllOrderedTests {

        @Test
        @DisplayName("returns all logs paginated")
        void returnsAll() {
            Page<UserActivityLog> page = logRepository
                    .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("findByCreatedAtAfterOrderByCreatedAtDesc")
    class DateAfterTests {

        @Test
        @DisplayName("returns logs after given date")
        void returnsAfterDate() {
            Page<UserActivityLog> page = logRepository
                    .findByCreatedAtAfterOrderByCreatedAtDesc(NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }

        @Test
        @DisplayName("returns empty when all logs are before the date")
        void returnsEmptyBeforeDate() {
            Page<UserActivityLog> page = logRepository
                    .findByCreatedAtAfterOrderByCreatedAtDesc(NOW.plusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByRoleAndCreatedAtAfter")
    class DateAfterByRoleTests {

        @Test
        @DisplayName("filters by role and date")
        void filtersCorrectly() {
            Page<UserActivityLog> page = logRepository
                    .findByRoleAndCreatedAtAfterOrderByCreatedAtDesc("user", NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndRoleAndCreatedAtAfter")
    class DateAfterByUserTests {

        @Test
        @DisplayName("filters by user and date range")
        void filtersCorrectly() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtDesc(
                            1L, "user", NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("countByUserIdAndRoleAndCreatedAtAfter")
    class CountByUserDateTests {

        @Test
        @DisplayName("counts recent logs for user")
        void countsRecent() {
            long count = logRepository.countByUserIdAndRoleAndCreatedAtAfter(
                    1L, "user", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns 0 for future cutoff date")
        void returnsZeroForFutureCutoff() {
            long count = logRepository.countByUserIdAndRoleAndCreatedAtAfter(
                    1L, "user", NOW.plusMinutes(1));
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("countByActionSince")
    class CountByActionTests {

        @Test
        @DisplayName("counts recent logs for given action")
        void countsAction() {
            long count = logRepository.countByActionSince("LOGIN", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns 0 for unknown action")
        void returnsZeroForUnknownAction() {
            long count = logRepository.countByActionSince("GHOST_ACTION", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("countBySuccessAndCreatedAtAfter")
    class CountBySuccessTests {

        @Test
        @DisplayName("counts successful logs")
        void countsSuccess() {
            long count = logRepository.countBySuccessAndCreatedAtAfter(true, NOW.minusMinutes(1));
            assertThat(count).isEqualTo(4L);
        }

        @Test
        @DisplayName("counts failed logs")
        void countsFailed() {
            long count = logRepository.countBySuccessAndCreatedAtAfter(false, NOW.minusMinutes(1));
            assertThat(count).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndRoleAndSuccessFalse")
    class FailedLogsTests {

        @Test
        @DisplayName("returns only failed logs for given user")
        void returnsFailedLogs() {
            Page<UserActivityLog> page = logRepository
                    .findByUserIdAndRoleAndSuccessFalseOrderByCreatedAtDesc(
                            1L, "user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(1L);
            assertThat(page.getContent().get(0).getSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("findByIpAddressOrderByCreatedAtDesc")
    class FindByIpTests {

        @Test
        @DisplayName("returns logs by IP address")
        void returnsForIp() {
            Page<UserActivityLog> page = logRepository
                    .findByIpAddressOrderByCreatedAtDesc("1.1.1.1", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("deleteByCreatedAtBefore")
    class DeleteBeforeTests {

        @Test
        @DisplayName("deletes logs older than given date")
        void deletesOldLogs() {
            long before = logRepository.count();
            int deleted = logRepository.deleteByCreatedAtBefore(NOW.plusMinutes(1));
            assertThat(deleted).isEqualTo((int) before);
            assertThat(logRepository.count()).isEqualTo(0L);
        }

        @Test
        @DisplayName("does not delete logs newer than the cutoff")
        void keepsNewLogs() {
            int deleted = logRepository.deleteByCreatedAtBefore(NOW.minusDays(1));
            assertThat(deleted).isEqualTo(0);
            assertThat(logRepository.count()).isEqualTo(5L);
        }
    }
}
