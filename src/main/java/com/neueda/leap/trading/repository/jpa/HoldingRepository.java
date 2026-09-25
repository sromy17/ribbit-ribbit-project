package com.neueda.leap.trading.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.Holding;

public interface HoldingRepository extends JpaRepository<Holding, Integer> {
    Optional<Holding> findByAccountAccountIdAndInstrumentInstrumentId(Integer accountId, Integer instrumentId);
}
