package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

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
        return verifyExecutionQuote(order).price();
    }

    @Override
    public MarketQuote verifyExecutionQuote(Order order) {
        String symbol = extractSymbol(order);

        try {
            Duration maxAge = Duration.ofMillis(Math.max(quoteCacheMaxAgeMs, 1));
            QuoteSnapshot cached = quoteCache.getFreshSnapshot(symbol, maxAge).orElse(null);
            if (cached != null) {
                return cached.toMarketQuote();
            }

            MarketQuote quote = fauxnanceQuoteClient.fetchMarketQuote(symbol);
            QuoteSnapshot snapshot = QuoteSnapshot.from(quote);
            quoteCache.upsert(snapshot);
            kafkaQuoteProducer.produce(snapshot);
            return quote;
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
            String symbol = ticker.trim().toUpperCase(Locale.ROOT);
            var cached = displayedQuotes.get(symbol);
            if (cached != null && cached.receivedAtUtc() != null
                    && Duration.between(cached.receivedAtUtc(), java.time.Instant.now()).toMillis() < 15000) return cached;
            MarketQuote quote = fauxnanceQuoteClient.fetchMarketQuote(symbol);
            if (quote.receivedAtUtc() != null) {
                quoteCache.upsert(QuoteSnapshot.from(quote));
                if (displayedQuotes.size() >= 512) displayedQuotes.clear();
                displayedQuotes.put(symbol, quote);
            }
            return quote;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Price unavailable for " + ticker, ex);

        }
    }

    @Override
    public List<MarketQuote> getQuotes(List<String> tickers) {
        if (tickers == null || tickers.isEmpty() || tickers.size() > 50) {
            throw new IllegalArgumentException("Request between 1 and 50 symbols");
        }
        List<String> symbols = tickers.stream().map(t -> t == null ? "" : t.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        if (symbols.stream().anyMatch(t -> !t.matches("[A-Z0-9][A-Z0-9.\\^:=/-]{0,24}"))) {
            throw new IllegalArgumentException("Invalid market symbol");
        }
        if (!batchSlots.tryAcquire()) throw new IllegalStateException("Quote service is busy; retry shortly");
        try {
        List<java.util.concurrent.CompletableFuture<MarketQuote>> requests = symbols.stream()
            .map(symbol -> java.util.concurrent.CompletableFuture.supplyAsync(() -> getQuote(symbol), quoteWorkers))
            .toList();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        List<MarketQuote> quotes = new ArrayList<>();
        try {
            for (var request : requests) {
                try {
                    quotes.add(request.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Quote request interrupted", interrupted);
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException unavailable) {
                    // Return only available quotes. Never invent prices for missing symbols.
                }
            }
        } finally {
            requests.forEach(request -> request.cancel(true));
        }
        if (quotes.isEmpty()) throw new IllegalStateException("Market data is temporarily unavailable");
        return quotes;
        } finally { batchSlots.release(); }
    }

    private final java.util.concurrent.ConcurrentHashMap<String,MarketQuote> displayedQuotes = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore batchSlots = new java.util.concurrent.Semaphore(2);
    private final java.util.concurrent.ExecutorService quoteWorkers = new java.util.concurrent.ThreadPoolExecutor(
        8, 8, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(100), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    @jakarta.annotation.PreDestroy
    public void stopQuoteWorkers() { quoteWorkers.shutdownNow(); }

    private String extractSymbol(Order order) {
        if (order == null || order.getInstrument() == null || order.getInstrument().getTicker() == null) {
            throw new IllegalArgumentException("Order does not contain an instrument ticker");
        }

        String providerSymbol = order.getInstrument().getMarketDataSymbol();
        String symbol = (providerSymbol == null ? order.getInstrument().getTicker() : providerSymbol).trim();
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("Order instrument ticker is blank");
        }

        return symbol.toUpperCase(Locale.ROOT);
    }
}
