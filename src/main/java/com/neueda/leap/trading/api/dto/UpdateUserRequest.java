package com.neueda.leap.trading.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
    @NotBlank String username
) {
}
