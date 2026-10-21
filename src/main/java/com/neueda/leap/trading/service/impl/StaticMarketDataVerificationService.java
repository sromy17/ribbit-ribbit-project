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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        String symbol = ticker.trim().toUpperCase(Locale.ROOT);

        try {
            QuoteApiResponse response = restClient.get()
                .uri("/quotes/{symbol}", symbol)
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.set("x-api-key", apiKey);
                    }
                })
                .retrieve()
                .body(QuoteApiResponse.class);

            if (response == null || response.data() == null || response.data().price() == null) {
                throw new IllegalStateException("No price returned for " + symbol);
            }
            QuoteData data = response.data();
            return new MarketQuote(symbol, data.price(), data.change(), data.changePercent());
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(extractErrorMessage(ex), ex);
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

    private String extractErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "Market data API request failed with status " + ex.getStatusCode();
        }

        try {
            ErrorEnvelope envelope = objectMapper.readValue(body, ErrorEnvelope.class);
            if (envelope != null && envelope.error() != null && envelope.error().message() != null) {
                return envelope.error().message();
            }
        } catch (IOException ignored) {
            // Fall through to generic message.
        }

        return "Market data API request failed with status " + ex.getStatusCode();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuoteApiResponse(QuoteData data, ApiError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuoteData(BigDecimal price, BigDecimal change, BigDecimal changePercent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorEnvelope(ApiError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiError(String code, String message) {
    }
}
