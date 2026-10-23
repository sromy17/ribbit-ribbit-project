package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.trading.service.contract.MarketQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.neueda.leap.trading.domain.CashMovement;
import com.neueda.leap.trading.domain.HoldingMovement;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderLifecycleEvent;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.repository.jpa.CashMovementRepository;
import com.neueda.leap.trading.repository.jpa.HoldingMovementRepository;
import com.neueda.leap.trading.repository.jpa.OrderLifecycleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderEvidenceService {
    private static final Logger log = LoggerFactory.getLogger(OrderEvidenceService.class);
    private final OrderLifecycleEventRepository lifecycleRepository;
    private final CashMovementRepository cashMovementRepository;
    private final HoldingMovementRepository holdingMovementRepository;

    public OrderEvidenceService(
        OrderLifecycleEventRepository lifecycleRepository,
        CashMovementRepository cashMovementRepository,
        HoldingMovementRepository holdingMovementRepository
    ) {
        this.lifecycleRepository = lifecycleRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.holdingMovementRepository = holdingMovementRepository;
    }

    public void event(Order order, String type, String reason) {
        event(order, type, reason, null, null, null, null, null);
    }

    public Long quote(Order order, MarketQuote quote, BigDecimal fee) {
        OrderLifecycleEvent event = newEvent(order, "PRICING_DECISION", null);
        event.setQuoteSource(quote.source());
        event.setQuoteAtUtc(quote.quoteAtUtc());
        event.setQuoteReceivedAtUtc(quote.receivedAtUtc());
        event.setQuoteCurrency(quote.currency());
        event.setQuotePriceType(quote.priceType());
        event.setQuotePrice(quote.price());
        event.setDecisionPrice(quote.price());
        event.setFee(fee);
        if (quote.source() == null || quote.quoteAtUtc() == null || quote.currency() == null) {
            log.warn("Incomplete upstream quote evidence for order {}", order.getOrderId());
        }
        return lifecycleRepository.save(event).getEventId();
    }

    public void fill(Order order, Trade trade) {
        event(order, "FILL_COMMITTED", null, null, null, null,
            trade.getExecutionPrice(), trade.getFee());
    }

    public void cashMovement(Order order, BigDecimal amount, String type) {
        CashMovement movement = new CashMovement();
        movement.setOrderId(order.getOrderId());
        movement.setAccountId(order.getAccount().getAccountId());
        movement.setAmount(amount);
        movement.setCurrency(order.getAccount().getCurrency());
        movement.setCurrencySource(order.getAccount().getCurrency() == null ? null : "ACCOUNT_METADATA");
        if (order.getAccount().getCurrency() == null) {
            log.warn("Cash movement currency unavailable for order {}", order.getOrderId());
        }
        movement.setMovementType(type);
        movement.setOccurredAtUtc(Instant.now());
        movement.setCorrelationId(order.getCorrelationId());
        cashMovementRepository.save(movement);
    }

    public void holdingMovement(Order order, int quantityDelta, int quantityAfter) {
        HoldingMovement movement = new HoldingMovement();
        movement.setOrderId(order.getOrderId());
        movement.setAccountId(order.getAccount().getAccountId());
        movement.setInstrumentId(order.getInstrument().getInstrumentId());
        movement.setQuantityDelta(quantityDelta);
        movement.setQuantityAfter(quantityAfter);
        movement.setOccurredAtUtc(Instant.now());
        movement.setCorrelationId(order.getCorrelationId());
        holdingMovementRepository.save(movement);
    }

    @Transactional
    public void rejected(Order order, String reason) {
        event(order, "VALIDATION_REJECTED", reason);
    }

    private void event(
        Order order,
        String type,
        String reason,
        String quoteSource,
        Instant quoteAt,
        BigDecimal quotePrice,
        BigDecimal decisionPrice,
        BigDecimal fee
    ) {
        OrderLifecycleEvent event = newEvent(order, type, reason);
        event.setQuoteSource(quoteSource);
        event.setQuoteAtUtc(quoteAt);
        event.setQuotePrice(quotePrice);
        event.setDecisionPrice(decisionPrice);
        event.setFee(fee);
        lifecycleRepository.save(event);
    }

    private OrderLifecycleEvent newEvent(Order order, String type, String reason) {
        OrderLifecycleEvent event = new OrderLifecycleEvent();
        event.setOrderId(order.getOrderId());
        event.setAccountId(order.getAccount().getAccountId());
        event.setInstrumentId(order.getInstrument() == null ? null : order.getInstrument().getInstrumentId());
        event.setEventType(type);
        event.setEventSequence(lifecycleRepository.findMaxSequence(order.getOrderId()) + 1);
        event.setOccurredAtUtc(Instant.now());
        event.setCorrelationId(order.getCorrelationId());
        event.setReason(reason);
        event.setQuantity(order.getQuantity());
        return event;
    }
}