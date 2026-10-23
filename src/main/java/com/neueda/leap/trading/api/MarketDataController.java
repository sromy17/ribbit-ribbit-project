package com.neueda.leap.trading.api;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;

import com.neueda.leap.trading.service.impl.marketdata.FauxnanceQuoteClient;
import com.neueda.leap.trading.service.impl.marketdata.KafkaQuoteProducer;
import com.neueda.leap.trading.service.impl.marketdata.QuoteCache;
import com.neueda.leap.trading.service.impl.marketdata.QuoteSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/market-data/quotes")
public class MarketDataController {
    private final FauxnanceQuoteClient fauxnanceQuoteClient;
    private final KafkaQuoteProducer kafkaQuoteProducer;
    private final QuoteCache quoteCache;
    private final long quoteCacheMaxAgeMs;

    public MarketDataController(
        FauxnanceQuoteClient fauxnanceQuoteClient,
        KafkaQuoteProducer kafkaQuoteProducer,
        QuoteCache quoteCache,
        @Value("${market-data.kafka.quote-cache-max-age-ms:1500}") long quoteCacheMaxAgeMs
    ) {
        this.fauxnanceQuoteClient = fauxnanceQuoteClient;
        this.kafkaQuoteProducer = kafkaQuoteProducer;
        this.quoteCache = quoteCache;
        this.quoteCacheMaxAgeMs = quoteCacheMaxAgeMs;
    }

    @GetMapping
    public Collection<QuoteSnapshot> getCachedQuotes() {
        return quoteCache.getAll();
    }

    @GetMapping("/{symbol}")
    public QuoteSnapshot getQuote(@PathVariable String symbol) {
        String normalized = normalize(symbol);
        Duration maxAge = Duration.ofMillis(Math.max(quoteCacheMaxAgeMs, 1));

        return quoteCache.getFreshSnapshot(normalized, maxAge)
            .orElseGet(() -> fetchAndPublish(normalized));
    }

    private QuoteSnapshot fetchAndPublish(String symbol) {
        try {
            QuoteSnapshot snapshot = QuoteSnapshot.from(fauxnanceQuoteClient.fetchMarketQuote(symbol));
            quoteCache.upsert(snapshot);
            kafkaQuoteProducer.produce(snapshot);
            return snapshot;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Unable to retrieve market data for " + symbol,
                ex
            );
        }
    }

    private String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Symbol is required");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}