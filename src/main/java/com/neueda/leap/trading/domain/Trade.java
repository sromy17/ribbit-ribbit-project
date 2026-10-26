package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "trades_executed")
public class Trade {
    @Id
    @Column(name = "executeid")
    private Integer tradeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestid", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "askprice", nullable = false, precision = 30, scale = 10)
    private BigDecimal executionPrice;

    @Column(name = "date_execution", nullable = false)
    private java.time.LocalDate executedAt;

    @Column(name = "quantity_executed", nullable = false)
    private Integer executedQuantity;

    @Column(name = "fee", nullable = false, precision = 30, scale = 10)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "executed_at_utc")
    private java.time.Instant executedAtUtc;

    @Column(name = "execution_currency", length = 3)
    private String executionCurrency;
    @Column(name = "fee_currency", length = 3)
    private String feeCurrency;
    @Column(name = "quantity_unit", length = 40)
    private String quantityUnit;
    @Column(name = "contract_multiplier", precision = 30, scale = 10)
    private BigDecimal contractMultiplier;
    @Column(name = "product_convention", length = 80)
    private String productConvention;
    @Column(name = "pricing_event_id")
    private Long pricingEventId;
    @Column(name = "client_id")
    private Integer clientId;

    public Integer getClientId() { return clientId; }
    public void setClientId(Integer value) { clientId = value; }
    public String getExecutionCurrency() { return executionCurrency; }
    public void setExecutionCurrency(String value) { executionCurrency = value; }
    public String getFeeCurrency() { return feeCurrency; }
    public void setFeeCurrency(String value) { feeCurrency = value; }
    public String getQuantityUnit() { return quantityUnit; }
    public void setQuantityUnit(String value) { quantityUnit = value; }
    public BigDecimal getContractMultiplier() { return contractMultiplier; }
    public void setContractMultiplier(BigDecimal value) { contractMultiplier = value; }
    public String getProductConvention() { return productConvention; }
    public void setProductConvention(String value) { productConvention = value; }
    public Long getPricingEventId() { return pricingEventId; }
    public void setPricingEventId(Long value) { pricingEventId = value; }

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

    public java.time.LocalDate getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(java.time.LocalDate executedAt) {
        this.executedAt = executedAt;
    }

    public Integer getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(Integer executedQuantity) {
        this.executedQuantity = executedQuantity;
    }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public java.time.Instant getExecutedAtUtc() { return executedAtUtc; }
    public void setExecutedAtUtc(java.time.Instant executedAtUtc) { this.executedAtUtc = executedAtUtc; }
}
