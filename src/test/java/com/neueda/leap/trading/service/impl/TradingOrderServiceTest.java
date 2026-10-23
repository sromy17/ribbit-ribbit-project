package com.neueda.leap.trading.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neueda.leap.trading.api.dto.CreateOrderRequest;
import com.neueda.leap.trading.api.dto.CreateOrderResponse;
import com.neueda.leap.trading.api.dto.ProcessOrderRequest;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.domain.RejectedOrder;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import com.neueda.leap.trading.repository.jpa.OrderRepository;
import com.neueda.leap.trading.repository.jpa.RejectedOrderRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.mybatis.AccountOrderReadMapper;
import com.neueda.leap.trading.service.contract.FeeCalculator;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.MarketQuote;
import com.neueda.leap.trading.service.contract.TradingRulesService;

@ExtendWith(MockitoExtension.class)
class TradingOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RejectedOrderRepository rejectedOrderRepository;

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
    private TradingRulesService tradingRulesService;

    @Mock
    private AccountOrderReadMapper readMapper;

    private TradingOrderService tradingOrderService;

    @BeforeEach
    void setUp() {
        tradingOrderService = new TradingOrderService(
            orderRepository,
            rejectedOrderRepository,
            tradeRepository,
            accountRepository,
            instrumentRepository,
            holdingRepository,
            feeCalculator,
            marketDataVerificationService,
            new OrderValidator(),
            tradingRulesService,
            readMapper
        );
    }

    @Test
    // Mockito matchers return null placeholders; the repository is mocked.
    @SuppressWarnings("null")
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
        // BR-05: Mock trading rules validation to pass
        when(tradingRulesService.validateOrderSubmission(any(), any(), any(), any(), any()))
            .thenReturn(ValidationResult.valid());

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
    @SuppressWarnings("null") // Mockito matchers are null placeholders.
    void rejectedSubmissionPersistsReasonAndDoesNotExecute() {
        TradingAccount account = new TradingAccount();
        account.setAccountId(10);
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(20);
        instrument.setTicker("AAPL");
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));
        when(instrumentRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(instrument));
        when(marketDataVerificationService.verifyExecutionPrice(any(Order.class)))
            .thenReturn(new BigDecimal("12.50"));
        when(tradingRulesService.validateOrderSubmission(any(), any(), any(), any(), any()))
            .thenReturn(ValidationResult.invalid("Insufficient funds"));
        when(orderRepository.findMaxOrderId()).thenReturn(99);
        when(rejectedOrderRepository.findMaxRejectId()).thenReturn(4);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            assertEquals(OrderStatus.REJECTED, order.getStatus());
            return order;
        });

        var result = tradingOrderService.submitOrder(
            new ProcessOrderRequest(10, "AAPL", OrderSide.BUY, 5, null));

        assertEquals(OrderStatus.REJECTED, result.status());
        ArgumentCaptor<RejectedOrder> captor = ArgumentCaptor.forClass(RejectedOrder.class);
        verify(rejectedOrderRepository).save(captor.capture());
        assertEquals(100, captor.getValue().getRequestId());
        assertEquals("Insufficient funds", captor.getValue().getReason());
        verifyNoInteractions(tradeRepository);
    }

    @Test
    void cancelOrder_marksOrderAsCancelled() {
        Order order = new Order();
        order.setOrderId(44);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(44)).thenReturn(Objects.requireNonNull(Optional.of(order)));

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
        when(accountRepository.findById(10)).thenReturn(Objects.requireNonNull(Optional.empty()));

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

    @Test
    void ambiguousTickerRequiresStableId() {
        var account = new TradingAccount();
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));
        when(instrumentRepository.countByTickerIgnoreCase("ABC")).thenReturn(2L);
        var error = assertThrows(IllegalArgumentException.class, () ->
            tradingOrderService.createOrder(new CreateOrderRequest(10, "ABC", OrderSide.BUY, 1, BigDecimal.TEN)));
        assertEquals("Ambiguous ticker; supply an instrument ID", error.getMessage());
        verifyNoInteractions(marketDataVerificationService, tradeRepository);
    }

    @Test
    void stableIdSelectsQualifiedInstrumentEvenWhenTickerIsAmbiguous() {
        var account = new TradingAccount();
        account.setAccountId(10);
        var instrument = new Instrument();
        instrument.setInstrumentId(42);
        instrument.setTicker("ABC");
        instrument.setMarketDataSymbol("ABC.L");
        when(accountRepository.findById(10)).thenReturn(Optional.of(account));
        when(instrumentRepository.findById(42)).thenReturn(Optional.of(instrument));
        when(marketDataVerificationService.verifyExecutionPrice(any(Order.class))).thenReturn(BigDecimal.TEN);
        when(tradingRulesService.validateOrderSubmission(any(), any(), any(), any(), any()))
            .thenReturn(ValidationResult.valid());
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        var response = tradingOrderService.createOrder(
            new CreateOrderRequest(10, null, OrderSide.BUY, 4, BigDecimal.TEN, 42));
        assertEquals(42, response.instrumentId());
        assertEquals(OrderStatus.PENDING, response.status());
        org.mockito.Mockito.verify(instrumentRepository, org.mockito.Mockito.never())
            .findByTickerIgnoreCase(any());
    }

    @Test
    void executionRejectsKnownCurrencyMismatchBeforeChangingFunds() {
        var account = new TradingAccount();
        account.setCurrency("USD");
        account.setAvailableFunds(new BigDecimal("1000"));
        var instrument = new Instrument();
        instrument.setTicker("ABC");
        var order = new Order();
        order.setOrderId(1);
        order.setStatus(OrderStatus.PENDING);
        order.setAccount(account);
        order.setInstrument(instrument);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(marketDataVerificationService.verifyExecutionQuote(order)).thenReturn(
            new MarketQuote("ABC", BigDecimal.TEN, null, null, "feed", null, null, "GBP", null));
        assertThrows(IllegalArgumentException.class, () -> tradingOrderService.executeOrder(1));
        assertEquals(new BigDecimal("1000"), account.getAvailableFunds());
        verifyNoInteractions(tradeRepository, accountRepository, holdingRepository);
    }

    @Test
    void committedFillSnapshotsClientCurrencyAndProductMetadata() {
        var user = new User();
        user.setUserId(7);
        var account = new TradingAccount();
        account.setAccountId(10);
        account.setUser(user);
        account.setCurrency("GBP");
        account.setAvailableFunds(new BigDecimal("1000"));
        var instrument = new Instrument();
        instrument.setInstrumentId(20);
        instrument.setTicker("ABC");
        instrument.setQuoteCurrency("GBP");
        instrument.setQuantityUnit("SHARE");
        instrument.setContractMultiplier(BigDecimal.ONE);
        instrument.setProductConvention("PRICE_PER_UNIT");
        var order = new Order();
        order.setOrderId(1);
        order.setStatus(OrderStatus.PENDING);
        order.setSide(OrderSide.BUY);
        order.setQuantity(4);
        order.setAccount(account);
        order.setInstrument(instrument);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(marketDataVerificationService.verifyExecutionQuote(order)).thenReturn(
            new MarketQuote("ABC", new BigDecimal("100"), null, null, "feed", null, null, "GBP", "ASK"));
        when(feeCalculator.calculate(OrderSide.BUY, 4, new BigDecimal("100"))).thenReturn(BigDecimal.ONE);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(call -> call.getArgument(0));

        var result = tradingOrderService.executeOrder(1);
        assertEquals(OrderStatus.EXECUTED, result.status());
        assertEquals(new BigDecimal("599"), account.getAvailableFunds());
        var captor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository).save(captor.capture());
        var fill = captor.getValue();
        instrument.setQuoteCurrency("USD");
        instrument.setQuantityUnit("OTHER");
        assertEquals(7, fill.getClientId());
        assertEquals("GBP", fill.getExecutionCurrency());
        assertEquals("GBP", fill.getFeeCurrency());
        assertEquals("SHARE", fill.getQuantityUnit());
        assertEquals(BigDecimal.ONE, fill.getContractMultiplier());
        assertEquals("PRICE_PER_UNIT", fill.getProductConvention());
    }
}
