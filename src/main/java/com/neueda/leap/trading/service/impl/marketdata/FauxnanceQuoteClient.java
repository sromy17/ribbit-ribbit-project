package com.neueda.leap.trading.service.impl.marketdata;

import tools.jackson.core.JacksonException;
import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.neueda.leap.trading.service.contract.MarketQuote;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FauxnanceQuoteClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private ProviderRequestBudget requestBudget;
    private UkEquityQuoteClient ukQuoteClient;

    @org.springframework.beans.factory.annotation.Autowired
    void setUkQuoteClient(UkEquityQuoteClient client) { this.ukQuoteClient = client; }

    @org.springframework.beans.factory.annotation.Autowired
    void setRequestBudget(ProviderRequestBudget requestBudget) { this.requestBudget = requestBudget; }

    private volatile Instant quotaRetryAt = Instant.EPOCH;

    public FauxnanceQuoteClient(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        @Value("${market-data.fauxnance.base-url:https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1}") String baseUrl,
        @Value("${market-data.fauxnance.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl != null ? baseUrl : "").build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public BigDecimal fetchQuote(String symbol) {
        return fetchMarketQuote(symbol).price();
    }

    public MarketQuote fetchMarketQuote(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.endsWith(".L") && ukQuoteClient != null) {
            if (requestBudget != null) requestBudget.reserve();
            return ukQuoteClient.fetchMarketQuote(normalized);
        }
        if (Instant.now().isBefore(quotaRetryAt)) {
            throw new IllegalStateException("Market data daily quota exhausted; resets at " + quotaRetryAt);
        }

        try {
            if (requestBudget != null) requestBudget.reserve();
            RestClient.RequestHeadersUriSpec<?> request = restClient.get();
            request.uri("/quotes/{symbol}", normalized);
            request.headers(headers -> {
                if (apiKey != null && !apiKey.isBlank()) {
                    headers.set("X-Api-Key", apiKey);
                }
            });
            QuoteApiResponse response = request.retrieve().body(QuoteApiResponse.class);

            if (response == null) {
                throw new IllegalArgumentException("Market data API returned an empty response for symbol " + normalized);
            }
            if (response.error() != null) {
                String message = response.error().message() != null
                    ? response.error().message()
                    : "Market data API returned an error";
                throw new IllegalArgumentException(message + " for symbol " + normalized);
            }
            if (response.data() == null || response.data().price() == null || response.data().price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Market data API returned an invalid price for symbol " + normalized);
            }

            QuoteData data = response.data();
            return new MarketQuote(normalized, data.price(), data.change(), data.changePercent(),
                data.source() == null ? (response.meta() == null || response.meta().source() == null ? "fauxnance" : "fauxnance:" + response.meta().source()) : data.source(),
                data.timestamp(), Instant.now(), data.currency(), data.priceType(),
                response.meta() == null ? null : response.meta().stale(),
                response.meta() == null ? null : response.meta().disclaimer());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                String message = extractErrorMessage(ex);
                // A daily quota applies to all symbols. Do not keep retrying it per tile.
                if (message.toLowerCase(Locale.ROOT).contains("daily quota")) {
                    quotaRetryAt = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                    throw new IllegalStateException("Market data daily quota exhausted; resets at " + quotaRetryAt, ex);
                }
            }
            throw new IllegalArgumentException(extractErrorMessage(ex) + " for symbol " + normalized, ex);
        }
    }

    public boolean isTickerValid(String symbol) {
        try {
            fetchQuote(symbol);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Instrument ticker is blank");
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private String extractErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body.isBlank()) {
            return "Market data API request failed with status " + ex.getStatusCode();
        }

        try {
            @Nullable ErrorEnvelope envelope = objectMapper.readValue(body, ErrorEnvelope.class);
            if (Objects.nonNull(envelope) && envelope.error() != null && envelope.error().message() != null) {
                return envelope.error().message();
            }
        } catch (JacksonException ignored) {
            // Fall through to generic message.
        }

        return "Market data API request failed with status " + ex.getStatusCode();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuoteApiResponse(QuoteData data, ApiError error, QuoteMeta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuoteMeta(String source, Boolean stale, String disclaimer) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record QuoteData(BigDecimal price, BigDecimal change, BigDecimal changePercent,
                             String source, @JsonAlias("asOf") Instant timestamp, String currency, String priceType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorEnvelope(ApiError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiError(String code, String message) {
    }
}
