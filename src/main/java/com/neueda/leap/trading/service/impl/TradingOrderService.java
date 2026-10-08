package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.neueda.leap.trading.service.contract.FeeCalculator;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.leap.trading.api.CreateOrderRequest;
import com.neueda.leap.trading.api.CreateOrderResponse;
import com.neueda.leap.trading.api.ProcessOrderRequest;
import com.neueda.leap.trading.api.ProcessOrderResponse;
import com.neueda.leap.trading.domain.Instrument;
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
    private final AccountOrderReadMapper readMapper;

    public TradingOrderService(
        OrderRepository orderRepository,
        TradeRepository tradeRepository,
        TradingAccountRepository accountRepository,
        InstrumentRepository instrumentRepository,
        HoldingRepository holdingRepository,
        FeeCalculator feeCalculator,
        MarketDataVerificationService marketDataVerificationService,
        OrderValidator orderValidator,
        AccountOrderReadMapper readMapper
    ) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.feeCalculator = feeCalculator;
        this.marketDataVerificationService = marketDataVerificationService;
        this.orderValidator = orderValidator;
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
        trade.setExecutedAt(LocalDate.now());
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

        Instrument instrument = instrumentRepository.findByTickerIgnoreCase(request.ticker())
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.ticker()));

        Order order = new Order();
        order.setOrderId(nextOrderId());
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setSide(request.side());
        order.setQuantity(request.quantity());
        order.setPrice(request.price());
        order.setDateRequested(LocalDate.now());
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
        return executeOrder(created.orderId());
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
                return newHolding;
            });

        boolean isBuy = side == OrderSide.BUY;
        int deltaQty = isBuy ? quantity : -quantity;
        holding.setQuantity(holding.getQuantity() + deltaQty);
        holding.setAsOfDate(LocalDate.now());
        holdingRepository.save(holding);
    }

    private Integer nextOrderId() {
        return orderRepository.findMaxOrderId() + 1;
    }

    private Integer nextTradeId() {
        return tradeRepository.findMaxTradeId() + 1;
    }

    /**
     * Recalculates all holdings for a given account by scanning all executed trades.
     * This method:
     * - Queries all executed trades for the account
     * - Groups trades by instrument
     * - Calculates net quantity (BUY quantities positive, SELL quantities negative)
     * - Creates or updates holdings records
     * 
     * Useful for initial data load, rebuilding out-of-sync holdings, or ETL.
     */
    @Transactional
    public void recalculateHoldingsForAccount(Integer accountId) {
        TradingAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        // Get all executed trades for this account
        List<Trade> trades = tradeRepository.findByOrderAccountAccountId(accountId);

        // Group trades by instrument and calculate net quantity
        java.util.Map<Instrument, Integer> instrumentQuantities = new java.util.HashMap<>();

        for (Trade trade : trades) {
            Instrument instrument = trade.getInstrument();
            int quantity = trade.getExecutedQuantity();
            
            // Get the side from the order to determine if it's a BUY or SELL
            OrderSide side = trade.getOrder().getSide();
            
            // Add or subtract quantity based on order side
            if (side == OrderSide.BUY) {
                instrumentQuantities.put(instrument, instrumentQuantities.getOrDefault(instrument, 0) + quantity);
            } else {
                instrumentQuantities.put(instrument, instrumentQuantities.getOrDefault(instrument, 0) - quantity);
            }
        }

        // Create or update holdings for each instrument
        LocalDate today = LocalDate.now();
        for (java.util.Map.Entry<Instrument, Integer> entry : instrumentQuantities.entrySet()) {
            Instrument instrument = entry.getKey();
            Integer netQuantity = entry.getValue();

            // Only create holding if quantity is positive
            if (netQuantity > 0) {
                Holding holding = holdingRepository
                    .findByAccountAccountIdAndInstrumentInstrumentId(accountId, instrument.getInstrumentId())
                    .orElseGet(() -> {
                        Holding newHolding = new Holding();
                        newHolding.setAccount(account);
                        newHolding.setInstrument(instrument);
                        return newHolding;
                    });

                holding.setQuantity(netQuantity);
                holding.setAsOfDate(today);
                holdingRepository.save(holding);
            } else if (netQuantity <= 0) {
                // Delete holding if quantity drops to zero or goes negative (shouldn't happen)
                holdingRepository.deleteByAccountAccountIdAndInstrumentInstrumentId(accountId, instrument.getInstrumentId());
            }
        }
    }

    /**
     * Recalculates holdings for ALL accounts by scanning all executed trades.
     * This is the batch operation used by ETL to keep OLTP holdings in sync.
     */
    @Transactional
    public void recalculateHoldingsForAllAccounts() {
        List<TradingAccount> allAccounts = accountRepository.findAll();
        for (TradingAccount account : allAccounts) {
            recalculateHoldingsForAccount(account.getAccountId());
        }
    }
}
