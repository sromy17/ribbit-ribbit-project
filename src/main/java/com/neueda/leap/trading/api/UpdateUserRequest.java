package com.neueda.leap.trading.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
    @NotBlank String username
) {
}
