package com.neueda.leap.trading.service.contract;

import java.math.BigDecimal;

import com.neueda.leap.trading.service.impl.ValidationResult;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.TradingAccount;

/**
 * TradingRulesService enforces comprehensive business trading rules.
 * BR-05: Every order must be checked against the firm's trading rules before it is accepted.
 *
 * Trading rules checked:
 * 1. Basic order validation (side, quantity, price)
 * 2. Instrument tradability
 * 3. Sufficient cash for BUY orders
 * 4. Sufficient holdings for SELL orders
 * 5. Account status validation
 * 6. Order size limits
 * 7. Price range validation
 */
public interface TradingRulesService {
    /**
     * Perform comprehensive validation on an order submission.
     * This is called BEFORE the order is accepted into the system.
     *
     * @param account The trading account placing the order
     * @param instrument The instrument being traded
     * @param side BUY or SELL
     * @param quantity Number of units
     * @param proposedPrice The price requested by the client
     * @return ValidationResult indicating success or detailed failure reason
     */
    ValidationResult validateOrderSubmission(
        TradingAccount account,
        Instrument instrument,
        OrderSide side,
        Integer quantity,
        BigDecimal proposedPrice
    );

    /**
     * Check if an account is in good standing and eligible to place orders.
     *
     * @param account The trading account
     * @return ValidationResult indicating account eligibility
     */
    ValidationResult validateAccountStatus(TradingAccount account);

    /**
     * Check if an order size is within acceptable limits.
     *
     * @param quantity Order quantity
     * @param side BUY or SELL
     * @return ValidationResult indicating if size is acceptable
     */
    ValidationResult validateOrderSize(Integer quantity, OrderSide side);

    /**
     * Check if the proposed price is within acceptable range.
     *
     * @param proposedPrice The price requested
     * @param side BUY or SELL
     * @return ValidationResult indicating if price is acceptable
     */
    ValidationResult validatePriceRange(BigDecimal proposedPrice, OrderSide side);
}
