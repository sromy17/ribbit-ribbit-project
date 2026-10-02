package com.neueda.leap.trading.service.contract;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.Order;

public interface MarketDataVerificationService {
    BigDecimal verifyExecutionPrice(Order order);
}
