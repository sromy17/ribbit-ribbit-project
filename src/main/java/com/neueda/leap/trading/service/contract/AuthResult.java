package com.neueda.leap.trading.service.contract;

import java.time.Instant;

public record AuthResult(String accessToken, Instant expiresAt) {
}
