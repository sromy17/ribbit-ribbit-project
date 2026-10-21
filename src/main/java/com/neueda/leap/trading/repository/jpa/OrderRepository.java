package com.neueda.leap.trading.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.neueda.leap.trading.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    @Query("select o from Order o join fetch o.instrument where o.account.accountId = :accountId order by o.dateRequested desc")
    List<Order> findByAccountAccountId(@Param("accountId") Integer accountId);

    @Query("select coalesce(max(o.orderId), 0) from Order o")
    Integer findMaxOrderId();
}
