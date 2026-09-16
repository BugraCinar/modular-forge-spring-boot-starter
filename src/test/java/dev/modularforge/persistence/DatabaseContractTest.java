package dev.modularforge.persistence;

import dev.modularforge.identity.*;
import dev.modularforge.identity.model.*;
import dev.modularforge.auth.token.*;
import dev.modularforge.audit.*;
import dev.modularforge.twofactor.*;
import dev.modularforge.notification.EmailService;
import dev.modularforge.ratelimit.RateLimitStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Run with -Dcontract.provider=mysql or mongodb to use an isolated real database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.modules.two-factor.enabled=true",
                "app.modules.two-factor.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@ActiveProfiles(resolver = DatabaseContractTest.Profiles.class)
class DatabaseContractTest {
    static final String PROVIDER = System.getProperty("contract.provider", "h2");
    static final String DATABASE = "mf_contract_" + UUID.randomUUID().toString().replace("-", "");
    public static class Profiles implements ActiveProfilesResolver {
        public String[] resolve(Class<?> type) {
            return PROVIDER.equals("mongodb") ? new String[]{"integration", "mongodb"} : new String[]{"integration"};
        }
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        if (PROVIDER.equals("mongodb")) {
            String address = System.getProperty("contract.mongo.address", "127.0.0.1:27028");
            try (var client = com.mongodb.client.MongoClients.create("mongodb://" + address + "/?directConnection=true")) {
                try { client.getDatabase("admin").runCommand(new org.bson.Document("replSetGetStatus", 1)); }
                catch (com.mongodb.MongoCommandException e) {
                    if (e.getErrorCode() != 94) throw e;
                    client.getDatabase("admin").runCommand(new org.bson.Document("replSetInitiate",
                            new org.bson.Document("_id", "rs0").append("members", java.util.List.of(
                                    new org.bson.Document("_id", 0).append("host", address)))));
                }
            }
            properties.add("spring.mongodb.uri", () -> "mongodb://" + address + "/" + DATABASE + "?replicaSet=rs0&serverSelectionTimeoutMS=15000");
            properties.add("management.endpoint.health.group.readiness.include", () -> "readinessState,mongo");
        } else {
            String url = "jdbc:h2:mem:" + DATABASE + ";DB_CLOSE_DELAY=-1";
            String driver = "org.h2.Driver";
            if (PROVIDER.equals("mysql")) {
                String base = System.getProperty("contract.mysql.url", "jdbc:mysql://127.0.0.1:13306/");
                try (var connection = java.sql.DriverManager.getConnection(base, "root", "");
                     var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE " + DATABASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
                }
                url = base + DATABASE; driver = "com.mysql.cj.jdbc.Driver";
            }
            final String selectedUrl = url; final String selectedDriver = driver;
            properties.add("spring.datasource.url", () -> selectedUrl);
            properties.add("spring.datasource.driver-class-name", () -> selectedDriver);
            properties.add("spring.datasource.username", () -> PROVIDER.equals("mysql") ? "root" : "sa");
            properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            properties.add("spring.flyway.enabled", () -> true);
        }
    }
    @MockitoBean EmailService notifications;
    @MockitoBean RateLimitStore rateLimits;
    @Autowired UserRepository users;
    @Autowired AdminRepository admins;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired VerificationTokenRepository verifications;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired UserActivityLogRepository activities;
    @Autowired AuthenticationErrorLogRepository errors;
    @Autowired TwoFactorCredentialRepository factors;
    @Autowired PlatformTransactionManager transactions;
    @Autowired dev.modularforge.auth.EmailChangeService emailChanges;
    @Autowired dev.modularforge.auth.PasswordService passwords;
    @Autowired TokenHashService hashes;
    @Autowired RefreshTokenService sessions;
    @Autowired TwoFactorAuthService secondFactor;
    @Autowired SensitiveEndpointAccessLogRepository sensitive;
    @Autowired AdminActivityLogRepository adminActivities;

