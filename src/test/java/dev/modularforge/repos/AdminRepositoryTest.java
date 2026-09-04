package dev.modularforge.repos;

import dev.modularforge.identity.AdminRepository;

import dev.modularforge.identity.model.Admin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AdminRepository")
class AdminRepositoryTest {

    @Autowired
    private AdminRepository adminRepository;

    private Admin buildAdmin(String username, String email, Integer level, boolean active) {
        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash("hash");
        admin.setSalt("salt");
        admin.setLevel(level);
        admin.setIsActive(active);
        admin.setLoginAttempts(0);
        return admin;
    }

    @BeforeEach
    void setUp() {
        adminRepository.deleteAll();
        adminRepository.saveAll(List.of(
            buildAdmin("superadmin",  "super@test.com",  0, true),
            buildAdmin("admin1",      "admin1@test.com", 1, true),
            buildAdmin("admin2",      "admin2@test.com", 2, true),
            buildAdmin("inactive",    "inactive@test.com", 1, false)
        ));
    }

    @Nested
    @DisplayName("findByUsername / findByEmail")
    class LookupTests {

        @Test
        @DisplayName("finds admin by exact username")
        void findByUsername_found() {
            Optional<Admin> result = adminRepository.findByUsername("admin1");
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("admin1@test.com");
        }

        @Test
        @DisplayName("returns empty when username does not exist")
        void findByUsername_notFound() {
            assertThat(adminRepository.findByUsername("ghost")).isEmpty();
        }

        @Test
        @DisplayName("finds admin by exact email")
        void findByEmail_found() {
            Optional<Admin> result = adminRepository.findByEmail("admin2@test.com");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("findByUsernameOrEmail returns match on username")
        void findByUsernameOrEmail_matchUsername() {
            Optional<Admin> result = adminRepository.findByUsernameOrEmail("superadmin", "noemail@x.com");
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("superadmin");
        }

        @Test
        @DisplayName("findByUsernameOrEmail returns match on email")
        void findByUsernameOrEmail_matchEmail() {
            Optional<Admin> result = adminRepository.findByUsernameOrEmail("nousername", "admin1@test.com");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("existsByUsername returns true for existing username")
        void existsByUsername_true() {
            assertThat(adminRepository.existsByUsername("admin1")).isTrue();
        }

        @Test
        @DisplayName("existsByUsername returns false for non-existing username")
        void existsByUsername_false() {
            assertThat(adminRepository.existsByUsername("ghost")).isFalse();
        }

        @Test
        @DisplayName("existsByEmail returns true for existing email")
        void existsByEmail_true() {
            assertThat(adminRepository.existsByEmail("super@test.com")).isTrue();
        }
    }

    @Nested
    @DisplayName("findAllActiveAdmins")
    class ActiveAdminTests {

        @Test
        @DisplayName("returns only active admins")
        void returnsOnlyActiveAdmins() {
            List<Admin> active = adminRepository.findAllActiveAdmins();
            assertThat(active).hasSize(3);
            assertThat(active).allMatch(Admin::getIsActive);
        }

        @Test
        @DisplayName("returns empty list when no active admins exist")
        void returnsEmptyWhenNoneActive() {
            adminRepository.findAll().forEach(a -> { a.setIsActive(false); adminRepository.save(a); });
            assertThat(adminRepository.findAllActiveAdmins()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByLevelLessThanEqualAndActiveTrue")
    class LevelFilterTests {

        @Test
        @DisplayName("returns active admins with level <= 1")
        void returnsLevelLessThanEqualOne() {
            List<Admin> result = adminRepository.findByLevelLessThanEqualAndActiveTrue(1);
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(a -> a.getLevel() <= 1);
        }

        @Test
        @DisplayName("excludes inactive admins even if level matches")
        void excludesInactiveAdmins() {
            List<Admin> result = adminRepository.findByLevelLessThanEqualAndActiveTrue(1);
            assertThat(result).noneMatch(a -> a.getUsername().equals("inactive"));
        }

        @Test
        @DisplayName("returns all active admins for level=2")
        void returnsAllForMaxLevel() {
            List<Admin> result = adminRepository.findByLevelLessThanEqualAndActiveTrue(2);
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("findByLevelAndActiveTrue")
    class ExactLevelTests {

        @Test
        @DisplayName("returns active admins at exact level")
        void exactLevelMatch() {
            List<Admin> result = adminRepository.findByLevelAndActiveTrue(2);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUsername()).isEqualTo("admin2");
        }

        @Test
        @DisplayName("returns empty list for level with no active admins")
        void noActiveAdminsAtLevel() {
            List<Admin> result = adminRepository.findByLevelAndActiveTrue(99);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("countSuperAdmins")
    class CountSuperAdminTests {

        @Test
        @DisplayName("counts only level-0 admins")
        void countsLevelZero() {
            long count = adminRepository.countSuperAdmins();
            assertThat(count).isEqualTo(1L);
        }

        @Test
        @DisplayName("returns zero when no super admins exist")
        void returnsZeroWhenNone() {
            adminRepository.findAll().stream()
                    .filter(a -> a.getLevel() == 0)
                    .forEach(adminRepository::delete);
            assertThat(adminRepository.countSuperAdmins()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByLevelGreaterThanEqual (paginated)")
    class PaginatedLevelTests {

        @Test
        @DisplayName("returns paginated admins at level >= 1")
        void paginatedResultsLevelAtLeastOne() {
            Page<Admin> page = adminRepository.findByLevelGreaterThanEqual(1, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("page size is respected")
        void pageSizeRespected() {
            Page<Admin> page = adminRepository.findByLevelGreaterThanEqual(0, PageRequest.of(0, 2));
            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }
    }
}
