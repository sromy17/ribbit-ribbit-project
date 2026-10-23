package com.neueda.leap.trading.api.dto;

import com.neueda.leap.trading.domain.OrderSide;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateAccountOrderRequest(
    String ticker,
    @NotNull OrderSide side,
    @NotNull @Min(1) Integer quantity,
    @Min(1) Integer instrumentId
) {
    public CreateAccountOrderRequest(String ticker, OrderSide side, Integer quantity) {
        this(ticker, side, quantity, null);
    }

    @jakarta.validation.constraints.AssertTrue(message = "Provide an instrument ID or ticker")
    public boolean isInstrumentReferenceValid() {
        return instrumentId != null || (ticker != null && !ticker.isBlank());
    }
}
