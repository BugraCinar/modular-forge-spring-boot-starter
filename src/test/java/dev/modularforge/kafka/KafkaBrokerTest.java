package dev.modularforge.kafka;

import dev.modularforge.shared.events.AccountEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaBrokerTest {
    @Test void eventReachesARealKraftBrokerWithItsAccountKey() {
        String topic = "account-events-test";
        var broker = new EmbeddedKafkaKraftBroker(1, 1, topic);
        broker.afterPropertiesSet();
        var config = new KafkaEventConfiguration();
        var factory = config.applicationEventProducerFactory(broker.getBrokersAsString(), "PLAINTEXT", "PLAIN", "");
        var template = config.applicationEventKafkaTemplate(factory);
        var mapper = JsonMapper.builder().build();
        var registry = new SimpleMeterRegistry();
        try (var consumer = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps("account-events-test", "false", broker),
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAllEmbeddedTopics(consumer);
            var event = AccountEvent.emailChanged("user", 19L);
            new KafkaAccountEventListener(template, mapper, registry, topic).onAccountEvent(event);
            template.flush();
            var record = KafkaTestUtils.getSingleRecord(consumer, topic, Duration.ofSeconds(20));
            assertThat(record.key()).isEqualTo("user:19");
            assertThat(mapper.readTree(record.value()).get("eventId").asText()).isEqualTo(event.eventId().toString());
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(registry.counter("app.kafka.events", "outcome", "sent").count()).isEqualTo(1));
        } finally {
            template.destroy();
            factory.destroy();
            broker.destroy();
            registry.close();
        }
    }
}
