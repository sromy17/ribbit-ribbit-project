package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.neueda.leap.trading.domain.Trade;

public interface TradeRepository extends JpaRepository<Trade, Integer> {
	@Query("select coalesce(max(t.tradeId), 0) from Trade t")
	Integer findMaxTradeId();
}
