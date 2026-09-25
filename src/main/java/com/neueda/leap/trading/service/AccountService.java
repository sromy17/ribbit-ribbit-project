package com.neueda.leap.trading.service;

import java.math.BigDecimal;

import com.neueda.leap.trading.domain.TradingAccount;

public interface AccountService {
    TradingAccount deposit(Integer accountId, BigDecimal amount);

    TradingAccount withdraw(Integer accountId, BigDecimal amount);
}
