package com.neueda.leap.trading.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Authenticated, bounded news proxy. Provider credentials never enter browser bundles. */
@RestController
@RequestMapping("/market")
public class MarketNewsController {
    private record Cached(long expiresAt, List<Map<String,Object>> articles) { }
    private final ConcurrentHashMap<String,Cached> cache = new ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore requests = new java.util.concurrent.Semaphore(4);
    private final RestClient provider;
    private final String apiKey;
    private final PublicMarketNewsFeed publicFeed;

    public MarketNewsController(String url, String apiKey) {
        this(url,apiKey,PublicMarketNewsFeed.DEFAULT_URL);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MarketNewsController(@Value("${market-data.finnhub.base-url:https://finnhub.io/api/v1}") String url,
            @Value("${market-data.finnhub.api-key:}") String apiKey,
            @Value("${market-data.news.rss-url:https://feeds.finance.yahoo.com/rss/2.0/headline}") String rssUrl) {
        this.apiKey = apiKey;
        this.publicFeed = new PublicMarketNewsFeed(rssUrl);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.provider = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @GetMapping("/news")
    public List<Map<String,Object>> news(@RequestParam(defaultValue="general") String category) {
        if (!"general".equals(category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported news category");
        return fetch("news", Map.of("category","general"));
    }

    @GetMapping("/company-news")
    public List<Map<String,Object>> company(@RequestParam String symbol, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        String normalized = symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9.-]{0,9}") || from.isAfter(to) || ChronoUnit.DAYS.between(from,to)>31)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid symbol and a range of at most 31 days");
        return fetch("company-news", Map.of("symbol",normalized,"from",from.toString(),"to",to.toString()));
    }

    private List<Map<String,Object>> fetch(String endpoint, Map<String,String> params) {
        String key = endpoint+new java.util.TreeMap<>(params);
        Cached cached = cache.get(key);
        if (cached!=null && cached.expiresAt()>System.currentTimeMillis()) return cached.articles();
        if (!requests.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"News service is busy; retry shortly");
        try {
            List<Map<String,Object>> articles = apiKey.isBlank() ? publicFeed.fetch(params) : provider.get().uri(builder -> {
                builder.path("/"+endpoint); params.forEach(builder::queryParam); return builder.build();
            }).header("X-Finnhub-Token",apiKey).retrieve().body(new ParameterizedTypeReference<List<Map<String,Object>>>() {});
            if (articles==null) throw new IllegalStateException("Missing news response");
            List<Map<String,Object>> result = articles.stream().filter(a -> a.get("headline") instanceof String && a.get("datetime") instanceof Number && a.get("url") instanceof String link && link.startsWith("https://")).limit(100).toList();
            if (cache.size()>=128) cache.clear();
            cache.put(key,new Cached(System.currentTimeMillis()+60_000,result));
            return result;
        } catch (RuntimeException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Market news is temporarily unavailable");
        } finally { requests.release(); }
    }
}
