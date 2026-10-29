package com.neueda.leap.trading.domain;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "holding_movements")
public class HoldingMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id") private Long movementId;
    @Column(name = "order_id", nullable = false) private Integer orderId;
    @Column(name = "account_id", nullable = false) private Integer accountId;
    @Column(name = "instrument_id", nullable = false) private Integer instrumentId;
    @Column(name = "quantity_delta", nullable = false) private Integer quantityDelta;
    @Column(name = "quantity_after", nullable = false) private Integer quantityAfter;
    @Column(name = "occurred_at_utc", nullable = false) private Instant occurredAtUtc;
    @Column(name = "correlation_id", nullable = false, length = 36) private String correlationId;
    public void setOrderId(Integer v) { orderId = v; }
    public void setAccountId(Integer v) { accountId = v; }
    public void setInstrumentId(Integer v) { instrumentId = v; }
    public void setQuantityDelta(Integer v) { quantityDelta = v; }
    public void setQuantityAfter(Integer v) { quantityAfter = v; }
    public void setOccurredAtUtc(Instant v) { occurredAtUtc = v; }
    public void setCorrelationId(String v) { correlationId = v; }
}