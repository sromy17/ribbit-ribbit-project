package com.neueda.leap.trading.api.dto;

import com.neueda.leap.trading.domain.AccountType;

import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
    @NotNull Integer userId,
    @NotNull AccountType accountType
) {
}
