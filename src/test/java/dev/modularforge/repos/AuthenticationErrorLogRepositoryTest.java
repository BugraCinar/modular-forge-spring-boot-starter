package dev.modularforge.repos;

import dev.modularforge.audit.AuthenticationErrorLogRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.identity.model.Role;
import dev.modularforge.identity.model.User;

import dev.modularforge.audit.AuthenticationErrorLog;
import dev.modularforge.audit.AuthenticationErrorLog.ErrorType;
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
@DisplayName("AuthenticationErrorLogRepository")
class AuthenticationErrorLogRepositoryTest {

    @Autowired
    private AuthenticationErrorLogRepository logRepository;

    private final LocalDateTime NOW = LocalDateTime.now();

    private AuthenticationErrorLog buildLog(ErrorType type, Long userId, String role,
                                            String ipAddress, String endpoint) {
        return AuthenticationErrorLog.builder()
                .errorType(type)
                .userId(userId)
                .role(role)
                .ipAddress(ipAddress)
                .endpoint(endpoint)
                .httpMethod("POST")
                .errorMessage("test error")
                .build();
    }

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        logRepository.saveAll(List.of(
            buildLog(ErrorType.UNAUTHORIZED_401, 1L, "user",  "1.1.1.1", "/api/v1/auth/login"),
            buildLog(ErrorType.UNAUTHORIZED_401, 1L, "user",  "1.1.1.1", "/api/v1/auth/login"),
            buildLog(ErrorType.FORBIDDEN_403,    2L, "user",  "2.2.2.2", "/api/v1/admin"),
            buildLog(ErrorType.INVALID_TOKEN,    null, null,   "3.3.3.3", "/api/v1/resource")
        ));
    }

    @Nested
    @DisplayName("findAllByOrderByCreatedAtDesc")
    class FindAllOrderedTests {

        @Test
        @DisplayName("returns all logs paginated")
        void returnsAllPaginated() {
            Page<AuthenticationErrorLog> page = logRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }

        @Test
        @DisplayName("page size is respected")
        void pageSizeRespected() {
            Page<AuthenticationErrorLog> page = logRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 2));
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findByErrorTypeOrderByCreatedAtDesc")
    class FilterByErrorTypeTests {

        @Test
        @DisplayName("returns only logs matching the given error type")
        void returnsMatchingType() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByErrorTypeOrderByCreatedAtDesc(ErrorType.UNAUTHORIZED_401, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).allMatch(l -> l.getErrorType() == ErrorType.UNAUTHORIZED_401);
        }

        @Test
        @DisplayName("returns empty when no logs match error type")
        void returnsEmptyForMissingType() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByErrorTypeOrderByCreatedAtDesc(ErrorType.INTERNAL_SERVER_ERROR_500, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByUserIdOrderByCreatedAtDesc")
    class FilterByUserIdTests {

        @Test
        @DisplayName("returns logs for a specific user id")
        void returnsForUser() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByUserIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns empty for unknown user id")
        void returnsEmptyForUnknown() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByUserIdOrderByCreatedAtDesc(99L, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByRoleOrderByCreatedAtDesc")
    class FilterByRoleTests {

        @Test
        @DisplayName("returns logs for given role")
        void returnsForRole() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByRoleOrderByCreatedAtDesc("user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("findByIpAddressOrderByCreatedAtDesc")
    class FilterByIpTests {

        @Test
        @DisplayName("returns logs from a specific IP")
        void returnsForIp() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByIpAddressOrderByCreatedAtDesc("1.1.1.1", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns empty for IP with no logs")
        void returnsEmptyForUnknownIp() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByIpAddressOrderByCreatedAtDesc("9.9.9.9", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByDateRange")
    class DateRangeTests {

        @Test
        @DisplayName("returns logs within date range")
        void returnsWithinRange() {
            LocalDateTime start = NOW.minusMinutes(1);
            LocalDateTime end   = NOW.plusMinutes(1);
            Page<AuthenticationErrorLog> page = logRepository
                    .findByDateRange(start, end, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }

        @Test
        @DisplayName("returns empty when date range excludes all logs")
        void returnsEmptyForPastRange() {
            LocalDateTime start = NOW.minusDays(10);
            LocalDateTime end   = NOW.minusDays(9);
            Page<AuthenticationErrorLog> page = logRepository
                    .findByDateRange(start, end, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByCreatedAtAfterOrderByCreatedAtDesc")
    class DateAfterTests {

        @Test
        @DisplayName("returns logs created after the given date")
        void returnsAfterDate() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByCreatedAtAfterOrderByCreatedAtDesc(NOW.minusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }

        @Test
        @DisplayName("returns empty when all logs are before the given date")
        void returnsEmptyWhenAllBefore() {
            Page<AuthenticationErrorLog> page = logRepository
                    .findByCreatedAtAfterOrderByCreatedAtDesc(NOW.plusMinutes(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("countByIpAddressSince")
    class CountByIpTests {

        @Test
        @DisplayName("counts logs for given IP since timestamp")
        void countsForIp() {
            Long count = logRepository.countByIpAddressSince("1.1.1.1", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns 0 for IP with no recent logs")
        void returnsZeroForUnknownIp() {
            Long count = logRepository.countByIpAddressSince("9.9.9.9", NOW.minusMinutes(1));
            assertThat(count).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns 0 when timestamp is in the future")
        void returnsZeroForFutureTimestamp() {
            Long count = logRepository.countByIpAddressSince("1.1.1.1", NOW.plusMinutes(1));
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("countByUserIdSince")
    class CountByUserIdTests {

        @Test
        @DisplayName("counts recent logs for given user id")
        void countsForUser() {
            Long count = logRepository.countByUserIdSince(1L, NOW.minusMinutes(1));
            assertThat(count).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("countByErrorType")
    class CountByErrorTypeTests {

        @Test
        @DisplayName("counts logs by error type")
        void counts() {
            long count = logRepository.countByErrorType(ErrorType.UNAUTHORIZED_401);
            assertThat(count).isEqualTo(2L);
        }

        @Test
        @DisplayName("returns 0 for type with no logs")
        void returnsZero() {
            long count = logRepository.countByErrorType(ErrorType.INTERNAL_SERVER_ERROR_500);
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getStatisticsByErrorType")
    class StatisticsTests {

        @Test
        @DisplayName("returns one row per distinct error type")
        void returnsDistinctTypes() {
            List<Object[]> stats = logRepository.getStatisticsByErrorType();
            assertThat(stats).hasSize(3);
        }

        @Test
        @DisplayName("counts are accurate per error type")
        void countsAreCorrect() {
            List<Object[]> stats = logRepository.getStatisticsByErrorType();
            stats.forEach(row -> {
                ErrorType type = (ErrorType) row[0];
                Long count = (Long) row[1];
                if (type == ErrorType.UNAUTHORIZED_401) {
                    assertThat(count).isEqualTo(2L);
                } else {
                    assertThat(count).isEqualTo(1L);
                }
            });
        }
    }

    @Nested
    @DisplayName("findRecentByIpAddress")
    class RecentByIpTests {

        @Test
        @DisplayName("returns logs for IP in descending order")
        void returnsForIp() {
            List<AuthenticationErrorLog> logs = logRepository
                    .findRecentByIpAddress("1.1.1.1", PageRequest.of(0, 10));
            assertThat(logs).hasSize(2);
            assertThat(logs).allMatch(l -> "1.1.1.1".equals(l.getIpAddress()));
        }

        @Test
        @DisplayName("limits results via pageable")
        void limitsResults() {
            List<AuthenticationErrorLog> logs = logRepository
                    .findRecentByIpAddress("1.1.1.1", PageRequest.of(0, 1));
            assertThat(logs).hasSize(1);
        }
    }
}
