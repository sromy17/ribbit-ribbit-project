package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.TradingAccount;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from TradingAccount a where a.accountId = :id")
    java.util.Optional<TradingAccount> findForUpdate(@org.springframework.data.repository.query.Param("id") Integer id);

    java.util.List<TradingAccount> findAllByUserUserIdOrderByAccountIdAsc(Integer userId);
}
