package com.neueda.leap.trading.repository.mybatis;

import java.math.BigDecimal;

public record AccountOrderSummary(
    Integer accountId,
    String username,
    BigDecimal availableFunds,
    Long openOrders,
    Long executedOrders,
    BigDecimal totalExecutedNotional
) {
}
