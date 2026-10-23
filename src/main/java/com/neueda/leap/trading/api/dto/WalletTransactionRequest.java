package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record WalletTransactionRequest(
    @NotNull Integer bankId,
    @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
