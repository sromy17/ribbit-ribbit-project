package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateBalanceRequest(
    @NotNull @DecimalMin("0.00") BigDecimal cashBalance
) {
}
