package dev.modularforge.kafka;

import java.util.HashMap;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.modules.kafka.enabled", havingValue = "true")
public class KafkaEventConfiguration {
    @Bean
    DefaultKafkaProducerFactory<String, String> applicationEventProducerFactory(
            @Value("${app.modules.kafka.bootstrap-servers:}") String servers,
            @Value("${app.modules.kafka.security-protocol:SSL}") String protocol,
            @Value("${app.modules.kafka.sasl-mechanism:PLAIN}") String mechanism,
            @Value("${app.modules.kafka.sasl-jaas-config:}") String jaas) {
        Assert.hasText(servers, "Kafka bootstrap servers are required when the module is enabled");
        var properties = new HashMap<String, Object>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        properties.put("security.protocol", protocol);
        properties.put("sasl.mechanism", mechanism);
        if (!jaas.isBlank()) properties.put("sasl.jaas.config", jaas);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    KafkaTemplate<String, String> applicationEventKafkaTemplate(
            DefaultKafkaProducerFactory<String, String> applicationEventProducerFactory) {
        var template = new KafkaTemplate<>(applicationEventProducerFactory);
        // The default error listener logs message contents. Only sanitized metrics/logs are used here.
        template.setProducerListener(null);
        return template;
    }
}
