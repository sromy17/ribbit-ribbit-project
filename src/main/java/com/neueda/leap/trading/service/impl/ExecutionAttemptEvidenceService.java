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

    public ExecutionAttemptEvidenceService(OrderLifecycleEventRepository repository) {
        this.repository = repository;
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
}