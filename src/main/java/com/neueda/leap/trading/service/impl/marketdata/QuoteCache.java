package com.neueda.leap.trading.service.impl.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class QuoteCache {
    private final ConcurrentMap<String, QuoteSnapshot> quotesBySymbol = new ConcurrentHashMap<>();

    public void upsert(QuoteSnapshot snapshot) {
        if (snapshot == null || snapshot.symbol() == null || snapshot.symbol().isBlank() || snapshot.price() == null) {
            return;
        }
        quotesBySymbol.put(normalize(snapshot.symbol()), snapshot);
    }

    public Optional<BigDecimal> getFreshPrice(String symbol, Duration maxAge) {
        if (symbol == null || symbol.isBlank() || maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            return Optional.empty();
        }

        QuoteSnapshot snapshot = quotesBySymbol.get(normalize(symbol));
        if (snapshot == null) {
            return Optional.empty();
        }

        long ageMs = System.currentTimeMillis() - snapshot.eventEpochMillis();
        if (ageMs > maxAge.toMillis()) {
            return Optional.empty();
        }

        return Optional.of(snapshot.price());
    }

    private String normalize(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
