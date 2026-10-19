package com.neueda.leap.trading.service.impl.marketdata;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderExecutionProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String topic;

    public KafkaOrderExecutionProducer(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        @Value("${market-data.execution.kafka.enabled:false}") boolean enabled,
        @Value("${market-data.execution.kafka.topic:orders.execute}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.topic = topic;
    }

    public void enqueue(Integer orderId) {
        if (!enabled) {
            throw new IllegalStateException("Asynchronous order execution is disabled");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("Order execution Kafka topic is not configured");
        }

        OrderExecutionCommand command = new OrderExecutionCommand(orderId, UUID.randomUUID().toString(), Instant.now());
        try {
            String payload = Objects.requireNonNull(objectMapper.writeValueAsString(command));
            String key = Objects.requireNonNull(String.valueOf(orderId));
            String configuredTopic = Objects.requireNonNull(topic);
            kafkaTemplate.send(configuredTopic, key, payload);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Could not serialize order execution command", ex);
        }
    }
}
