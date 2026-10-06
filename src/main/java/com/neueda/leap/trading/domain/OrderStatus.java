package com.neueda.leap.trading.domain;

/**
 * Enum representing the lifecycle status of a trading order.
 * BR-06: Orders are recorded with their status before execution.
 * BR-05: Orders can be rejected if they fail trading rules validation.
 */
public enum OrderStatus {
    /** Order has passed validation and is waiting for execution */
    PENDING,
    /** Order has been successfully executed */
    EXECUTED,
    /** Order was cancelled by the client before execution */
    CANCELLED,
    /** Order was rejected due to trading rules validation failure */
    REJECTED
}
