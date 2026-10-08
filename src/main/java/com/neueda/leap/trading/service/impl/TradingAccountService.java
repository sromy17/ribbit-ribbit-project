package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.util.Objects;

import com.neueda.leap.trading.service.contract.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;

@Service
public class TradingAccountService implements AccountService {
    private final TradingAccountRepository accountRepository;

    public TradingAccountService(TradingAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public TradingAccount deposit(Integer accountId, BigDecimal amount) {
        Integer requiredAccountId = Objects.requireNonNull(accountId, "accountId must not be null");
        TradingAccount account = accountRepository.findById(requiredAccountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.depositFunds(amount);
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public TradingAccount withdraw(Integer accountId, BigDecimal amount) {
        Integer requiredAccountId = Objects.requireNonNull(accountId, "accountId must not be null");
        TradingAccount account = accountRepository.findById(requiredAccountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        account.withdrawFunds(amount);
        return accountRepository.save(account);
    }
}
