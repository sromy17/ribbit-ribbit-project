package com.neueda.leap.trading.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.MarketQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;

import com.neueda.leap.trading.domain.Order;

@Component
public class StaticMarketDataVerificationService implements MarketDataVerificationService {
    private static final Logger log = LoggerFactory.getLogger(StaticMarketDataVerificationService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final boolean useOrderPriceFallback;

    public StaticMarketDataVerificationService(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        @Value("${market-data.fauxnance.base-url:https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1}") String baseUrl,
        @Value("${market-data.fauxnance.api-key:}") String apiKey,
        @Value("${market-data.fauxnance.use-order-price-fallback:true}") boolean useOrderPriceFallback
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl != null ? baseUrl : "").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.useOrderPriceFallback = useOrderPriceFallback;
    }

    @Override
    public BigDecimal verifyExecutionPrice(Order order) {
        String symbol = extractSymbol(order);

        try {
            QuoteApiResponse response = restClient.get()
                .uri("/quotes/{symbol}", symbol)
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.set("X-Api-Key", apiKey);
                    }
                })
                .retrieve()
                .body(QuoteApiResponse.class);

            if (response == null) {
                return fallbackToOrderPrice(order, "Market data API returned an empty response for symbol " + symbol, null);
            }

            if (response.error() != null) {
                String message = response.error().message() != null ? response.error().message() : "Market data API returned an error";
                return fallbackToOrderPrice(order, message + " for symbol " + symbol, null);
            }

            if (response.data() == null || response.data().price() == null || response.data().price().compareTo(BigDecimal.ZERO) <= 0) {
                return fallbackToOrderPrice(order, "Market data API returned an invalid price for symbol " + symbol, null);
            }

            return response.data().price();
        } catch (RestClientResponseException ex) {
            String message = extractErrorMessage(ex);
            return fallbackToOrderPrice(order, message + " for symbol " + symbol, ex);
        } catch (RuntimeException ex) {
            return fallbackToOrderPrice(order, "Failed to retrieve market data for symbol " + symbol, ex);
        }
    }

    @Override
    public boolean isTickerValid(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return false;
        }

        String symbol = ticker.trim().toUpperCase(Locale.ROOT);

        try {
            QuoteApiResponse response = restClient.get()
                .uri("/quotes/{symbol}", symbol)
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.set("X-Api-Key", apiKey);
                    }
                })
                .retrieve()
                .body(QuoteApiResponse.class);

            return response != null
                && response.error() == null
                && response.data() != null
                && response.data().price() != null
                && response.data().price().compareTo(BigDecimal.ZERO) > 0;
        } catch (RuntimeException ex) {
            return false;
        }
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
            String message = extractErrorMessage(ex);
            log.warn("Fauxnance quote request failed for {} with HTTP {}: {}", symbol, ex.getStatusCode().value(), message);
            throw new IllegalStateException(
                "Market data provider returned HTTP " + ex.getStatusCode().value() + " for " + symbol,
                ex
            );
        }
    }

    @Override
    public List<MarketQuote> getQuotes(List<String> tickers) {
        if (tickers == null || tickers.isEmpty() || tickers.size() > 25) {
            throw new IllegalArgumentException("Between 1 and 25 tickers are required");
        }

        String symbols = tickers.stream()
            .filter(ticker -> ticker != null && !ticker.isBlank())
            .map(ticker -> ticker.trim().toUpperCase(Locale.ROOT))
            .distinct()
            .reduce((left, right) -> left + "," + right)
            .orElseThrow(() -> new IllegalArgumentException("At least one ticker is required"));

        try {
            JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/quotes").queryParam("symbols", symbols).build())
                .headers(headers -> {
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.set("X-Api-Key", apiKey);
                    }
                })
                .retrieve()
                .body(JsonNode.class);

            List<MarketQuote> quotes = new ArrayList<>();
            JsonNode items = response == null ? null : response.path("data").path("quotes");
            if (items == null || !items.isArray()) {
                throw new IllegalStateException("Market data API returned no quote list");
            }
            for (JsonNode item : items) {
                JsonNode quote = item.path("quote");
                if (!quote.isObject() || quote.path("price").isMissingNode()) {
                    continue;
                }
                quotes.add(new MarketQuote(
                    quote.path("symbol").asText(item.path("symbol").asText()),
                    quote.path("price").decimalValue(),
                    quote.path("change").isNull() ? null : quote.path("change").decimalValue(),
                    quote.path("changePercent").isNull() ? null : quote.path("changePercent").decimalValue()
                ));
            }
            return quotes;
        } catch (RestClientResponseException ex) {
            String message = extractErrorMessage(ex);
            log.warn("Fauxnance batch quote request failed with HTTP {}: {}", ex.getStatusCode().value(), message);
            throw new IllegalStateException(
                "Market data provider returned HTTP " + ex.getStatusCode().value(),
                ex
            );
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
