package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "trades_executed")
public class Trade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "executeid")
    private Integer tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestid", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "askprice", nullable = false)
    private BigDecimal executionPrice;

    @Column(name = "date_execution", nullable = false)
    private LocalDate executedAt;

    @Column(name = "quantity_executed", nullable = false)
    private Integer executedQuantity;

    public Integer getTradeId() {
        return tradeId;
    }

    public void setTradeId(Integer tradeId) {
        this.tradeId = tradeId;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    @Transient
    @JsonProperty("account")
    public TradingAccount getAccount() {
        return order == null ? null : order.getAccount();
    }

    @Transient
    @JsonProperty("instrument")
    public Instrument getInstrument() {
        return order == null ? null : order.getInstrument();
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public LocalDate getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDate executedAt) {
        this.executedAt = executedAt;
    }

    public Integer getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(Integer executedQuantity) {
        this.executedQuantity = executedQuantity;
    }
}
