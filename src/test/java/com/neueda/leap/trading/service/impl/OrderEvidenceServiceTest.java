package com.neueda.leap.trading.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.trading.domain.*;
import com.neueda.leap.trading.repository.jpa.*;
import com.neueda.leap.trading.service.contract.MarketQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderEvidenceServiceTest {
    private final OrderLifecycleEventRepository events = mock(OrderLifecycleEventRepository.class);
    private final CashMovementRepository cash = mock(CashMovementRepository.class);
    private final HoldingMovementRepository holdings = mock(HoldingMovementRepository.class);
    private final OrderEvidenceService service = new OrderEvidenceService(events, cash, holdings);
    private Order order;

    @BeforeEach
    void setup() {
        var account = new TradingAccount();
        account.setAccountId(10);
        var instrument = new Instrument();
        instrument.setInstrumentId(20);
        order = new Order();
        order.setOrderId(30);
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setQuantity(4);
        when(events.save(any(OrderLifecycleEvent.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void pricingPreservesActualQuoteEvidenceAndFillDoesNotInventAnotherQuote() {
        Instant upstream = Instant.parse("2026-10-23T12:00:00Z");
        Instant received = upstream.plusSeconds(2);
        service.quote(order, new MarketQuote("ABC.L", new BigDecimal("100"), null, null,
            "actual-feed", upstream, received, "GBP", "ASK"), BigDecimal.ONE);
        var trade = new Trade();
        trade.setExecutionPrice(new BigDecimal("100"));
        trade.setExecutedAtUtc(received.plusSeconds(1));
        service.fill(order, trade);

        var captor = ArgumentCaptor.forClass(OrderLifecycleEvent.class);
        verify(events, times(2)).save(captor.capture());
        var pricing = captor.getAllValues().get(0);
        assertEquals("actual-feed", pricing.getQuoteSource());
        assertEquals(upstream, pricing.getQuoteAtUtc());
        assertEquals(received, pricing.getQuoteReceivedAtUtc());
        assertEquals("GBP", pricing.getQuoteCurrency());
        assertEquals("ASK", pricing.getQuotePriceType());
        var fill = captor.getAllValues().get(1);
        assertNull(fill.getQuoteSource());
        assertNull(fill.getQuoteAtUtc());
        assertEquals(trade.getExecutionPrice(), fill.getDecisionPrice());
    }

    @Test
    void absentUpstreamFieldsRemainUnknownAndCashDoesNotDefaultToUsd() {
        service.quote(order, new MarketQuote("ABC", BigDecimal.TEN, null, null), BigDecimal.ZERO);
        service.cashMovement(order, BigDecimal.TEN.negate(), "BUY_SETTLEMENT");
        var event = ArgumentCaptor.forClass(OrderLifecycleEvent.class);
        verify(events).save(event.capture());
        assertNull(event.getValue().getQuoteAtUtc());
        assertNull(event.getValue().getQuoteReceivedAtUtc());
        assertNull(event.getValue().getQuoteCurrency());
        var movement = ArgumentCaptor.forClass(CashMovement.class);
        verify(cash).save(movement.capture());
        assertNull(movement.getValue().getCurrency());
        assertNull(movement.getValue().getCurrencySource());
    }

    @Test
    void cashUsesActualAccountCurrency() {
        order.getAccount().setCurrency("INR");
        service.cashMovement(order, BigDecimal.TEN, "SELL_SETTLEMENT");
        var movement = ArgumentCaptor.forClass(CashMovement.class);
        verify(cash).save(movement.capture());
        assertEquals("INR", movement.getValue().getCurrency());
        assertEquals("ACCOUNT_METADATA", movement.getValue().getCurrencySource());
        assertEquals(BigDecimal.TEN, movement.getValue().getAmount());
    }
}
