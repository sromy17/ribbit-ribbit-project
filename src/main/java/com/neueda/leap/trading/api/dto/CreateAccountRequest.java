package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.AccountType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
    @NotNull Integer userId,
    @NotNull AccountType accountType,
    @NotNull @DecimalMin("0.01") BigDecimal initialCashBalance
) {
}
