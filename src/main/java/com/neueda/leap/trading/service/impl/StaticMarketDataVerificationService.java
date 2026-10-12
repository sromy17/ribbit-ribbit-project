package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

import com.neueda.leap.trading.service.impl.marketdata.FauxnanceQuoteClient;
import com.neueda.leap.trading.service.impl.marketdata.KafkaQuotePublisher;
import com.neueda.leap.trading.service.impl.marketdata.QuoteCache;
import com.neueda.leap.trading.service.impl.marketdata.QuoteSnapshot;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.neueda.leap.trading.domain.Order;

@Component
public class StaticMarketDataVerificationService implements MarketDataVerificationService {
    private static final Logger log = LoggerFactory.getLogger(StaticMarketDataVerificationService.class);

    private final FauxnanceQuoteClient fauxnanceQuoteClient;
    private final QuoteCache quoteCache;
    private final KafkaQuotePublisher kafkaQuotePublisher;
    private final boolean useOrderPriceFallback;
    private final long quoteCacheMaxAgeMs;

    public StaticMarketDataVerificationService(
        FauxnanceQuoteClient fauxnanceQuoteClient,
        QuoteCache quoteCache,
        KafkaQuotePublisher kafkaQuotePublisher,
        @Value("${market-data.fauxnance.use-order-price-fallback:true}") boolean useOrderPriceFallback,
        @Value("${market-data.kafka.quote-cache-max-age-ms:1500}") long quoteCacheMaxAgeMs
    ) {
        this.fauxnanceQuoteClient = fauxnanceQuoteClient;
        this.quoteCache = quoteCache;
        this.kafkaQuotePublisher = kafkaQuotePublisher;
        this.useOrderPriceFallback = useOrderPriceFallback;
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
            kafkaQuotePublisher.publish(snapshot);
            return livePrice;
        } catch (RuntimeException ex) {
            return fallbackToOrderPrice(order, "Failed to retrieve market data for symbol " + symbol + ": " + ex.getMessage(), ex);
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

    /**
     * REVISIT REVISIT REVISIT REVISIT REVISIT!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * This uses the price of a ticker in the OrderRequest in the event that retrieving the 
     * real time price fails. This is most likely not the logic we want in prod. Consider just 
     * failing the order execution if we fail to retrieve the real-time market price.
     */
    private BigDecimal fallbackToOrderPrice(Order order, String reason, Exception ex) {
        if (!useOrderPriceFallback || order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(reason);
        }

        if (ex == null) {
            log.warn("{}. Falling back to order-submitted price {}.", reason, order.getPrice());
        } else {
            log.warn("{}. Falling back to order-submitted price {}.", reason, order.getPrice(), ex);
        }
        return order.getPrice();
    }
}
