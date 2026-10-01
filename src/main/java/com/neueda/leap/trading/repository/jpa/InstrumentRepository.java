package com.neueda.leap.trading.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.Instrument;

public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {
	Optional<Instrument> findByTickerIgnoreCase(String ticker);
}
