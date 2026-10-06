package com.neueda.leap.trading.api;

import com.neueda.leap.trading.domain.OrderStatus;

public record CreateOrderResponse(
    Integer orderId,
    Integer accountId,
    Integer instrumentId,
    OrderStatus status
) {
}
