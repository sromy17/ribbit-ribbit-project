package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
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
import com.neueda.leap.trading.domain.RejectedOrder;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import com.neueda.leap.trading.repository.jpa.OrderRepository;
import com.neueda.leap.trading.repository.jpa.RejectedOrderRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.mybatis.AccountOrderReadMapper;
import com.neueda.leap.trading.repository.mybatis.AccountOrderSummary;

@Service
public class TradingOrderService implements OrderService {
    private final OrderRepository orderRepository;
    private final RejectedOrderRepository rejectedOrderRepository;
    private final TradeRepository tradeRepository;
    private final TradingAccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final HoldingRepository holdingRepository;
    private final FeeCalculator feeCalculator;
    private final MarketDataVerificationService marketDataVerificationService;
    private final OrderValidator orderValidator;
    private final TradingRulesService tradingRulesService;
    private final AccountOrderReadMapper readMapper;

    public TradingOrderService(
        OrderRepository orderRepository,
        RejectedOrderRepository rejectedOrderRepository,
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
        this.orderRepository = orderRepository;
        this.rejectedOrderRepository = rejectedOrderRepository;
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.feeCalculator = feeCalculator;
        this.marketDataVerificationService = marketDataVerificationService;
        this.orderValidator = orderValidator;
        this.tradingRulesService = tradingRulesService;
        this.readMapper = readMapper;
    }

    @Override
    @Transactional
    public ProcessOrderResponse executeOrder(Integer orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be executed");
        }

        TradingAccount account = order.getAccount();
        Instrument instrument = order.getInstrument();
        BigDecimal executionPrice = marketDataVerificationService.verifyExecutionPrice(order);
        BigDecimal fee = feeCalculator.calculate(order.getSide(), order.getQuantity(), executionPrice);

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

        updateHolding(account, instrument, order.getSide(), order.getQuantity(), executionPrice);

        Trade trade = new Trade();
        trade.setTradeId(nextTradeId());
        trade.setOrder(order);
        trade.setExecutionPrice(executionPrice);
        trade.setExecutedQuantity(order.getQuantity());
        trade.setExecutedAt(Instant.now());
        trade = tradeRepository.save(trade);

        order.setStatus(OrderStatus.EXECUTED);
        order = orderRepository.save(order);

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
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        TradingAccount account = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.accountId()));

        Instrument instrument = resolveInstrument(request.ticker());

        Order order = new Order();
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setSide(request.side());
        order.setQuantity(request.quantity());

        BigDecimal marketPrice = marketDataVerificationService.verifyExecutionPrice(order);

        // BR-05: Validate trading rules BEFORE accepting the order
        ValidationResult validation = tradingRulesService.validateOrderSubmission(
            account,
            instrument,
            request.side(),
            request.quantity(),
            marketPrice
        );

        order.setOrderId(nextOrderId());
        order.setPrice(marketPrice);
        order.setDateRequested(Instant.now());

        // Set order status based on validation result
        if (!validation.isValid()) {
            order.setStatus(OrderStatus.REJECTED);
            order = orderRepository.save(order);
            saveRejection(order.getOrderId(), validation.getReason());
            return new CreateOrderResponse(
                order.getOrderId(),
                account.getAccountId(),
                instrument.getInstrumentId(),
                order.getStatus()
            );
        }

        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);

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
        if (created.status() == OrderStatus.REJECTED) {
            return new ProcessOrderResponse(
                created.orderId(),
                null,
                OrderStatus.REJECTED,
                null,
                null,
                null,
                null
            );
        }
        return executeOrder(created.orderId());
    }

    private void saveRejection(Integer orderId, String reason) {
        RejectedOrder rejection = new RejectedOrder();
        rejection.setRejectId(rejectedOrderRepository.findMaxRejectId() + 1);
        rejection.setRequestId(orderId);
        rejection.setReason(reason == null ? "Order rejected" : reason.substring(0, Math.min(reason.length(), 50)));
        rejection.setRejectDate(Instant.now());
        rejectedOrderRepository.save(rejection);
    }

    @Override
    @Transactional
    public void cancelOrder(Integer orderId) {
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

    private void updateHolding(
        TradingAccount account,
        Instrument instrument,
        OrderSide side,
        Integer quantity,
        BigDecimal executionPrice
    ) {
        Holding holding = holdingRepository
            .findByAccountAccountIdAndInstrumentInstrumentId(account.getAccountId(), instrument.getInstrumentId())
            .orElseGet(() -> {
                Holding newHolding = new Holding();
                newHolding.setAccount(account);
                newHolding.setInstrument(instrument);
                newHolding.setQuantity(0);
                newHolding.setAverageCost(BigDecimal.ZERO);
                return newHolding;
            });

        boolean isBuy = side == OrderSide.BUY;
        holding.updatePosition(isBuy, quantity, executionPrice);
        holding.setAsOfDate(Instant.now());
        holdingRepository.save(holding);
    }

    private Integer nextOrderId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            if (!orderRepository.existsById(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to allocate a unique order/request id");
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
