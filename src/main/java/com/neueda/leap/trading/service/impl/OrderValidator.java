package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.OrderSide;

/**
 * Validates orders according to trading rules defined in BR-05.
 * Checks:
 * - Basic order parameters (side, quantity, price)
 * - Cash balance for BUY orders
 * - Holdings for SELL orders
 * - Instrument tradability status
 */
@Component
public class OrderValidator {

    /**
     * Validate order parameters and cash balance for BUY orders.
     * BR-05: Every order must be checked against trading rules before acceptance.
     */
    public ValidationResult validate(OrderSide side, Integer quantity, BigDecimal price, BigDecimal cashBalance, BigDecimal fees) {
        if (side == null) {
            return ValidationResult.invalid("Order side is required");
        }
        if (quantity == null || quantity <= 0) {
            return ValidationResult.invalid("Quantity must be greater than zero");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.invalid("Price must be greater than zero");
        }
        if (cashBalance == null || cashBalance.compareTo(BigDecimal.ZERO) < 0) {
            return ValidationResult.invalid("Account cash balance is invalid");
        }

        if (side == OrderSide.BUY) {
            BigDecimal totalCost = price.multiply(BigDecimal.valueOf(quantity)).add(fees);
            if (cashBalance.compareTo(totalCost) < 0) {
                return ValidationResult.invalid("Insufficient funds for BUY order. Required: " + totalCost + ", Available: " + cashBalance);
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validate order for SELL side, checking for sufficient holdings.
     * BR-05: Every order must be checked against trading rules before acceptance.
     *
     * @param side Order side (BUY or SELL)
     * @param quantity Quantity to sell
     * @param holding Current holding for the instrument (null if no position exists)
     * @return ValidationResult indicating success or failure with reason
     */
    public ValidationResult validateSellOrder(OrderSide side, Integer quantity, Holding holding) {
        if (side != OrderSide.SELL) {
            return ValidationResult.valid(); // Only validate SELL orders
        }

        if (quantity == null || quantity <= 0) {
            return ValidationResult.invalid("Quantity must be greater than zero");
        }

        if (holding == null || holding.getQuantity() == null || holding.getQuantity() <= 0) {
            return ValidationResult.invalid("Insufficient holdings. No position exists for this instrument");
        }

        if (holding.getQuantity() < quantity) {
            return ValidationResult.invalid(
                "Insufficient holdings for SELL order. Required: " + quantity + 
                ", Available: " + holding.getQuantity()
            );
        }

        return ValidationResult.valid();
    }

    /**
     * Validate that the instrument is currently tradable.
     * BR-05: Instrument must be currently tradable before accepting orders.
     *
     * @param instrument The instrument to validate
     * @return ValidationResult indicating if instrument is tradable
     */
    public ValidationResult validateInstrumentTradability(Instrument instrument) {
        if (instrument == null) {
            return ValidationResult.invalid("Instrument not found");
        }

        if (!instrument.isTradable()) {
            return ValidationResult.invalid(
                "Instrument " + instrument.getTicker() + " is not currently tradable. Status: " + instrument.getStatus()
            );
        }

        return ValidationResult.valid();
    }
}
