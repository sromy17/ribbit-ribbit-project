package com.neueda.leap.trading.service.impl.marketdata;

import tools.jackson.core.JacksonException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaQuoteConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaQuoteConsumer.class);

    private final ObjectMapper objectMapper;
    private final QuoteCache quoteCache;

    public KafkaQuoteConsumer(ObjectMapper objectMapper, QuoteCache quoteCache) {
        this.objectMapper = objectMapper;
        this.quoteCache = quoteCache;
    }

    @KafkaListener(
        topics = "${market-data.kafka.topic:market.quotes}",
        groupId = "${market-data.kafka.group-id:trading-market-data}",
        autoStartup = "${market-data.kafka.enabled:false}"
    )
    public void onMessage(String payload) {
        try {
            @Nullable QuoteSnapshot snapshot = objectMapper.readValue(payload, QuoteSnapshot.class);
            quoteCache.upsert(snapshot);
        } catch (JacksonException ex) {
            log.warn("Failed to parse Kafka quote payload", ex);
        }
    }
}
