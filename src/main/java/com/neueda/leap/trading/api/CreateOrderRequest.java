package com.neueda.leap.trading.api;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
    @NotNull Integer accountId,
    @NotNull Integer instrumentId,
    @NotNull OrderSide side,
    @NotNull @Min(1) Integer quantity,
    @NotNull @DecimalMin("0.01") BigDecimal price
) {
}
