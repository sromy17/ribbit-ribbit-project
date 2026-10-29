package com.neueda.leap.trading.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neueda.leap.trading.service.impl.marketdata.ProviderRequestBudget;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/market/history")
public class MarketHistoryController {
    private final RestClient client;
    private final ProviderRequestBudget budget;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public MarketHistoryController(RestClient.Builder builder, ProviderRequestBudget budget) {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = builder.baseUrl("https://query1.finance.yahoo.com").requestFactory(factory).build();
        this.budget = budget;
    }

    MarketHistoryController(RestClient client, ProviderRequestBudget budget) {
        this.client = client;
        this.budget = budget;
    }

    @GetMapping("/{symbol}")
    public History history(@PathVariable String symbol) {
        String key = symbol.trim().toUpperCase(Locale.ROOT);
        if (!key.matches("[A-Z][A-Z0-9.-]{0,21}\\.(L|NS)"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "History supports London and NSE equity symbols");
        try {
            return cache.compute(key, (ticker, previous) -> {
                if (previous != null && previous.expiresAt().isAfter(Instant.now())) return previous;
                budget.reserve();
                Envelope envelope = client.get().uri("/v8/finance/chart/{symbol}?interval=1d&range=1mo", ticker)
                    .header("User-Agent", "Ribbit/1.0").retrieve().body(Envelope.class);
                return new Cached(parse(ticker, envelope), Instant.now().plus(Duration.ofHours(1)));
            }).history();
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Price history is temporarily unavailable", error);
        }
    }

    static History parse(String symbol, Envelope envelope) {
        if (envelope == null || envelope.chart() == null || envelope.chart().result() == null
            || envelope.chart().result().isEmpty()) throw new IllegalArgumentException("No history");
        Result result = envelope.chart().result().getFirst();
        Meta meta = result.meta();
        if (meta == null || !symbol.equalsIgnoreCase(meta.symbol())) throw new IllegalArgumentException("History identity mismatch");
        boolean pence = "GBp".equals(meta.currency()) || "GBX".equals(meta.currency());
        String currency = pence ? "GBP" : meta.currency();
        if (!(symbol.endsWith(".L") && "GBP".equals(currency) || symbol.endsWith(".NS") && "INR".equals(currency)))
            throw new IllegalArgumentException("History currency mismatch");
        if (result.timestamp() == null || result.indicators() == null || result.indicators().quote() == null
            || result.indicators().quote().isEmpty()) throw new IllegalArgumentException("No closing prices");
        List<BigDecimal> prices = result.indicators().quote().getFirst().close();
        if (prices == null) throw new IllegalArgumentException("No closing prices");
        var points = new ArrayList<Point>();
        for (int i = 0; i < Math.min(prices.size(), result.timestamp().size()); i++) {
            BigDecimal price = prices.get(i);
            Long timestamp = result.timestamp().get(i);
            if (price != null && price.signum() > 0 && timestamp != null)
                points.add(new Point(Instant.ofEpochSecond(timestamp), pence ? price.movePointLeft(2) : price));
        }
        if (points.size() < 2) throw new IllegalArgumentException("Insufficient history");
        return new History(symbol, currency, "yahoo:chart", points);
    }

    record Cached(History history, Instant expiresAt) {}
    public record History(String symbol, String currency, String source, List<Point> points) {}
    public record Point(Instant atUtc, BigDecimal price) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Envelope(Chart chart) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Chart(List<Result> result) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Result(Meta meta, List<Long> timestamp, Indicators indicators) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Meta(String symbol, String currency) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Indicators(List<Prices> quote) {}
    @JsonIgnoreProperties(ignoreUnknown = true) record Prices(List<BigDecimal> close) {}
}
