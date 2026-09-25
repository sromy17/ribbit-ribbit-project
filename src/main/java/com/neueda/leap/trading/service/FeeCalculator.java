package com.neueda.leap.trading.service;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.OrderSide;

public interface FeeCalculator {
    BigDecimal calculate(OrderSide side, Integer quantity, BigDecimal price);
}
