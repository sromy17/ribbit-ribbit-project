package com.neueda.leap.trading.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.Bank;

public interface BankRepository extends JpaRepository<Bank, Integer> {
    List<Bank> findByAccountIdOrderByBankIdAsc(Integer accountId);
    boolean existsByAccountIdAndRoutingNumberAndAccountNumber(
        Integer accountId,
        String routingNumber,
        String accountNumber
    );
}
