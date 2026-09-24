package com.neueda.leap.trading.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.neueda.leap.trading.api.ProcessOrderRequest;
import com.neueda.leap.trading.api.ProcessOrderResponse;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.TradingAccount;
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
    private final FeeCalculator feeCalculator;
    private final OrderValidator orderValidator;
    private final AccountOrderReadMapper readMapper;

    public TradingOrderService(
        OrderRepository orderRepository,
        TradeRepository tradeRepository,
        TradingAccountRepository accountRepository,
        InstrumentRepository instrumentRepository,
        FeeCalculator feeCalculator,
        OrderValidator orderValidator,
        AccountOrderReadMapper readMapper
    ) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.accountRepository = accountRepository;
        this.instrumentRepository = instrumentRepository;
        this.feeCalculator = feeCalculator;
        this.orderValidator = orderValidator;
        this.readMapper = readMapper;
    }

    @Override
    @Transactional
    public ProcessOrderResponse submitOrder(ProcessOrderRequest request) {
        TradingAccount account = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + request.accountId()));

        Instrument instrument = instrumentRepository.findById(request.instrumentId())
            .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.instrumentId()));

        BigDecimal fee = feeCalculator.calculate(request.side(), request.quantity(), request.price());
        ValidationResult validation = orderValidator.validate(
            request.side(),
            request.quantity(),
            request.price(),
            account.getAvailableFunds(),
            fee
        );
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.getReason());
        }

        Order order = new Order(); //use constructor instead of setters?
        order.setAccount(account);
        order.setInstrument(instrument);
        order.setSide(request.side());
        order.setQuantity(request.quantity());
        order.setPrice(request.price());
        order.setDateRequested(LocalDate.now());
        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);

        applyFunds(account, request.side(), request.price(), request.quantity(), fee);
        accountRepository.save(account);

        Trade trade = new Trade(); //use constructor instead of setters?
        trade.setOrder(order);
        trade.setExecutionPrice(request.price());
        trade.setExecutedQuantity(request.quantity());
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
}
