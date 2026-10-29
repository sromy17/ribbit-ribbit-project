package com.neueda.leap.trading.service.impl;

import java.time.Instant;

import com.neueda.leap.trading.domain.OrderLifecycleEvent;
import com.neueda.leap.trading.repository.jpa.OrderLifecycleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionAttemptEvidenceService {
    private final OrderLifecycleEventRepository repository;
    private final com.neueda.leap.trading.repository.jpa.OrderRepository orders;

    public ExecutionAttemptEvidenceService(OrderLifecycleEventRepository repository, com.neueda.leap.trading.repository.jpa.OrderRepository orders) {
        this.repository = repository;
        this.orders = orders;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Integer orderId, Integer accountId, Integer instrumentId, String correlationId, Integer quantity) {
        OrderLifecycleEvent event = new OrderLifecycleEvent();
        event.setOrderId(orderId);
        event.setAccountId(accountId);
        event.setInstrumentId(instrumentId);
        event.setEventType("EXECUTION_ATTEMPTED");
        event.setEventSequence(repository.findMaxSequence(orderId) + 1);
        event.setOccurredAtUtc(Instant.now());
        event.setCorrelationId(correlationId);
        event.setQuantity(quantity);
        repository.save(event);
    }
    /** Called by the orchestration layer only after the execution transaction has rolled back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(Integer orderId,String reason) {
        if(orderId==null) return;
        var order=orders.findForUpdate(orderId).orElse(null);
        if(order==null || order.getStatus()!=com.neueda.leap.trading.domain.OrderStatus.PENDING) return;
        var event=new OrderLifecycleEvent();
        event.setOrderId(orderId);event.setAccountId(order.getAccount().getAccountId());
        event.setInstrumentId(order.getInstrument().getInstrumentId());event.setCorrelationId(order.getCorrelationId());
        event.setEventType("EXECUTION_FAILED");event.setEventSequence(repository.findMaxSequence(orderId)+1);
        event.setOccurredAtUtc(Instant.now());
        event.setReason(reason==null?"Execution failed":reason.substring(0,Math.min(reason.length(),500)));
        repository.save(event);
    }

}