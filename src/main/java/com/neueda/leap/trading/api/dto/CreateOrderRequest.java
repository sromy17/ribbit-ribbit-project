package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.OrderSide;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
    @NotNull Integer accountId,
    String ticker,
    @NotNull OrderSide side,
    @NotNull @Min(1) Integer quantity,
    @NotNull @DecimalMin("0.01") BigDecimal price,
    @Min(1) Integer instrumentId
) {
    public CreateOrderRequest(Integer accountId, String ticker, OrderSide side, Integer quantity, BigDecimal price) {
        this(accountId, ticker, side, quantity, price, null);
    }

    @jakarta.validation.constraints.AssertTrue(message = "Provide an instrument ID or ticker")
    public boolean isInstrumentReferenceValid() {
        return instrumentId != null || (ticker != null && !ticker.isBlank());
    }
}