    User user(String prefix) {
        User user = new User();
        String name = prefix + UUID.randomUUID().toString().substring(0, 8);
        user.setUsername(name); user.setEmail(name + "@example.com");
        user.setSalt("salt"); user.setPasswordHash("hash"); user.setEmailVerified(true);
        return users.saveAndFlush(user);
    }
    @Test void accountQueriesUniqueIndexesAndOptimisticLocking() {
        User saved = user("query");
        assertThat(saved.getId()).isPositive(); assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(users.findByUsernameOrEmail(saved.getUsername(), saved.getUsername())).isPresent();
        assertThat(users.findWithFilters(saved.getUsername().toUpperCase(), true, true, UserType.APP_USER, PageRequest.of(0, 10))).hasSize(1);
        User first = users.findById(saved.getId()).orElseThrow();
        User stale = users.findById(saved.getId()).orElseThrow();
        first.setBio("first writer"); users.saveAndFlush(first);
        stale.setBio("lost update");
        assertThatThrownBy(() -> users.saveAndFlush(stale)).isInstanceOf(OptimisticLockingFailureException.class);
        User duplicate = new User(); duplicate.setUsername(saved.getUsername()); duplicate.setEmail("unique-" + saved.getEmail());
        duplicate.setSalt("salt"); duplicate.setPasswordHash("hash");
        assertThatThrownBy(() -> users.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void transactionsRollbackAccountAndTokenTogether() {
        User original = user("rollback");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User user = users.findById(original.getId()).orElseThrow();
            user.setBio("must roll back"); users.saveAndFlush(user);
            var token = new VerificationToken(user.getId(), "user");
            token.storeTokenMetadata("raw", "rollback-" + user.getId(), "masked"); verifications.save(token);
            throw new IllegalStateException("abort test transaction");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.findById(original.getId()).orElseThrow().getBio()).isNull();
        assertThat(verifications.findByTokenHash("rollback-" + original.getId())).isEmpty();
    }
    @Test void refreshConsumptionRetentionFiltersAndStatistics() {
        User owner = user("refresh");
        RefreshToken token = new RefreshToken(owner.getId(), "user", 30L);
        token.setIssuedAuthVersion(0L); token.storeTokenMetadata("raw", "refresh-" + owner.getId(), "masked");
        token = refreshTokens.saveAndFlush(token);
        Long id = token.getId(); var now = LocalDateTime.now();
        assertThat(refreshTokens.countAllActiveTokens(now)).isPositive();
        assertThat(refreshTokens.countActiveTokensByRole(now)).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo("user"); assertThat((Long) row[1]).isPositive();
        });
        assertThat(refreshTokens.findWithFilters("user", owner.getId(), false, null)).hasSize(1);
        var tx = new TransactionTemplate(transactions);
        assertThat(tx.<Integer>execute(status -> refreshTokens.revokeIfActive(id, now))).isEqualTo(1);
        assertThat(tx.<Integer>execute(status -> refreshTokens.revokeIfActive(id, now))).isZero();
        tx.executeWithoutResult(status -> refreshTokens.cleanupRevokedAndExpired(now));
        assertThat(refreshTokens.findById(id)).isPresent();
        tx.executeWithoutResult(status -> refreshTokens.cleanupRevokedAndExpired(now.plusDays(31)));
        assertThat(refreshTokens.findById(id)).isEmpty();
    }
    @Test void auditQueriesAndAggregationMatchAcrossProviders() {
        User owner = user("audit"); var now = LocalDateTime.now();
        UserActivityLog activity = new UserActivityLog();
        activity.setUserId(owner.getId()); activity.setRole("user"); activity.setAction("LOGIN");
        activity.setResourceType("SESSION"); activity.setSuccess(true); activity.setIpAddress("127.0.0.1");
        activities.saveAndFlush(activity);
        assertThat(activities.findWithFilters(owner.getId(), "user", "LOGIN", "SESSION", true,
                now.minusMinutes(1), now.plusMinutes(1), "127.0.0.1", PageRequest.of(0, 10))).hasSize(1);
        AuthenticationErrorLog error = new AuthenticationErrorLog();
        error.setUserId(owner.getId()); error.setErrorType(AuthenticationErrorLog.ErrorType.ACCESS_DENIED);
        error.setEndpoint("/contract-test"); error.setIpAddress("127.0.0.1"); errors.saveAndFlush(error);
        assertThat(errors.getStatisticsByErrorType()).isNotEmpty();
        assertThat(errors.getDailyStatistics(now.minusDays(1))).isNotEmpty();
        assertThat(errors.findByDateRange(now.minusMinutes(1), now.plusMinutes(1), PageRequest.of(0, 10))).isNotEmpty();
    }

    @Test void verifiedEmailChangeCommitsAddressAndRevokesSessionsTogether() {
        User owner = user("email");
        owner.setSalt(passwords.generateSalt()); owner.setPasswordHash(passwords.hashPassword("Password1!", owner.getSalt()));
        owner = users.saveAndFlush(owner); Long id = owner.getId();
        var refresh = sessions.createRefreshToken(id, "user", new org.springframework.mock.web.MockHttpServletRequest());
        String newEmail = "changed-" + owner.getEmail();
        emailChanges.request(id, "user", "Password1!", newEmail);
        var raw = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(notifications).sendEmailChangeVerificationEmail(org.mockito.ArgumentMatchers.eq(newEmail), raw.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo(owner.getEmail());
        VerificationToken stored = verifications.findByTokenHash(hashes.hashToken(raw.getValue())).orElseThrow();
        assertThat(stored.getPlaintextToken()).isNull();
        emailChanges.confirm(raw.getValue());
        User updated = users.findById(id).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo(newEmail); assertThat(updated.currentAuthVersion()).isEqualTo(1L);
        assertThat(refreshTokens.findById(refresh.getId()).orElseThrow().getIsRevoked()).isTrue();
        assertThatThrownBy(() -> emailChanges.confirm(raw.getValue())).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
    }

    @Test void optionalTotpAndAuditPersistThroughTheSelectedProvider() {
        Admin admin = new Admin(); String name = "admin" + UUID.randomUUID().toString().substring(0, 8);
        admin.setUsername(name); admin.setEmail(name + "@example.com"); admin.setSalt("salt"); admin.setPasswordHash("hash");
        admin = admins.saveAndFlush(admin); Long id = admin.getId();
        var setup = secondFactor.generateSecret(id);
        var authenticator = new com.warrenstrange.googleauth.GoogleAuthenticator();
        String code = "%06d".formatted(authenticator.getTotpPassword(setup.getSecret()));
        assertThat(secondFactor.verifyAndEnable(id, code)).isTrue();
        TwoFactorCredential credential = factors.findByAdminId(id).orElseThrow();
        assertThat(credential.getEncryptedSecret()).doesNotContain(setup.getSecret());
        // Simulate the later time step after enrollment before exercising login consumption.
        credential.setLastCodeHash(null); credential.setLastCodeAcceptedAt(null); factors.saveAndFlush(credential);
        var challenge = secondFactor.beginChallenge(id).orElseThrow();
        assertThat(secondFactor.completeLogin(name, code, challenge.token())).isPresent();
        assertThat(secondFactor.completeLogin(name, code, challenge.token())).isEmpty();
        assertThatThrownBy(() -> secondFactor.generateSecret(id)).isInstanceOf(dev.modularforge.shared.error.BadRequestException.class);
        var log = new AdminActivityLog(); log.setAdminId(id); log.setAction("UPDATE"); log.setResourceType("PROFILE");
        adminActivities.saveAndFlush(log);
        assertThat(adminActivities.findByAdminIdOrderByCreatedAtDesc(id)).hasSize(1);
        var access = new SensitiveEndpointAccessLog(); access.setEndpoint("/contract"); access.setEndpointCategory("test");
        access.setSeverity(SensitiveEndpointAccessLog.SeverityLevel.CRITICAL); sensitive.saveAndFlush(access);
        assertThat(sensitive.getStatisticsBySeverity()).isNotEmpty(); assertThat(sensitive.getStatisticsByCategory()).isNotEmpty();
        assertThat(sensitive.getDailyStatistics(LocalDateTime.now().minusDays(1))).isNotEmpty();
        assertThat(sensitive.findRecentCriticalLogs(LocalDateTime.now().minusMinutes(1))).isNotEmpty();
    }

    @Test void concurrentAccountTransactionsAllocateDistinctIds() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Long> insert = () -> new TransactionTemplate(transactions).execute(status -> {
                start.countDown();
                try {
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return user("parallel").getId();
            });
            var first = executor.submit(insert); var second = executor.submit(insert);
            Long firstId = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            Long secondId = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(firstId).isPositive().isNotEqualTo(secondId);
            assertThat(users.findById(firstId)).isPresent(); assertThat(users.findById(secondId)).isPresent();
        }
    }
}
