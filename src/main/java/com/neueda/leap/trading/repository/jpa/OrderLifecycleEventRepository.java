package com.neueda.leap.trading.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.neueda.leap.trading.domain.OrderLifecycleEvent;

public interface OrderLifecycleEventRepository extends JpaRepository<OrderLifecycleEvent, Long> {
    @Query("select coalesce(max(e.eventSequence), 0) from OrderLifecycleEvent e where e.orderId = :orderId")
    Long findMaxSequence(Integer orderId);
}