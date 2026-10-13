package com.neueda.leap.trading.service.impl.marketdata;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataKafkaPoller {
    private static final Logger log = LoggerFactory.getLogger(MarketDataKafkaPoller.class);

    private final InstrumentRepository instrumentRepository;
    private final FauxnanceQuoteClient fauxnanceQuoteClient;
    private final KafkaQuoteProducer kafkaQuotePublisher;
    private final QuoteCache quoteCache;
    private final boolean kafkaEnabled;

    public MarketDataKafkaPoller(
        InstrumentRepository instrumentRepository,
        FauxnanceQuoteClient fauxnanceQuoteClient,
        KafkaQuoteProducer kafkaQuotePublisher,
        QuoteCache quoteCache,
        @Value("${market-data.kafka.enabled:false}") boolean kafkaEnabled
    ) {
        this.instrumentRepository = instrumentRepository;
        this.fauxnanceQuoteClient = fauxnanceQuoteClient;
        this.kafkaQuotePublisher = kafkaQuotePublisher;
        this.quoteCache = quoteCache;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Scheduled(fixedDelayString = "${market-data.kafka.poll-ms:5000}")
    public void pollAndPublishQuotes() {
        if (!kafkaEnabled) {
            return;
        }

        List<String> symbols = instrumentRepository.findAll()
            .stream()
            .map(instrument -> instrument != null ? instrument.getTicker() : null)
            .filter(Objects::nonNull)
            .map(ticker -> ticker != null ? ticker.trim() : "")
            .filter(s -> !s.isEmpty())
            .map(s -> s.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();

        for (String symbol : symbols) {
            try {
                BigDecimal price = fauxnanceQuoteClient.fetchQuote(symbol);
                QuoteSnapshot snapshot = new QuoteSnapshot(symbol, price, System.currentTimeMillis(), "fauxnance-poller");
                quoteCache.upsert(snapshot);
                kafkaQuotePublisher.produce(snapshot);
            } catch (RuntimeException ex) {
                log.debug("Skipping quote publish for symbol {} due to polling error", symbol, ex);
            }
        }
    }
}
