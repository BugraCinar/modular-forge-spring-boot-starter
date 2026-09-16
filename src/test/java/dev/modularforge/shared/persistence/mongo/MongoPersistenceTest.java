package dev.modularforge.shared.persistence.mongo;

import dev.modularforge.identity.model.User;
import dev.modularforge.auth.token.VerificationToken;
import dev.modularforge.shared.persistence.EntityRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.*;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class MongoPersistenceTest {
    MongoPersistenceConfig config = new MongoPersistenceConfig();
    MongoMappingContext mapping() throws Exception {
        var context = config.mongoMappingContext(MongoManagedTypes.from(User.class, VerificationToken.class, PlainVersion.class, UserSidecar.class),
                MongoCustomConversions.create(adapter -> {}));
        context.afterPropertiesSet(); return context;
    }
    @org.springframework.data.mongodb.core.mapping.Document("user_sidecar_test")
    static class UserSidecar { Long id; Long userId; }
    static class PlainVersion {
        @org.springframework.data.annotation.Version Long version;
        String value;
    }
    @Test void jpaAndNativeMongoVersionsAreRecognized() throws Exception {
        var context = mapping();
        assertThat(context.getRequiredPersistentEntity(User.class).getRequiredVersionProperty().getName()).isEqualTo("rowVersion");
        assertThat(context.getRequiredPersistentEntity(PlainVersion.class).getRequiredVersionProperty().getName()).isEqualTo("version");
        assertThat(config.transactionManager(mock(MongoDatabaseFactory.class))).isNotNull();
    }
    @Test void lifecycleAllocatesNumericIdsAndPreservesCreationTimestamps() {
        var client = mock(com.mongodb.client.MongoClient.class);
        var factory = mock(MongoDatabaseFactory.class);
        var database = mock(com.mongodb.client.MongoDatabase.class);
        com.mongodb.client.MongoCollection<Document> counters = mock(com.mongodb.client.MongoCollection.class);
        when(factory.getMongoDatabase()).thenReturn(database); when(database.getName()).thenReturn("test");
        when(client.getDatabase("test")).thenReturn(database); when(database.getCollection("repository_sequences")).thenReturn(counters);
        when(counters.withWriteConcern(com.mongodb.WriteConcern.MAJORITY)).thenReturn(counters);
        when(counters.findOneAndUpdate(any(org.bson.conversions.Bson.class), any(org.bson.conversions.Bson.class),
                any(com.mongodb.client.model.FindOneAndUpdateOptions.class))).thenReturn(new Document("sequence", 42L));
        var lifecycle = config.mongoEntityLifecycle(client, factory);
        User user = new User(); lifecycle.onBeforeConvert(user, "users");
        assertThat(user.getId()).isEqualTo(42L); assertThat(user.getCreatedAt()).isNotNull(); assertThat(user.getUpdatedAt()).isNotNull();
        LocalDateTime created = user.getCreatedAt(); lifecycle.onBeforeConvert(user, "users");
        assertThat(user.getCreatedAt()).isEqualTo(created);
        var token = new VerificationToken(42L, "user"); token.setCreatedDate(null);
        lifecycle.onBeforeConvert(token, "verification_tokens");
        assertThat(token.getCreatedDate()).isNotNull(); lifecycle.onBeforeConvert(token, "verification_tokens");
        Document raw = new Document("value", 1); assertThat(lifecycle.onBeforeConvert(raw, "raw")).isSameAs(raw);
        PlainVersion plain = new PlainVersion(); assertThat(lifecycle.onBeforeConvert(plain, "plain")).isSameAs(plain);
    }
    @Test void schemaRejectsStandaloneAndSupportsReplicaSetsAndRouters() throws Exception {
        MongoTemplate template = mock(MongoTemplate.class);
        var initializer = new MongoSchemaInitializer(template, mapping());
        when(template.executeCommand(any(Document.class))).thenReturn(new Document("ok", 1));
        assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("replica set");
        when(template.executeCommand(any(Document.class))).thenReturn(new Document("setName", "rs0"));
        when(template.indexOps(any(Class.class))).thenReturn(mock(IndexOperations.class));
        when(template.collectionExists(anyString())).thenReturn(false);
        initializer.run(null);
        verify(template).createCollection("users");
        when(template.executeCommand(any(Document.class))).thenReturn(new Document("msg", "isdbgrid"));
        when(template.collectionExists(anyString())).thenReturn(true);
        when(template.findOne(any(Query.class), eq(Document.class), anyString())).thenReturn(new Document("_id", 99L));
        initializer.run(null);
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(template, atLeastOnce()).upsert(any(Query.class), update.capture(), eq("repository_sequences"));
        assertThat(update.getAllValues()).anySatisfy(value -> assertThat(value.getUpdateObject().get("$max", Document.class).getLong("sequence")).isEqualTo(99L));
        when(template.findOne(any(Query.class), eq(Document.class), anyString())).thenReturn(new Document("_id", "legacy-id"));
        initializer.run(null);
    }
    @Test void filtersKeepUserValuesAsLiteralBsonAndOmitEmptyConditions() {
        assertThat(MongoFilters.users(null, null, null, null)).isEmpty();
        assertThat(MongoFilters.users(" ", null, null, null)).isEmpty();
        Document query = MongoFilters.users(".*", true, false, "APP_USER");
        var alternatives = (List<Document>) query.get("$or");
        var pattern = (java.util.regex.Pattern) alternatives.getFirst().get("username");
        assertThat(pattern.matcher("victim").find()).isFalse(); assertThat(pattern.matcher("literal.*name").find()).isTrue();
        assertThat(MongoFilters.activity(null, null, null, null, null, null, null, null)).isEmpty();
        assertThat(MongoFilters.activity(1L, "user", "LOGIN", "session", true, 1, 2, "ip")).containsKey("createdAt");
        EntityRepository<User> repository = mock(EntityRepository.class, CALLS_REAL_METHODS);
        User user = new User(); doReturn(user).when(repository).save(user);
        assertThat(repository.saveAndFlush(user)).isSameAs(user); repository.flush();
    }
    @Test void aggregationAdaptersPreservePublicStatisticsTypes() {
        var refresh = mock(dev.modularforge.auth.token.persistence.mongo.MongoRefreshTokenRepository.class, CALLS_REAL_METHODS);
        var now = LocalDateTime.now(); doReturn(List.of(new CountBucket("user", 3))).when(refresh).countActiveTokensByRoleBuckets(now);
        assertThat(refresh.countActiveTokensByRole(now).getFirst()).containsExactly("user", 3L);
        var errors = mock(dev.modularforge.audit.persistence.mongo.MongoAuthenticationErrorLogRepository.class, CALLS_REAL_METHODS);
        doReturn(List.of(new CountBucket("ACCESS_DENIED", 2))).when(errors).getStatisticsByErrorTypeBuckets();
        doReturn(List.of(new CountBucket("2026-09-15", 2))).when(errors).getDailyStatisticsBuckets(now);
        assertThat(errors.getStatisticsByErrorType().getFirst()).containsExactly(dev.modularforge.audit.AuthenticationErrorLog.ErrorType.ACCESS_DENIED, 2L);
        assertThat(errors.getDailyStatistics(now).getFirst()).containsExactly(java.time.LocalDate.of(2026, 9, 15), 2L);
        var sensitive = mock(dev.modularforge.audit.persistence.mongo.MongoSensitiveEndpointAccessLogRepository.class, CALLS_REAL_METHODS);
        doReturn(List.of(new CountBucket("CRITICAL", 1))).when(sensitive).getStatisticsBySeverityBuckets();
        doReturn(List.of(new CountBucket("auth", 1))).when(sensitive).getStatisticsByCategoryBuckets();
        doReturn(List.of(new CountBucket("2026-09-15", 1))).when(sensitive).getDailyStatisticsBuckets(now);
        assertThat(sensitive.getStatisticsBySeverity().getFirst()).containsExactly(dev.modularforge.audit.SensitiveEndpointAccessLog.SeverityLevel.CRITICAL, 1L);
        assertThat(sensitive.getStatisticsByCategory().getFirst()).containsExactly("auth", 1L);
        assertThat(sensitive.getDailyStatistics(now).getFirst()).containsExactly(java.time.LocalDate.of(2026, 9, 15), 1L);
    }
}
