package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import com.neueda.leap.trading.domain.HoldingMovement;

public interface HoldingMovementRepository extends JpaRepository<HoldingMovement, Long> {
}