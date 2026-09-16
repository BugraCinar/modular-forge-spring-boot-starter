package dev.modularforge.kafka;

import dev.modularforge.shared.events.AccountEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KafkaEventsTest {
    @Test void producerConfigurationIsOptionalAndRequiresServers() {
        var runner = new ApplicationContextRunner().withUserConfiguration(KafkaEventConfiguration.class);
        runner.run(c -> assertThat(c).doesNotHaveBean(KafkaTemplate.class));
        runner.withPropertyValues("app.modules.kafka.enabled=true").run(c -> assertThat(c).hasFailed());
        runner.withPropertyValues("app.modules.kafka.enabled=true", "app.modules.kafka.bootstrap-servers=localhost:9092")
                .run(c -> assertThat(c).hasSingleBean(KafkaTemplate.class));
        var config = new KafkaEventConfiguration();
        var factory = config.applicationEventProducerFactory("localhost:9092", "SASL_SSL", "PLAIN", "test-jaas");
        try {
            assertThat(factory.getConfigurationProperties()).containsEntry("sasl.jaas.config", "test-jaas")
                    .containsEntry("enable.idempotence", true).containsEntry("acks", "all");
        } finally { factory.destroy(); }
    }

    @Test void serializesOnlyThePublicContractAndCountsDeliveryOutcomes() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        var registry = new SimpleMeterRegistry();
        var mapper = JsonMapper.builder().build();
        var listener = new KafkaAccountEventListener(template, mapper, registry, "events.v1");
        var event = AccountEvent.emailChanged("user", 42L);
        when(template.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        listener.onAccountEvent(event);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("events.v1"), eq("user:42"), payload.capture());
        var json = mapper.readTree(payload.getValue());
        assertThat(json.size()).isEqualTo(6);
        assertThat(json.get("type").asText()).isEqualTo("account.email-changed");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(json.get("occurredAt").asText()).isNotBlank();
        assertThat(registry.counter("app.kafka.events", "outcome", "sent").count()).isEqualTo(1);
        when(template.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("private details")));
        listener.onAccountEvent(event);
        when(template.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertThatCode(() -> listener.onAccountEvent(event)).doesNotThrowAnyException();
        assertThat(registry.counter("app.kafka.events", "outcome", "failed").count()).isEqualTo(2);
        assertThatThrownBy(() -> new KafkaAccountEventListener(template, mapper, registry, "../bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void onlyCommittedTransactionsPublish() {
        new ApplicationContextRunner().withUserConfiguration(TransactionTestConfig.class, KafkaAccountEventListener.class)
                .withPropertyValues("app.modules.kafka.enabled=true").run(c -> {
                    KafkaTemplate<String, String> template = c.getBean(KafkaTemplate.class);
                    when(template.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
                    clearInvocations(template); // Ignore Spring lifecycle callbacks, retain send stubbing.
                    var tx = new TransactionTemplate(new TestTransactionManager());
                    var event = AccountEvent.emailChanged("admin", 7L);
                    c.publishEvent(event); // no transaction: deliberately ignored
                    verifyNoInteractions(template);
                    tx.executeWithoutResult(status -> { c.publishEvent(event); status.setRollbackOnly(); });
                    verifyNoInteractions(template);
                    tx.executeWithoutResult(status -> { c.publishEvent(event); verifyNoInteractions(template); });
                    verify(template).send(eq("modular-forge.account-events.v1"), eq("admin:7"), anyString());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionTestConfig {
        @Bean KafkaTemplate<String, String> template() { return mock(KafkaTemplate.class); }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
        @Bean SimpleMeterRegistry registry() { return new SimpleMeterRegistry(); }
    }
    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { }
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
