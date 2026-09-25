package com.neueda.leap.trading.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.neueda.leap.trading.domain.OrderSide;

@Component
public class OrderValidator {

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
                return ValidationResult.invalid("Insufficient funds for BUY order");
            }
        }

        return ValidationResult.valid();
    }
}
