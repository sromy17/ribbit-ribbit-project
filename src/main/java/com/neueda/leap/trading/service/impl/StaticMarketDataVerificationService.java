package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

import com.neueda.leap.trading.service.impl.marketdata.FauxnanceQuoteClient;
import com.neueda.leap.trading.service.impl.marketdata.KafkaQuoteProducer;
import com.neueda.leap.trading.service.impl.marketdata.QuoteCache;
import com.neueda.leap.trading.service.impl.marketdata.QuoteSnapshot;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.MarketQuote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.neueda.leap.trading.domain.Order;

@Component
public class StaticMarketDataVerificationService implements MarketDataVerificationService {
    private final FauxnanceQuoteClient fauxnanceQuoteClient;
    private final QuoteCache quoteCache;
    private final KafkaQuoteProducer kafkaQuoteProducer;
    private final long quoteCacheMaxAgeMs;

    public StaticMarketDataVerificationService(
        FauxnanceQuoteClient fauxnanceQuoteClient,
        QuoteCache quoteCache,
        KafkaQuoteProducer kafkaQuoteProducer,
        @Value("${market-data.kafka.quote-cache-max-age-ms:1500}") long quoteCacheMaxAgeMs
    ) {
        this.fauxnanceQuoteClient = fauxnanceQuoteClient;
        this.quoteCache = quoteCache;
        this.kafkaQuoteProducer = kafkaQuoteProducer;
        this.quoteCacheMaxAgeMs = quoteCacheMaxAgeMs;
    }

    @Override
    public BigDecimal verifyExecutionPrice(Order order) {
        String symbol = extractSymbol(order);

        try {
            Duration maxAge = Duration.ofMillis(Math.max(quoteCacheMaxAgeMs, 1));
            BigDecimal cachedPrice = quoteCache.getFreshPrice(symbol, maxAge).orElse(null);
            if (cachedPrice != null) {
                return cachedPrice;
            }

            BigDecimal livePrice = fauxnanceQuoteClient.fetchQuote(symbol);
            QuoteSnapshot snapshot = new QuoteSnapshot(symbol, livePrice, System.currentTimeMillis(), "fauxnance-sync");
            quoteCache.upsert(snapshot);
            kafkaQuoteProducer.produce(snapshot);
            return livePrice;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                "Unable to verify execution price for symbol " + symbol + ": " + ex.getMessage(),
                ex
            );
        }
    }

    @Override
    public boolean isTickerValid(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return false;
        }

        return fauxnanceQuoteClient.isTickerValid(ticker.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public BigDecimal getCurrentPrice(String ticker) {
        return getQuote(ticker).price();
    }

    @Override
    public MarketQuote getQuote(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }
        try {
            return fauxnanceQuoteClient.fetchMarketQuote(ticker);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Price unavailable for " + ticker, ex);
        }
    }

    private String extractSymbol(Order order) {
        if (order == null || order.getInstrument() == null || order.getInstrument().getTicker() == null) {
            throw new IllegalArgumentException("Order does not contain an instrument ticker");
        }

        String symbol = order.getInstrument().getTicker().trim();
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Order instrument ticker is blank");
        }

        return symbol.toUpperCase(Locale.ROOT);
    }
}
