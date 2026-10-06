package com.neueda.leap.trading.api;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank String accessToken
) {
}
