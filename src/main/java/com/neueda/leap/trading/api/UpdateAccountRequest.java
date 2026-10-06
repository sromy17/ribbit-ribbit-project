package com.neueda.leap.trading.api;

import com.neueda.leap.trading.domain.AccountType;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(
    @NotNull AccountType accountType
) {
}
