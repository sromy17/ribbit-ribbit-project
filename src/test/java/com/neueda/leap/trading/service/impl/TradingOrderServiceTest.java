package com.neueda.leap.trading.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neueda.leap.trading.api.CreateOrderRequest;
import com.neueda.leap.trading.api.CreateOrderResponse;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import com.neueda.leap.trading.repository.jpa.OrderRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.mybatis.AccountOrderReadMapper;
import com.neueda.leap.trading.service.contract.FeeCalculator;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;

@ExtendWith(MockitoExtension.class)
class TradingOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradingAccountRepository accountRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private MarketDataVerificationService marketDataVerificationService;

    @Mock
    private AccountOrderReadMapper readMapper;

    private TradingOrderService tradingOrderService;

    @BeforeEach
    void setUp() {
        tradingOrderService = new TradingOrderService(
            orderRepository,
            tradeRepository,
            accountRepository,
            instrumentRepository,
            holdingRepository,
            feeCalculator,
            marketDataVerificationService,
            new OrderValidator(),
            readMapper
        );
    }

    @Test
    void createOrder_createsPendingOrder() {
        TradingAccount account = new TradingAccount();
        account.setAccountId(10);

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(20);
        instrument.setTicker("AAPL");

        when(accountRepository.findById(10)).thenReturn(Optional.of(account));
        when(instrumentRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(instrument));
        when(orderRepository.findMaxOrderId()).thenReturn(99);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderRequest request = new CreateOrderRequest(
            10,
            "AAPL",
            OrderSide.BUY,
            5,
            new BigDecimal("12.50")
        );

        CreateOrderResponse response = tradingOrderService.createOrder(request);

        assertEquals(100, response.orderId());
        assertEquals(10, response.accountId());
        assertEquals(20, response.instrumentId());
        assertEquals(OrderStatus.PENDING, response.status());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void cancelOrder_marksOrderAsCancelled() {
        Order order = new Order();
        order.setOrderId(44);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(44)).thenReturn(Optional.of(order));

        tradingOrderService.cancelOrder(44);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void getOrders_returnsRepositoryResult() {
        Order order = new Order();
        order.setOrderId(7);
        List<Order> expected = List.of(order);

        when(orderRepository.findByAccountAccountId(10)).thenReturn(expected);

        List<Order> result = tradingOrderService.getOrders(10);

        assertSame(expected, result);
    }

    @Test
    void createOrder_throwsWhenAccountMissing() {
        when(accountRepository.findById(10)).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
            10,
            "AAPL",
            OrderSide.BUY,
            5,
            new BigDecimal("12.50")
        );

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> tradingOrderService.createOrder(request)
        );

        assertEquals("Account not found: 10", ex.getMessage());
    }
}
