package com.neueda.leap.trading.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

        Instrument instrument = instrumentRepository.findById(request.instrumentId())
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.instrumentId()));

        Order order = new Order();
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
            request.instrumentId(),
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
                newHolding.setAverageCost(BigDecimal.ZERO);
                return newHolding;
            });

        boolean isBuy = side == OrderSide.BUY;
        holding.updatePosition(isBuy, quantity, executionPrice);
        holdingRepository.save(holding);
    }
}
