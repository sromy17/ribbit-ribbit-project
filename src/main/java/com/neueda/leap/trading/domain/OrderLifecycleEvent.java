package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "order_lifecycle_events", uniqueConstraints = @UniqueConstraint(
    name = "uk_order_lifecycle_sequence", columnNames = {"order_id", "event_sequence"}
))
public class OrderLifecycleEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;
    @Column(name = "account_id", nullable = false)
    private Integer accountId;
    @Column(name = "instrument_id")
    private Integer instrumentId;
    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;
    @Column(name = "event_sequence", nullable = false)
    private Long eventSequence;
    @Column(name = "event_version", nullable = false)
    private Integer eventVersion = 1;
    @Column(name = "occurred_at_utc", nullable = false)
    private Instant occurredAtUtc;
    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;
    @Column(name = "quote_source", length = 80)
    private String quoteSource;
    @Column(name = "quote_at_utc")
    private Instant quoteAtUtc;
    @Column(name = "quote_price", precision = 30, scale = 10)
    private BigDecimal quotePrice;
    @Column(name = "decision_price", precision = 30, scale = 10)
    private BigDecimal decisionPrice;
    @Column(name = "quantity")
    private Integer quantity;
    @Column(name = "fee", precision = 30, scale = 10)
    private BigDecimal fee;
    @Column(name = "reason", length = 500)
    private String reason;

    public Long getEventId() { return eventId; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }
    public Integer getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Integer instrumentId) { this.instrumentId = instrumentId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getEventSequence() { return eventSequence; }
    public void setEventSequence(Long eventSequence) { this.eventSequence = eventSequence; }
    public Integer getEventVersion() { return eventVersion; }
    public void setEventVersion(Integer eventVersion) { this.eventVersion = eventVersion; }
    public Instant getOccurredAtUtc() { return occurredAtUtc; }
    public void setOccurredAtUtc(Instant occurredAtUtc) { this.occurredAtUtc = occurredAtUtc; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getQuoteSource() { return quoteSource; }
    public void setQuoteSource(String quoteSource) { this.quoteSource = quoteSource; }
    public Instant getQuoteAtUtc() { return quoteAtUtc; }
    public void setQuoteAtUtc(Instant quoteAtUtc) { this.quoteAtUtc = quoteAtUtc; }
    public BigDecimal getQuotePrice() { return quotePrice; }
    public void setQuotePrice(BigDecimal quotePrice) { this.quotePrice = quotePrice; }
    public BigDecimal getDecisionPrice() { return decisionPrice; }
    public void setDecisionPrice(BigDecimal decisionPrice) { this.decisionPrice = decisionPrice; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}