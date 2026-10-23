package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import com.neueda.leap.trading.domain.CashMovement;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {
}