package dev.modulithforge.repos;

import dev.modulithforge.auth.token.VerificationTokenRepository;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.identity.model.Role;
import dev.modulithforge.identity.model.User;

import dev.modulithforge.auth.token.VerificationToken;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("VerificationTokenRepository")
class VerificationTokenRepositoryTest {

    @Autowired
    private VerificationTokenRepository tokenRepository;

    private final LocalDateTime NOW = LocalDateTime.now();
    private VerificationToken buildToken(Long userId, String role, LocalDateTime expiryDate) {
        VerificationToken t = new VerificationToken(userId, role);
        t.setExpiryDate(expiryDate);
        return t;
    }

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        tokenRepository.saveAll(List.of(
            buildToken(1L, "user", NOW.plusHours(24)),   // valid
            buildToken(2L, "user",  NOW.plusHours(24)),   // valid
            buildToken(3L, "user", NOW.minusHours(1)),   // expired
            buildToken(4L, "admin",  NOW.minusHours(2))    // expired
        ));
    }

    @Nested
    @DisplayName("findByToken")
    class FindByTokenTests {

        @Test
        @DisplayName("returns token by its string value")
        void found() {
            String tokenValue = tokenRepository.findAll().get(0).getToken();
            Optional<VerificationToken> result = tokenRepository.findByToken(tokenValue);
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns empty for unknown token string")
        void notFound() {
            assertThat(tokenRepository.findByToken("not-a-real-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndRole")
    class FindByUserTests {

        @Test
        @DisplayName("returns token for known user")
        void found() {
            Optional<VerificationToken> result = tokenRepository.findByUserIdAndRole(1L, "user");
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("returns empty for unknown user")
        void notFound() {
            Optional<VerificationToken> result = tokenRepository.findByUserIdAndRole(99L, "user");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFirstByRoleOrderByCreatedDateDesc")
    class FindFirstByRoleTests {

        @Test
        @DisplayName("returns the most recent token for user type")
        void returnsLatest() {
            VerificationToken newer = buildToken(5L, "user", NOW.plusHours(24));
            tokenRepository.save(newer);

            Optional<VerificationToken> result = tokenRepository.findFirstByRoleOrderByCreatedDateDesc("user");
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns empty for user type with no tokens")
        void returnsEmpty() {
            Optional<VerificationToken> result = tokenRepository.findFirstByRoleOrderByCreatedDateDesc("unknown_type");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByUserIdAndRole")
    class DeleteTests {

        @Test
        @DisplayName("deletes token for given user")
        void deletesUserToken() {
            long countBefore = tokenRepository.count();
            tokenRepository.deleteByUserIdAndRole(1L, "user");
            assertThat(tokenRepository.count()).isEqualTo(countBefore - 1);
            assertThat(tokenRepository.findByUserIdAndRole(1L, "user")).isEmpty();
        }

        @Test
        @DisplayName("does not delete tokens of other users")
        void doesNotDeleteOthers() {
            tokenRepository.deleteByUserIdAndRole(1L, "user");
            assertThat(tokenRepository.findByUserIdAndRole(2L, "user")).isPresent();
        }
    }

    @Nested
    @DisplayName("findAllByOrderByCreatedDateDesc (paginated)")
    class FindAllTests {

        @Test
        @DisplayName("returns all tokens paginated")
        void returnsAll() {
            Page<VerificationToken> page = tokenRepository
                    .findAllByOrderByCreatedDateDesc(PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(4L);
        }

        @Test
        @DisplayName("page size is respected")
        void pageSizeRespected() {
            Page<VerificationToken> page = tokenRepository
                    .findAllByOrderByCreatedDateDesc(PageRequest.of(0, 2));
            assertThat(page.getContent()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findByRoleOrderByCreatedDateDesc (paginated)")
    class FindByRoleTests {

        @Test
        @DisplayName("returns tokens for given role")
        void returnsForRole() {
            Page<VerificationToken> page = tokenRepository
                    .findByRoleOrderByCreatedDateDesc("user", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(3L);
        }

        @Test
        @DisplayName("returns empty for unknown user type")
        void returnsEmpty() {
            Page<VerificationToken> page = tokenRepository
                    .findByRoleOrderByCreatedDateDesc("unknown", PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("findByExpiryDateBefore (expired tokens)")
    class FindExpiredTests {

        @Test
        @DisplayName("returns tokens that have expired")
        void returnsExpired() {
            Page<VerificationToken> page = tokenRepository
                    .findByExpiryDateBeforeOrderByCreatedDateDesc(NOW, PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(2L);
            assertThat(page.getContent()).allMatch(t -> t.getExpiryDate().isBefore(NOW));
        }

        @Test
        @DisplayName("returns empty when no tokens are expired")
        void returnsEmpty() {
            Page<VerificationToken> page = tokenRepository
                    .findByExpiryDateBeforeOrderByCreatedDateDesc(NOW.minusDays(1), PageRequest.of(0, 10));
            assertThat(page.getTotalElements()).isEqualTo(0L);
        }
    }
}
