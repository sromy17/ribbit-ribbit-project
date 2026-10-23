package com.neueda.leap.trading.domain;

import java.math.BigDecimal;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_request")
public class Order {
    @Id
    @Column(name = "requestid")
    private Integer orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accountid", nullable = false)
    @JsonIgnore
    private TradingAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumentid", nullable = false)
    @JsonIgnore
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 5)
    private OrderSide side;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "bidprice", nullable = false)
    private BigDecimal price;

    @Column(name = "date_request", nullable = false)
    private java.time.LocalDate dateRequested;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "created_at_utc")
    private Instant createdAtUtc;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId = UUID.randomUUID().toString();

    @JsonIgnore
    @OneToMany(mappedBy = "order")
    private List<Trade> trades = new ArrayList<>();

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public TradingAccount getAccount() {
        return account;
    }

    public void setAccount(TradingAccount account) {
        this.account = account;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }

    @JsonProperty("symbol")
    public String getSymbol() {
        return instrument == null ? null : instrument.getTicker();
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public java.time.LocalDate getDateRequested() {
        return dateRequested;
    }

    public void setDateRequested(java.time.LocalDate dateRequested) {
        this.dateRequested = dateRequested;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public List<Trade> getTrades() {
        return trades;
    }

    public void setTrades(List<Trade> trades) {
        this.trades = trades;
    }
}
