package com.neueda.leap.trading.service.contract;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketQuote(
    String symbol,
    BigDecimal price,
    BigDecimal change,
    BigDecimal changePercent,
    String source,
    Instant quoteAtUtc,
    Instant receivedAtUtc,
    String currency,
    String priceType
) {
    public MarketQuote(String symbol, BigDecimal price, BigDecimal change, BigDecimal changePercent) {
        this(symbol, price, change, changePercent, null, null, null, null, null);
    }
}
