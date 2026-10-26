package com.neueda.leap.trading.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import com.neueda.leap.trading.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :id")
    java.util.Optional<Order> findForUpdate(@Param("id") Integer id);

    @Query("select o from Order o join fetch o.instrument where o.account.accountId = :accountId order by o.dateRequested desc")
    List<Order> findByAccountAccountId(@Param("accountId") Integer accountId);

}
