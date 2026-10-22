package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
    Integer movementId,
    Integer bankId,
    String movementType,
    BigDecimal amount,
    Instant movementDate,
    BigDecimal cashBalance
) {
}
