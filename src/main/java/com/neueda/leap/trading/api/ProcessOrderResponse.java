package com.neueda.leap.trading.api;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.repository.mybatis.AccountOrderSummary;

public record ProcessOrderResponse(
    Integer orderId,
    Integer tradeId,
    OrderStatus status,
    Integer executedQuantity,
    BigDecimal executionPrice,
    BigDecimal fee,
    AccountOrderSummary accountSummary
) {
}
