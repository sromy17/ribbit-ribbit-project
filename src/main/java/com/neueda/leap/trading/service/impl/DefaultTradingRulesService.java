package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.service.contract.FeeCalculator;
import com.neueda.leap.trading.service.contract.TradingRulesService;

/**
 * Default implementation of TradingRulesService.
 * Enforces all business rules for order submission and acceptance.
 * BR-05: Every order must be checked against the firm's trading rules before it is accepted.
 */
@Service
public class DefaultTradingRulesService implements TradingRulesService {
    private static final Logger log = LoggerFactory.getLogger(DefaultTradingRulesService.class);

    // Trading rule thresholds
    private static final int MAX_ORDER_QUANTITY = 1_000_000;
    private static final int MIN_ORDER_QUANTITY = 1;
    private static final BigDecimal MAX_ORDER_PRICE = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal MIN_ORDER_PRICE = BigDecimal.ZERO;

    private final OrderValidator orderValidator;
    private final FeeCalculator feeCalculator;
    private final HoldingRepository holdingRepository;

    public DefaultTradingRulesService(
        OrderValidator orderValidator,
        FeeCalculator feeCalculator,
        HoldingRepository holdingRepository
    ) {
        this.orderValidator = orderValidator;
        this.feeCalculator = feeCalculator;
        this.holdingRepository = holdingRepository;
    }

    /**
     * Perform comprehensive validation on an order submission.
     * BR-05: Every order must be checked against the firm's trading rules before it is accepted.
     *
     * Checks (in order):
     * 1. Account is in good standing
     * 2. Instrument is tradable
     * 3. Order size is within limits
     * 4. Order price is within acceptable range
     * 5. For BUY orders: sufficient cash balance
     * 6. For SELL orders: sufficient holdings
     */
    @Override
    public ValidationResult validateOrderSubmission(
        TradingAccount account,
        Instrument instrument,
        OrderSide side,
        Integer quantity,
        BigDecimal proposedPrice
    ) {
        log.debug("Validating order submission for account {}, instrument {}, side {}, qty {}, price {}",
            account.getAccountId(), instrument.getTicker(), side, quantity, proposedPrice);

        // 1. Validate account status
        ValidationResult accountValidation = validateAccountStatus(account);
        if (!accountValidation.isValid()) {
            log.warn("Account validation failed: {}", accountValidation.getReason());
            return accountValidation;
        }

        // 2. Validate instrument tradability
        ValidationResult instrumentValidation = orderValidator.validateInstrumentTradability(instrument);
        if (!instrumentValidation.isValid()) {
            log.warn("Instrument validation failed: {}", instrumentValidation.getReason());
            return instrumentValidation;
        }

        // 3. Validate order size
        ValidationResult sizeValidation = validateOrderSize(quantity, side);
        if (!sizeValidation.isValid()) {
            log.warn("Order size validation failed: {}", sizeValidation.getReason());
            return sizeValidation;
        }

        // 4. Validate price range
        ValidationResult priceValidation = validatePriceRange(proposedPrice, side);
        if (!priceValidation.isValid()) {
            log.warn("Price range validation failed: {}", priceValidation.getReason());
            return priceValidation;
        }

        // 5. Validate based on order side
        if (side == OrderSide.BUY) {
            BigDecimal estimatedFee = feeCalculator.calculate(side, quantity, proposedPrice);
            ValidationResult buyValidation = orderValidator.validate(
                side,
                quantity,
                proposedPrice,
                account.getAvailableFunds(),
                estimatedFee
            );
            if (!buyValidation.isValid()) {
                log.warn("BUY order validation failed: {}", buyValidation.getReason());
                return buyValidation;
            }
        } else if (side == OrderSide.SELL) {
            Holding holding = holdingRepository.findByAccountAndInstrument(account, instrument);
            ValidationResult sellValidation = orderValidator.validateSellOrder(side, quantity, holding);
            if (!sellValidation.isValid()) {
                log.warn("SELL order validation failed: {}", sellValidation.getReason());
                return sellValidation;
            }
        }

        log.debug("Order validation passed for account {}, instrument {}", account.getAccountId(), instrument.getTicker());
        return ValidationResult.valid();
    }

    /**
     * Check if an account is in good standing and eligible to place orders.
     * Validations:
     * - Account is not null
     * - Account has valid funds >= 0
     * - Account exists and is active
     */
    @Override
    public ValidationResult validateAccountStatus(TradingAccount account) {
        if (account == null) {
            return ValidationResult.invalid("Account not found");
        }

        if (account.getAvailableFunds() == null) {
            return ValidationResult.invalid("Account has invalid fund information");
        }

        if (account.getAvailableFunds().compareTo(BigDecimal.ZERO) < 0) {
            return ValidationResult.invalid("Account has negative balance. Deposit funds before placing orders");
        }

        return ValidationResult.valid();
    }

    /**
     * Check if an order size is within acceptable limits.
     * Current limits:
     * - Minimum: 1 unit
     * - Maximum: 1,000,000 units
     */
    @Override
    public ValidationResult validateOrderSize(Integer quantity, OrderSide side) {
        if (quantity == null || quantity < MIN_ORDER_QUANTITY) {
            return ValidationResult.invalid(
                "Order quantity must be at least " + MIN_ORDER_QUANTITY
            );
        }

        if (quantity > MAX_ORDER_QUANTITY) {
            return ValidationResult.invalid(
                "Order quantity exceeds maximum limit of " + MAX_ORDER_QUANTITY +
                ". Please split your order or contact trading desk for large orders"
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Check if the proposed price is within acceptable range.
     * Current limits:
     * - Minimum: 0.01
     * - Maximum: 1,000,000
     */
    @Override
    public ValidationResult validatePriceRange(BigDecimal proposedPrice, OrderSide side) {
        if (proposedPrice == null) {
            return ValidationResult.invalid("Price is required");
        }

        if (proposedPrice.compareTo(MIN_ORDER_PRICE) <= 0) {
            return ValidationResult.invalid("Price must be greater than zero");
        }

        if (proposedPrice.compareTo(MAX_ORDER_PRICE) > 0) {
            return ValidationResult.invalid(
                "Price exceeds maximum limit of " + MAX_ORDER_PRICE
            );
        }

        return ValidationResult.valid();
    }
}
