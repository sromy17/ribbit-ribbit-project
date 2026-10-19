package com.neueda.leap.trading.service.impl.marketdata;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KafkaQuoteProducer {
    private static final Logger log = LoggerFactory.getLogger(KafkaQuoteProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final boolean kafkaEnabled;
    private final String quoteTopic;

    public KafkaQuoteProducer(
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        @Value("${market-data.kafka.enabled:false}") boolean kafkaEnabled,
        @Value("${market-data.kafka.topic:market.quotes}") String quoteTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.kafkaEnabled = kafkaEnabled;
        this.quoteTopic = quoteTopic;
    }

    public void produce(QuoteSnapshot snapshot) {
        if (!kafkaEnabled || snapshot == null || snapshot.symbol() == null || snapshot.symbol().isBlank()) {
            return;
        }

        if (quoteTopic == null || quoteTopic.isBlank()) {
            return;
        }

        final String topic = Objects.requireNonNull(quoteTopic);
        final String symbol = Objects.requireNonNull(snapshot.symbol());

        try {
            String payload = objectMapper.writeValueAsString(snapshot);
            if (payload == null || payload.isBlank()) {
                return;
            }
            kafkaTemplate.send(topic, symbol, payload)
                .whenComplete((_, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to Produce quote for {} to Kafka topic {}", symbol, topic, ex);
                    }
                });
        } catch (JacksonException ex) {
            log.warn("Failed to serialize quote snapshot for {}", snapshot.symbol(), ex);
        }
    }
}
