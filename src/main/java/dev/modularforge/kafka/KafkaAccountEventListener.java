package dev.modularforge.kafka;

import dev.modularforge.shared.events.AccountEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "app.modules.kafka.enabled", havingValue = "true")
public class KafkaAccountEventListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaAccountEventListener.class);
    private final KafkaTemplate<String, String> template;
    private final JsonMapper mapper;
    private final String topic;
    private final Counter sent;
    private final Counter failed;

    public KafkaAccountEventListener(KafkaTemplate<String, String> template, JsonMapper mapper,
            MeterRegistry registry, @Value("${app.modules.kafka.topic:modular-forge.account-events.v1}") String topic) {
        Assert.isTrue(topic.matches("[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,248}"), "Invalid Kafka event topic");
        this.template = template;
        this.mapper = mapper;
        this.topic = topic;
        sent = registry.counter("app.kafka.events", "outcome", "sent");
        failed = registry.counter("app.kafka.events", "outcome", "failed");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountEvent(AccountEvent event) {
        try {
            template.send(topic, event.accountRole() + ":" + event.accountId(), mapper.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error == null) sent.increment();
                        else recordFailure();
                    });
        } catch (RuntimeException failure) {
            recordFailure();
        }
    }

    private void recordFailure() {
        failed.increment();
        log.warn("Kafka application event delivery failed; inspect broker health and app.kafka.events metrics");
    }
}
