package com.neueda.leap.trading.service.contract;

import java.math.BigDecimal;
import java.util.List;

import com.neueda.leap.trading.domain.Order;

public interface MarketDataVerificationService {
    BigDecimal verifyExecutionPrice(Order order);

    boolean isTickerValid(String ticker);

    BigDecimal getCurrentPrice(String ticker);

    MarketQuote getQuote(String ticker);

    List<MarketQuote> getQuotes(List<String> tickers);
}
