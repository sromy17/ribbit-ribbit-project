package com.neueda.leap.trading.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.InstrumentStatus;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.service.contract.FeeCalculator;

/**
 * Unit tests for DefaultTradingRulesService.
 * Validates BR-05: Every order must be checked against the firm's trading rules before it is accepted.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Trading Rules Service - BR-05 Validation Tests")
public class DefaultTradingRulesServiceTest {

    private DefaultTradingRulesService tradingRulesService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private HoldingRepository holdingRepository;

    @BeforeEach
    void setUp() {
        tradingRulesService = new DefaultTradingRulesService(
            orderValidator,
            feeCalculator,
            holdingRepository
        );
    }

    // ==================== Account Validation Tests ====================

    @Test
    @DisplayName("Should reject order for null account")
    void testValidateOrderSubmission_NullAccount() {

        ValidationResult result = tradingRulesService.validateAccountStatus(null);

        assertFalse(result.isValid());
        assertEquals("Account not found", result.getReason());
    }

    @Test
    @DisplayName("Should reject order for account with negative balance")
    void testValidateOrderSubmission_NegativeBalance() {
        TradingAccount account = createTestAccount(BigDecimal.valueOf(-100));

        ValidationResult result = tradingRulesService.validateAccountStatus(account);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("negative balance"));
    }

    @Test
    @DisplayName("Should accept order for account with valid balance")
    void testValidateOrderSubmission_ValidAccount() {
        TradingAccount account = createTestAccount(BigDecimal.valueOf(10000));

        ValidationResult result = tradingRulesService.validateAccountStatus(account);

        assertTrue(result.isValid());
    }

    // ==================== Instrument Tradability Tests ====================

    @Test
    @DisplayName("Should reject order for non-tradable instrument (SUSPENDED)")
    void testValidateOrderSubmission_SuspendedInstrument() {
        Instrument instrument = createTestInstrument("SUSPENDED_TICKER", InstrumentStatus.SUSPENDED);
        OrderValidator validator = new OrderValidator();

        ValidationResult result = validator.validateInstrumentTradability(instrument);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("not currently tradable"));
    }

    @Test
    @DisplayName("Should reject order for non-tradable instrument (DELISTED)")
    void testValidateOrderSubmission_DelistedInstrument() {
        Instrument instrument = createTestInstrument("DELISTED_TICKER", InstrumentStatus.DELISTED);
        OrderValidator validator = new OrderValidator();

        ValidationResult result = validator.validateInstrumentTradability(instrument);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("not currently tradable"));
    }

    @Test
    @DisplayName("Should accept order for ACTIVE instrument")
    void testValidateOrderSubmission_ActiveInstrument() {
        Instrument instrument = createTestInstrument("AAPL", InstrumentStatus.ACTIVE);
        OrderValidator validator = new OrderValidator();

        ValidationResult result = validator.validateInstrumentTradability(instrument);

        assertTrue(result.isValid());
    }

    // ==================== Order Size Validation Tests ====================

    @Test
    @DisplayName("Should reject order with zero quantity")
    void testValidateOrderSize_ZeroQuantity() {
        ValidationResult result = tradingRulesService.validateOrderSize(0, OrderSide.BUY);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("must be at least 1"));
    }

    @Test
    @DisplayName("Should reject order with negative quantity")
    void testValidateOrderSize_NegativeQuantity() {
        ValidationResult result = tradingRulesService.validateOrderSize(-100, OrderSide.BUY);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("must be at least 1"));
    }

    @Test
    @DisplayName("Should reject order exceeding maximum quantity limit")
    void testValidateOrderSize_ExceedsMaximum() {
        ValidationResult result = tradingRulesService.validateOrderSize(2_000_000, OrderSide.BUY);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("exceeds maximum limit"));
    }

    @Test
    @DisplayName("Should accept order within quantity limits")
    void testValidateOrderSize_ValidQuantity() {
        ValidationResult result = tradingRulesService.validateOrderSize(100, OrderSide.BUY);

        assertTrue(result.isValid());
    }

    // ==================== Price Validation Tests ====================

    @Test
    @DisplayName("Should reject order with zero price")
    void testValidatePriceRange_ZeroPrice() {
        ValidationResult result = tradingRulesService.validatePriceRange(BigDecimal.ZERO, OrderSide.BUY);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("must be greater than zero"));
    }

    @Test
    @DisplayName("Should reject order with negative price")
    void testValidatePriceRange_NegativePrice() {
        ValidationResult result = tradingRulesService.validatePriceRange(BigDecimal.valueOf(-50), OrderSide.BUY);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("must be greater than zero"));
    }

    @Test
    @DisplayName("Should reject order exceeding maximum price limit")
    void testValidatePriceRange_ExceedsMaximum() {
        ValidationResult result = tradingRulesService.validatePriceRange(BigDecimal.valueOf(2_000_000), OrderSide.BUY);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("exceeds maximum limit"));
    }

    @Test
    @DisplayName("Should accept order within price range")
    void testValidatePriceRange_ValidPrice() {
        ValidationResult result = tradingRulesService.validatePriceRange(BigDecimal.valueOf(150.50), OrderSide.BUY);

        assertTrue(result.isValid());
    }

    // ==================== SELL Order Validation Tests ====================

    @Test
    @DisplayName("Should reject SELL order with no holdings")
    void testValidateSellOrder_NoHolding() {
        OrderValidator validator = new OrderValidator();

        ValidationResult result = validator.validateSellOrder(OrderSide.SELL, 100, null);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Insufficient holdings"));
    }

    @Test
    @DisplayName("Should reject SELL order exceeding available holdings")
    void testValidateSellOrder_InsufficientHolding() {
        Holding holding = createTestHolding(50); // Only have 50 shares
        OrderValidator validator = new OrderValidator();

        ValidationResult result = validator.validateSellOrder(OrderSide.SELL, 100, holding);

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Insufficient holdings"));
    }

    @Test
    @DisplayName("Should accept SELL order within available holdings")
    void testValidateSellOrder_SufficientHolding() {
        Holding holding = createTestHolding(100); // Have 100 shares
        OrderValidator validator = new OrderValidator();

        ValidationResult result = validator.validateSellOrder(OrderSide.SELL, 50, holding);

        assertTrue(result.isValid());
    }

    // ==================== BUY Order Validation Tests ====================

    @Test
    @DisplayName("Should reject BUY order with insufficient funds")
    void testValidateBuyOrder_InsufficientFunds() {
        OrderValidator validator = new OrderValidator();
        // 100 * 150 + 15 = $15,015 required, but only have $10,000
        ValidationResult result = validator.validate(
            OrderSide.BUY,
            100,
            BigDecimal.valueOf(150),
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(15)
        );

        assertFalse(result.isValid());
        assertTrue(result.getReason().contains("Insufficient funds"));
    }

    @Test
    @DisplayName("Should accept BUY order with sufficient funds")
    void testValidateBuyOrder_SufficientFunds() {
        OrderValidator validator = new OrderValidator();
        // 100 * 150 + 15 = $15,015 required, have $20,000
        ValidationResult result = validator.validate(
            OrderSide.BUY,
            100,
            BigDecimal.valueOf(150),
            BigDecimal.valueOf(20000),
            BigDecimal.valueOf(15)
        );

        assertTrue(result.isValid());
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Should reject BUY order when all validations fail")
    void testValidateOrderSubmission_ComprehensiveBuyOrderValidation() {
        TradingAccount account = createTestAccount(BigDecimal.valueOf(10000));
        Instrument instrument = createTestInstrument("AAPL", InstrumentStatus.ACTIVE);

        when(feeCalculator.calculate(OrderSide.BUY, 100, BigDecimal.valueOf(150)))
            .thenReturn(BigDecimal.valueOf(15));
        when(orderValidator.validate(any(), any(), any(), any(), any()))
            .thenReturn(ValidationResult.invalid("Insufficient funds"));
        when(orderValidator.validateInstrumentTradability(instrument))
            .thenReturn(ValidationResult.valid());

        ValidationResult result = tradingRulesService.validateOrderSubmission(
            account,
            instrument,
            OrderSide.BUY,
            100,
            BigDecimal.valueOf(150)
        );

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should accept valid BUY order passing all validations")
    void testValidateOrderSubmission_ValidBuyOrder() {
        TradingAccount account = createTestAccount(BigDecimal.valueOf(50000));
        Instrument instrument = createTestInstrument("AAPL", InstrumentStatus.ACTIVE);

        when(feeCalculator.calculate(OrderSide.BUY, 100, BigDecimal.valueOf(150)))
            .thenReturn(BigDecimal.valueOf(15));
        when(orderValidator.validate(any(), any(), any(), any(), any()))
            .thenReturn(ValidationResult.valid());
        when(orderValidator.validateInstrumentTradability(instrument))
            .thenReturn(ValidationResult.valid());

        ValidationResult result = tradingRulesService.validateOrderSubmission(
            account,
            instrument,
            OrderSide.BUY,
            100,
            BigDecimal.valueOf(150)
        );

        assertTrue(result.isValid());
    }

    // ==================== Helper Methods ====================

    private TradingAccount createTestAccount(BigDecimal balance) {
        TradingAccount account = new TradingAccount();
        account.setAccountId(1);
        account.setAvailableFunds(balance);
        return account;
    }

    private Instrument createTestInstrument(String ticker, InstrumentStatus status) {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(1);
        instrument.setTicker(ticker);
        instrument.setInstrumentName("Test Instrument");
        instrument.setStatus(status);
        return instrument;
    }

    private Holding createTestHolding(Integer quantity) {
        Holding holding = new Holding();
        holding.setHoldingId(1);
        holding.setQuantity(quantity);
        holding.setAverageCost(BigDecimal.valueOf(100));
        return holding;
    }
}
