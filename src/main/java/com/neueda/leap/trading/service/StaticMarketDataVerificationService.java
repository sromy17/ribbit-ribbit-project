package com.neueda.leap.trading.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.neueda.leap.trading.domain.Order;

@Component
public class StaticMarketDataVerificationService implements MarketDataVerificationService {
    @Override
    public BigDecimal verifyExecutionPrice(Order order) {
        // Placeholder until external market data API integration is implemented.
        return order.getPrice();
    }
}
