package com.neueda.leap.trading.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank String accessToken
) {
}
