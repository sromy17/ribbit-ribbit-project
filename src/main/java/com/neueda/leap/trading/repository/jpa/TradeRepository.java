package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.Trade;

public interface TradeRepository extends JpaRepository<Trade, Integer> {
}
