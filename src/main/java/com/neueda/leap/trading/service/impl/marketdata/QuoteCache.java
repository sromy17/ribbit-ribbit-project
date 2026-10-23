package com.neueda.leap.trading.service.impl.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class QuoteCache {
    private final ConcurrentMap<String, QuoteSnapshot> quotesBySymbol = new ConcurrentHashMap<>();

    public void upsert(QuoteSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null || snapshot.symbol().isBlank() || snapshot.price() == null || snapshot.price().signum() <= 0
                || snapshot.eventEpochMillis() <= 0 || snapshot.eventEpochMillis() > System.currentTimeMillis()) {
            return;
        }
        quotesBySymbol.compute(normalize(snapshot.symbol()), (symbol, previous) ->
            previous == null || snapshot.eventEpochMillis() > previous.eventEpochMillis() ? snapshot : previous);
    }

    public Optional<BigDecimal> getFreshPrice(String symbol, Duration maxAge) {
        Optional<QuoteSnapshot> snapshot = getFreshSnapshot(symbol, maxAge);
        return snapshot.isPresent() ? Optional.of(snapshot.get().price()) : Optional.empty();
    }

    public Optional<QuoteSnapshot> getFreshSnapshot(String symbol, Duration maxAge) {
        if (symbol == null || symbol.isBlank() || maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            return Optional.empty();
        }

        QuoteSnapshot snapshot = quotesBySymbol.get(normalize(symbol));
        if (snapshot == null) {
            return Optional.empty();
        }

        long ageMs = System.currentTimeMillis() - snapshot.eventEpochMillis();
        if (ageMs < 0 || ageMs > maxAge.toMillis()) {
            return Optional.empty();
        }

        return Optional.of(snapshot);
    }

    public Collection<QuoteSnapshot> getAll() {
        return List.copyOf(quotesBySymbol.values());
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
