package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.neueda.leap.trading.domain.RejectedOrder;

public interface RejectedOrderRepository extends JpaRepository<RejectedOrder, Integer> {
    @Query("select coalesce(max(r.rejectId), 0) from RejectedOrder r")
    Integer findMaxRejectId();
}
