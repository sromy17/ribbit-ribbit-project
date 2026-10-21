package com.neueda.leap.trading.service.contract;

import java.math.BigDecimal;

public record MarketQuote(
    String symbol,
    BigDecimal price,
    BigDecimal change,
    BigDecimal changePercent
) {
}
