package com.neueda.leap.trading.repository.jpa;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.HoldingId;

public interface HoldingRepository extends JpaRepository<Holding, HoldingId> {
    Optional<Holding> findByAccountAccountIdAndInstrumentInstrumentId(Integer accountId, Integer instrumentId);

    List<Holding> findByAccountAccountId(Integer accountId);

    void deleteByAccountAccountIdAndInstrumentInstrumentId(Integer accountId, Integer instrumentId);
}
