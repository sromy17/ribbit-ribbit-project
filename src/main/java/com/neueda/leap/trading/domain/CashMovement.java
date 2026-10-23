package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "cash_movements")
public class CashMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id") private Long movementId;
    @Column(name = "order_id", nullable = false) private Integer orderId;
    @Column(name = "account_id", nullable = false) private Integer accountId;
    @Column(name = "amount", nullable = false, precision = 30, scale = 10) private BigDecimal amount;
    @Column(name = "currency", length = 3) private String currency;
    @Column(name = "currency_source", length = 80) private String currencySource;
    @Column(name = "movement_type", nullable = false, length = 40) private String movementType;
    @Column(name = "occurred_at_utc", nullable = false) private Instant occurredAtUtc;
    @Column(name = "correlation_id", nullable = false, length = 36) private String correlationId;
    public void setOrderId(Integer v) { orderId = v; }
    public void setAccountId(Integer v) { accountId = v; }
    public void setAmount(BigDecimal v) { amount = v; }
    public void setCurrency(String v) { currency = v; }
    public String getCurrencySource() { return currencySource; }
    public void setCurrencySource(String value) { currencySource = value; }
    public void setMovementType(String v) { movementType = v; }
    public void setOccurredAtUtc(Instant v) { occurredAtUtc = v; }
    public void setCorrelationId(String v) { correlationId = v; }
    public Long getMovementId() { return movementId; }
    public Integer getOrderId() { return orderId; }
    public Integer getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
}