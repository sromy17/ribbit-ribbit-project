package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;


import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.neueda.leap.trading.service.contract.FeeCalculator;
import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.MarketQuote;
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
import com.neueda.leap.trading.service.impl.marketdata.KafkaOrderExecutionProducer;
import org.springframework.beans.factory.annotation.Autowired;

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
    private final KafkaOrderExecutionProducer executionProducer;
    private final OrderEvidenceService evidenceService;
    private final ExecutionAttemptEvidenceService executionAttemptEvidenceService;

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
        this(
            orderRepository,
            rejectedOrderRepository,
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
            null,
            null
        );
    }

    @Autowired
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
        AccountOrderReadMapper readMapper,
        KafkaOrderExecutionProducer executionProducer,
        OrderEvidenceService evidenceService,
        ExecutionAttemptEvidenceService executionAttemptEvidenceService
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
        this.executionProducer = executionProducer;
        this.evidenceService = evidenceService;
        this.executionAttemptEvidenceService = executionAttemptEvidenceService;
    }

    @Override
    public ProcessOrderResponse executeOrder(Integer orderId) {
        TradingOrderService transactions=transactionalSelf == null ? this : transactionalSelf;
        try { return transactions.executeCommittedOrder(orderId); }
        catch(RuntimeException failure) {
            if(executionAttemptEvidenceService!=null) {
                try { executionAttemptEvidenceService.failed(orderId,failure instanceof IllegalArgumentException ? failure.getMessage() : failure.getClass().getSimpleName()); }
                catch(RuntimeException recordingFailure) { failure.addSuppressed(recordingFailure); }
            }
            throw failure;
        }
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ProcessOrderResponse executeCommittedOrder(Integer orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        Order order = orderRepository.findForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be executed");
        }

        if (executionAttemptEvidenceService != null) {
            executionAttemptEvidenceService.record(
                order.getOrderId(), order.getAccount().getAccountId(),
                order.getInstrument().getInstrumentId(), order.getCorrelationId(), order.getQuantity());
        }

        TradingAccount account = accountRepository.findForUpdate(order.getAccount().getAccountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        order.setAccount(account);
        Instrument instrument = order.getInstrument();
        validateMarketDataIdentity(instrument);
        MarketQuote quote = marketDataVerificationService.verifyExecutionQuote(order);
        BigDecimal executionPrice = quote.price();
        validateExecutionMetadata(account, instrument, quote);
        BigDecimal fee = feeCalculator.calculate(order.getSide(), order.getQuantity(), executionPrice);
        Long pricingEventId = evidenceService == null ? null : evidenceService.quote(order, quote, fee);

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
        trade.setExecutedAt(LocalDate.now(java.time.ZoneOffset.UTC));
        trade.setExecutedAtUtc(Instant.now());
        trade.setFee(fee);
        trade.setExecutionCurrency(quote.currency() == null ? instrument.getQuoteCurrency() : quote.currency());
        trade.setFeeCurrency(account.getCurrency());
        trade.setQuantityUnit(instrument.getQuantityUnit());
        trade.setContractMultiplier(instrument.getContractMultiplier());
        trade.setProductConvention(instrument.getProductConvention());
        trade.setPricingEventId(pricingEventId);
        trade.setClientId(account.getUser() == null ? null : account.getUser().getUserId());
        trade = tradeRepository.save(trade);

        order.setStatus(OrderStatus.EXECUTED);
        order = orderRepository.save(order);

        BigDecimal notional = executionPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        if (evidenceService != null) {
            evidenceService.cashMovement(order,
                order.getSide() == OrderSide.BUY ? notional.add(fee).negate() : notional.subtract(fee),
                order.getSide() == OrderSide.BUY ? "BUY_SETTLEMENT" : "SELL_SETTLEMENT");
        }
        int quantityAfter = updateHolding(account, instrument, order.getSide(), trade.getExecutedQuantity(), executionPrice);
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

        Instrument instrument = resolveInstrument(request.instrumentId(), request.ticker());
        validateMarketDataIdentity(instrument);

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
        order.setDateRequested(LocalDate.now(java.time.ZoneOffset.UTC));
        order.setCreatedAtUtc(Instant.now());
        order.setStatus(validation.isValid() ? OrderStatus.PENDING : OrderStatus.REJECTED);
        order = orderRepository.save(order);
        if (evidenceService != null) evidenceService.event(order, "ORDER_SUBMITTED", null);

        // Set order status based on validation result
        if (!validation.isValid()) {
            order.setStatus(OrderStatus.REJECTED);
            if (evidenceService != null) evidenceService.rejected(order, validation.getReason());
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
        if (evidenceService != null) evidenceService.event(order, "ORDER_ACCEPTED", null);

        return new CreateOrderResponse(
            order.getOrderId(),
            account.getAccountId(),
            instrument.getInstrumentId(),
            order.getStatus()
        );
    }

    private FinancialIdAllocator idAllocator;
    @Autowired
    void setIdAllocator(FinancialIdAllocator allocator) { this.idAllocator=allocator; }

    private TradingOrderService transactionalSelf;

    @Autowired
    void setTransactionalSelf(@org.springframework.context.annotation.Lazy TradingOrderService transactions) {
        this.transactionalSelf = transactions;
    }

    @Override
    public ProcessOrderResponse submitOrder(ProcessOrderRequest request) {
        // Commit accepted intent before execution and its independently committed attempt evidence.
        // A direct instance is supported by the isolated unit tests; Spring supplies the proxy at runtime.
        TradingOrderService transactions = transactionalSelf == null ? this : transactionalSelf;
        CreateOrderResponse created = transactions.createOrder(new CreateOrderRequest(
            request.accountId(),
            request.ticker(),
            request.side(),
            request.quantity(),
            request.price(),
            request.instrumentId()
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
        ProcessOrderResponse result = transactions.executeOrder(created.orderId());
        // MyBatis reads use a separate connection: refresh only after execution commits.
        return new ProcessOrderResponse(result.orderId(), result.tradeId(), result.status(),
            result.executedQuantity(), result.executionPrice(), result.fee(),
            readMapper.getAccountOrderSummary(request.accountId()));
    }

    private void saveRejection(Integer orderId, String reason) {
        RejectedOrder rejection = new RejectedOrder();
        rejection.setRejectId(idAllocator == null ? rejectedOrderRepository.findMaxRejectId() + 1 : idAllocator.next("REJECTION"));
        rejection.setRequestId(orderId);
        rejection.setReason(reason == null ? "Order rejected" : reason.substring(0, Math.min(reason.length(), 50)));
        rejection.setRejectDate(Instant.now());
        rejectedOrderRepository.save(rejection);
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
        Order order = orderRepository.findForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) return;
        if (order.getStatus() != OrderStatus.PENDING) throw new IllegalArgumentException("Only pending orders can be cancelled");
        order.cancel();
        orderRepository.save(order);
        if (evidenceService != null) {
            evidenceService.event(order, "ORDER_CANCELLED", "Cancelled by account owner");
        }
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

    private void validateExecutionMetadata(TradingAccount account, Instrument instrument, MarketQuote quote) {
        if (quote.currency() != null && instrument.getQuoteCurrency() != null
                && !quote.currency().equals(instrument.getQuoteCurrency())) {
            throw new IllegalArgumentException("Quote currency does not match instrument metadata");
        }
        String priceCurrency = quote.currency() == null ? instrument.getQuoteCurrency() : quote.currency();
        if (instrument.getSettlementCurrency() != null && priceCurrency != null
                && !instrument.getSettlementCurrency().equals(priceCurrency)) {
            throw new IllegalArgumentException("Cross-currency settlement is not supported");
        }
        String settlementCurrency = instrument.getSettlementCurrency() == null
            ? priceCurrency : instrument.getSettlementCurrency();
        if (account.getCurrency() != null && settlementCurrency != null
                && !account.getCurrency().equals(settlementCurrency)) {
            throw new IllegalArgumentException("Account currency does not match execution currency");
        }
        if (instrument.getContractMultiplier() != null
                && instrument.getContractMultiplier().compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Execution of non-unit contract multipliers is not supported");
        }
        if (instrument.getProductConvention() != null
                && !"PRICE_PER_UNIT".equals(instrument.getProductConvention())) {
            throw new IllegalArgumentException("Execution product convention is not supported");
        }
    }

    private void validateMarketDataIdentity(Instrument instrument) {
        if ((instrument.getMarketDataSymbol() == null || instrument.getMarketDataSymbol().isBlank())
                && instrumentRepository.countByTickerIgnoreCase(instrument.getTicker()) > 1) {
            throw new IllegalArgumentException("Ambiguous market-data symbol; configure a qualified provider symbol for instrument "
                + instrument.getInstrumentId());
        }
    }

    private int updateHolding(
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
        if (isBuy) {
            BigDecimal priorCost = holding.getAverageCost() == null ? BigDecimal.ZERO : holding.getAverageCost();
            holding.setAverageCost(priorCost.multiply(BigDecimal.valueOf(holding.getQuantity()))
                .add(executionPrice.multiply(BigDecimal.valueOf(quantity)))
                .divide(BigDecimal.valueOf((long) holding.getQuantity() + quantity), 10, java.math.RoundingMode.HALF_EVEN));
        }
        holding.updatePosition(isBuy, quantity);
        if (holding.getQuantity() == 0) {
            holdingRepository.delete(holding);
            return 0;
        }

        holding.setAsOfDate(LocalDate.now(java.time.ZoneOffset.UTC));
        holdingRepository.save(holding);
        return holding.getQuantity();
    }

    private Integer nextOrderId() {
        if (idAllocator != null) {
            return idAllocator.next("ORDER");
        }

        for (int attempt = 0; attempt < 20; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            if (!orderRepository.existsById(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to allocate a unique order/request id");

    }

    private Integer nextTradeId() {
        return idAllocator == null ? tradeRepository.findMaxTradeId() + 1 : idAllocator.next("TRADE");
    }

    private Instrument resolveInstrument(Integer instrumentId, String ticker) {
        if (instrumentId != null) {
            Instrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + instrumentId));
            if (ticker != null && !ticker.isBlank() && !ticker.trim().equalsIgnoreCase(instrument.getTicker())) {
                throw new IllegalArgumentException("Instrument ID and ticker do not match");
            }
            return instrument;
        }
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("Ticker is required");
        }

        String normalizedTicker = ticker.trim().toUpperCase();
        if (instrumentRepository.countByTickerIgnoreCase(normalizedTicker) > 1) {
            throw new IllegalArgumentException("Ambiguous ticker; supply an instrument ID");
        }
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
