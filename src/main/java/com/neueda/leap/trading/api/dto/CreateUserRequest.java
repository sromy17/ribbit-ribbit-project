package com.neueda.leap.trading.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank String password
) {
}
