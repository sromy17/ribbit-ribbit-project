package com.neueda.leap.trading.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBankAccountRequest(
    @NotBlank @Size(max = 50) String nickname,
    @NotBlank @Pattern(regexp = "\\d{9}") String routingNumber,
    @NotBlank @Pattern(regexp = "\\d{4,17}") String accountNumber
) {
}
