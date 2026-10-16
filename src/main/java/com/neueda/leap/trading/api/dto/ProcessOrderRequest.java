package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProcessOrderRequest(
    @NotNull Integer accountId,
    @NotBlank String ticker,
    @NotNull OrderSide side,
    @NotNull @Min(1) Integer quantity,
    BigDecimal price
) {
}
