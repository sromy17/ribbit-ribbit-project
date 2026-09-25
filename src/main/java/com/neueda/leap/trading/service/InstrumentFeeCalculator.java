package com.neueda.leap.trading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.neueda.leap.trading.domain.OrderSide;

@Component
public class InstrumentFeeCalculator implements FeeCalculator {
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001");
    private static final BigDecimal MIN_FEE = new BigDecimal("1.00");

    @Override
    public BigDecimal calculate(OrderSide side, Integer quantity, BigDecimal price) {
        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = notional.multiply(FEE_RATE); //.setScale(2, RoundingMode.HALF_UP) -> Can use to set decimal places
        return fee.max(MIN_FEE);
    }
}
