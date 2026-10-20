package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.neueda.leap.trading.service.contract.FeeCalculator;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.OrderService;
import com.neueda.leap.trading.service.contract.TradingRulesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.leap.trading.api.dto.CreateOrderRequest;
import com.neueda.leap.trading.api.dto.CreateOrderResponse;
import com.neueda.leap.trading.api.dto.ProcessOrderRequest;
import com.neueda.leap.trading.api.dto.ProcessOrderResponse;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.InstrumentStatus;
import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import com.neueda.leap.trading.repository.jpa.OrderRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.mybatis.AccountOrderReadMapper;
import com.neueda.leap.trading.repository.mybatis.AccountOrderSummary;
import com.neueda.leap.trading.service.impl.marketdata.KafkaOrderExecutionProducer;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class TradingOrderService implements OrderService {
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final TradingAccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final HoldingRepository holdingRepository;
    private final FeeCalculator feeCalculator;
    private final MarketDataVerificationService marketDataVerificationService;
    private final OrderValidator orderValidator;
    private final TradingRulesService tradingRulesService;
    private final AccountOrderReadMapper readMapper;
    private final KafkaOrderExecutionProducer executionProducer;
    private final OrderEvidenceService evidenceService;

    public TradingOrderService(
        OrderRepository orderRepository,
        TradeRepository tradeRepository,
        TradingAccountRepository accountRepository,
        InstrumentRepository instrumentRepository,
        HoldingRepository holdingRepository,
        FeeCalculator feeCalculator,
        MarketDataVerificationService marketDataVerificationService,
        OrderValidator orderValidator,
        TradingRulesService tradingRulesService,
        AccountOrderReadMapper readMapper
    ) {
        this(
            orderRepository,
            tradeRepository,
            accountRepository,
            instrumentRepository,
            holdingRepository,
            feeCalculator,
            marketDataVerificationService,
            orderValidator,
            tradingRulesService,
            readMapper,
            null,
            null
        );
    }

    @Autowired
    public TradingOrderService(
        OrderRepository orderRepository,
        TradeRepository tradeRepository,
        TradingAccountRepository accountRepository,
        InstrumentRepository instrumentRepository,
        HoldingRepository holdingRepository,
        FeeCalculator feeCalculator,
        MarketDataVerificationService marketDataVerificationService,
        OrderValidator orderValidator,
        TradingRulesService tradingRulesService,
        AccountOrderReadMapper readMapper,
        KafkaOrderExecutionProducer executionProducer,
        OrderEvidenceService evidenceService
    ) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.feeCalculator = feeCalculator;
        this.marketDataVerificationService = marketDataVerificationService;
        this.orderValidator = orderValidator;
        this.tradingRulesService = tradingRulesService;
        this.readMapper = readMapper;
        this.executionProducer = executionProducer;
        this.evidenceService = evidenceService;
    }

    @Override
    @Transactional
    public ProcessOrderResponse executeOrder(Integer orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be executed");
        }

        if (evidenceService != null) evidenceService.event(order, "EXECUTION_ATTEMPTED", null);

        TradingAccount account = order.getAccount();
        Instrument instrument = order.getInstrument();
        BigDecimal executionPrice = marketDataVerificationService.verifyExecutionPrice(order);
        BigDecimal fee = feeCalculator.calculate(order.getSide(), order.getQuantity(), executionPrice);
        if (evidenceService != null) evidenceService.quote(order, executionPrice, fee);

        ValidationResult validation = orderValidator.validate(
            order.getSide(),
            order.getQuantity(),
            executionPrice,
            account.getAvailableFunds(),
            fee
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getReason());
        }

        applyFunds(account, order.getSide(), executionPrice, order.getQuantity(), fee);
        accountRepository.save(account);

        Trade trade = new Trade();
        trade.setTradeId(nextTradeId());
        trade.setOrder(order);
        trade.setExecutionPrice(executionPrice);
        trade.setExecutedQuantity(order.getQuantity());
        trade.setExecutedAt(LocalDate.now());
        trade.setExecutedAtUtc(Instant.now());
        trade.setFee(fee);
        trade = tradeRepository.save(trade);

        order.setStatus(OrderStatus.EXECUTED);
        order = orderRepository.save(order);

        BigDecimal notional = executionPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        if (evidenceService != null) {
            evidenceService.cashMovement(order,
                order.getSide() == OrderSide.BUY ? notional.add(fee).negate() : notional.subtract(fee),
                order.getSide() == OrderSide.BUY ? "BUY_SETTLEMENT" : "SELL_SETTLEMENT");
        }
        int quantityAfter = updateHolding(account, instrument, order.getSide(), trade.getExecutedQuantity());
        if (evidenceService != null) {
            evidenceService.holdingMovement(order,
                order.getSide() == OrderSide.BUY ? trade.getExecutedQuantity() : -trade.getExecutedQuantity(),
                quantityAfter);
            evidenceService.fill(order, trade);
        }

        AccountOrderSummary summary = readMapper.getAccountOrderSummary(account.getAccountId());

        return new ProcessOrderResponse(
            order.getOrderId(),
            trade.getTradeId(),
            order.getStatus(),
            trade.getExecutedQuantity(),
            trade.getExecutionPrice(),
            fee,
            summary
        );
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        if (request == null || request.accountId() == null) {
            throw new IllegalArgumentException("Request and account ID cannot be null");
        }
        int accountId = request.accountId();
        TradingAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        Instrument instrument = resolveInstrument(request.ticker());

        // BR-05: Validate trading rules BEFORE accepting the order
        ValidationResult validation = tradingRulesService.validateOrderSubmission(
            account,
            instrument,
            request.side(),
            request.quantity(),
            request.price()
        );

        Order order = new Order();
        order.setOrderId(nextOrderId());
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setSide(request.side());
        order.setQuantity(request.quantity());
        order.setPrice(request.price());
        order.setDateRequested(LocalDate.now());
        order.setCreatedAtUtc(Instant.now());
        order = orderRepository.save(order);
        if (evidenceService != null) evidenceService.event(order, "ORDER_SUBMITTED", null);

        // Set order status based on validation result
        if (!validation.isValid()) {
            order.setStatus(OrderStatus.REJECTED);
            if (evidenceService != null) evidenceService.rejected(order, validation.getReason());
            throw new IllegalArgumentException("Order rejected: " + validation.getReason());
        }

        order.setStatus(OrderStatus.PENDING);
        if (evidenceService != null) evidenceService.event(order, "ORDER_ACCEPTED", null);

        return new CreateOrderResponse(
            order.getOrderId(),
            account.getAccountId(),
            instrument.getInstrumentId(),
            order.getStatus()
        );
    }

    @Override
    @Transactional
    public ProcessOrderResponse submitOrder(ProcessOrderRequest request) {
        CreateOrderResponse created = createOrder(new CreateOrderRequest(
            request.accountId(),
            request.ticker(),
            request.side(),
            request.quantity(),
            request.price()
        ));
        return executeOrder(created.orderId());
    }

    @Override
    @Transactional(readOnly = true)
    public void enqueueExecution(Integer orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        if (executionProducer == null) {
            throw new IllegalStateException("Asynchronous order execution is not configured");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be queued for execution");
        }
        executionProducer.enqueue(orderId);
    }

    @Override
    @Transactional
    public void cancelOrder(Integer orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.cancel();
        orderRepository.save(order);
    }

    @Override
    public List<Order> getOrders(Integer accountId) {
        return orderRepository.findByAccountAccountId(accountId);
    }

    private void applyFunds(TradingAccount account, OrderSide side, BigDecimal price, Integer quantity, BigDecimal fee) {
        BigDecimal notional = price.multiply(BigDecimal.valueOf(quantity));
        if (side == OrderSide.BUY) {
            account.withdrawFunds(notional.add(fee));
            return;
        }

        account.depositFunds(notional.subtract(fee));
    }

    private int updateHolding(
        TradingAccount account,
        Instrument instrument,
        OrderSide side,
        Integer quantity
    ) {
        Holding holding = holdingRepository
            .findByAccountAccountIdAndInstrumentInstrumentId(account.getAccountId(), instrument.getInstrumentId())
            .orElseGet(() -> {
                Holding newHolding = new Holding();
                newHolding.setAccount(account);
                newHolding.setInstrument(instrument);
                newHolding.setQuantity(0);
                return newHolding;
            });

        boolean isBuy = side == OrderSide.BUY;
        holding.updatePosition(isBuy, quantity);
        if (holding.getQuantity() == 0) {
            holdingRepository.delete(holding);
            return 0;
        }

        holding.setAsOfDate(LocalDate.now());
        holdingRepository.save(holding);
        return holding.getQuantity();
    }

    private Integer nextOrderId() {
        return orderRepository.findMaxOrderId() + 1;
    }

    private Integer nextTradeId() {
        return tradeRepository.findMaxTradeId() + 1;
    }

    private Instrument resolveInstrument(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }

        String normalizedTicker = ticker.trim().toUpperCase();
        return instrumentRepository.findByTickerIgnoreCase(normalizedTicker)
            .orElseGet(() -> createInstrumentFromTicker(normalizedTicker));
    }

    private Instrument createInstrumentFromTicker(String ticker) {
        if (!marketDataVerificationService.isTickerValid(ticker)) {
            throw new IllegalArgumentException("Instrument not found in Fauxnance: " + ticker);
        }

        Instrument instrument = new Instrument();
        instrument.setInstrumentId(generateInstrumentId());
        instrument.setTicker(ticker);
        instrument.setInstrumentName(ticker);
        instrument.setStatus(InstrumentStatus.ACTIVE);
        return instrumentRepository.save(instrument);
    }

    private int generateInstrumentId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
            if (!instrumentRepository.existsById(candidate)) {
                return candidate;
            }
        }

        throw new IllegalArgumentException("Unable to allocate instrument id");
    }
}
