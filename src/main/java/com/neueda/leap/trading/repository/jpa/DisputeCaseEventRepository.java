package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import com.neueda.leap.trading.domain.DisputeCaseEvent;

public interface DisputeCaseEventRepository extends JpaRepository<DisputeCaseEvent, Long> {
}