package com.neueda.leap.trading.api;

import jakarta.validation.constraints.NotBlank;

public record StaffProvisionRequest(
    @NotBlank String username,
    @NotBlank String role
) {
}