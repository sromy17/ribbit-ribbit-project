package com.neueda.leap.trading.service.impl.marketdata;

import java.time.Instant;

public record OrderExecutionCommand(
    Integer orderId,
    String commandId,
    Instant requestedAt
) {
}
