package com.neueda.leap.trading.service.contract;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.Order;

public interface MarketDataVerificationService {
    BigDecimal verifyExecutionPrice(Order order);

    default MarketQuote verifyExecutionQuote(Order order) {
        return new MarketQuote(order.getInstrument().getTicker(), verifyExecutionPrice(order), null, null);
    }

    boolean isTickerValid(String ticker);

    BigDecimal getCurrentPrice(String ticker);

    MarketQuote getQuote(String ticker);
}
