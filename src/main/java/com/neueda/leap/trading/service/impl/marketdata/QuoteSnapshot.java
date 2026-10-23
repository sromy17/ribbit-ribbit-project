package com.neueda.leap.trading.service.impl.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.trading.service.contract.MarketQuote;

public record QuoteSnapshot(
    String symbol,
    BigDecimal price,
    long eventEpochMillis,
    String source,
    Instant quoteAtUtc,
    String currency,
    String priceType
) {
    public QuoteSnapshot(String symbol, BigDecimal price, long eventEpochMillis, String source) {
        this(symbol, price, eventEpochMillis, source, null, null, null);
    }

    public static QuoteSnapshot from(MarketQuote quote) {
        if (quote.receivedAtUtc() == null) {
            throw new IllegalArgumentException("Quote receipt timestamp is required for caching");
        }
        return new QuoteSnapshot(quote.symbol(), quote.price(), quote.receivedAtUtc().toEpochMilli(),
            quote.source(), quote.quoteAtUtc(), quote.currency(), quote.priceType());
    }

    public MarketQuote toMarketQuote() {
        return new MarketQuote(symbol, price, null, null, source, quoteAtUtc,
            Instant.ofEpochMilli(eventEpochMillis), currency, priceType);
    }
}
