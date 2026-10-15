package com.neueda.leap.trading.service.impl.marketdata;

import java.math.BigDecimal;

public record QuoteSnapshot(
    String symbol,
    BigDecimal price,
    long eventEpochMillis,
    String source
) {
}
