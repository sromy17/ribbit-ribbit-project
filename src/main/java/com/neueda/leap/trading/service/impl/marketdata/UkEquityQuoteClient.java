package com.neueda.leap.trading.service.impl.marketdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neueda.leap.trading.service.contract.MarketQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** London quotes are often in GBp (pence), which must not be labelled as GBP. */
@Component
public class UkEquityQuoteClient {
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public UkEquityQuoteClient(RestClient.Builder builder) {
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        client = builder.baseUrl("https://query1.finance.yahoo.com").requestFactory(factory).build();
    }

    UkEquityQuoteClient(RestClient client) { this.client = client; }

    public MarketQuote fetchMarketQuote(String symbol) {
        var response = client.get().uri("/v8/finance/chart/{symbol}?interval=1d&range=1d", symbol)
            .header("User-Agent", "Ribbit/1.0").retrieve().body(Envelope.class);
        if (response == null || response.chart() == null || response.chart().result() == null
            || response.chart().result().isEmpty()) throw new IllegalArgumentException("UK quote unavailable for " + symbol);
        var meta = response.chart().result().getFirst().meta();
        if (meta == null || !symbol.equalsIgnoreCase(meta.symbol()) || meta.regularMarketPrice() == null
            || meta.regularMarketPrice().signum() <= 0 || meta.regularMarketTime() == null
            || !("GBp".equals(meta.currency()) || "GBX".equals(meta.currency()) || "GBP".equals(meta.currency())))
            throw new IllegalArgumentException("Invalid UK quote identity, currency or price for " + symbol);
        BigDecimal divisor = "GBP".equals(meta.currency()) ? BigDecimal.ONE : new BigDecimal("100");
        BigDecimal price = meta.regularMarketPrice().divide(divisor);
        BigDecimal previous = meta.chartPreviousClose() == null ? null : meta.chartPreviousClose().divide(divisor);
        BigDecimal change = previous == null ? null : price.subtract(previous);
        BigDecimal percent = previous == null || previous.signum() <= 0 ? null
            : change.multiply(new BigDecimal("100")).divide(previous, 8, RoundingMode.HALF_UP);
        return new MarketQuote(symbol, price, change, percent, "yahoo:chart",
            Instant.ofEpochSecond(meta.regularMarketTime()), Instant.now(), "GBP", "PRICE_PER_UNIT", null,
            "Indicative market data. Quotes may be delayed.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(Chart chart) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Chart(List<Result> result) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(Meta meta) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Meta(String symbol, String currency, BigDecimal regularMarketPrice, BigDecimal chartPreviousClose,
                Long regularMarketTime) {}
}
