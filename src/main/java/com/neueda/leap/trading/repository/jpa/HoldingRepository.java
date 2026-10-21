package com.neueda.leap.trading.repository.jpa;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.TradingAccount;

public interface HoldingRepository extends JpaRepository<Holding, Integer> {
    Optional<Holding> findByAccountAccountIdAndInstrumentInstrumentId(Integer accountId, Integer instrumentId);

    @Query("select h from Holding h join fetch h.instrument where h.account.accountId = :accountId and h.quantity > 0")
    List<Holding> findByAccountAccountId(@Param("accountId") Integer accountId);

    /**
     * Find a holding by account and instrument.
     * Used in order validation to check if seller has sufficient holdings.
     * BR-05: Validates SELL orders have sufficient holdings before acceptance.
     */
    Holding findByAccountAndInstrument(TradingAccount account, Instrument instrument);
}
