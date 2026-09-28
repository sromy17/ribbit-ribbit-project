package com.neueda.leap.trading.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.neueda.leap.trading.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByAccountAccountId(Integer accountId);

    @Query("select coalesce(max(o.orderId), 0) from Order o")
    Integer findMaxOrderId();
}
