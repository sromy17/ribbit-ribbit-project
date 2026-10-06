package com.neueda.leap.trading.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "holdings")
public class Holding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holdingid")
    private Integer holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accountid", nullable = false)
    @JsonIgnore
    private TradingAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumentid", nullable = false)
    @JsonIgnore
    private Instrument instrument;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "averagecost", nullable = false)
    private BigDecimal averageCost;

    public void updatePosition(boolean isBuy, int deltaQty, BigDecimal executionPrice) {
        if (deltaQty <= 0) {
            throw new IllegalArgumentException("Quantity delta must be positive");
        }
        if (isBuy) {
            BigDecimal currentValue = averageCost.multiply(BigDecimal.valueOf(quantity));
            BigDecimal purchasedValue = executionPrice.multiply(BigDecimal.valueOf(deltaQty));
            int newQuantity = quantity + deltaQty;
            this.averageCost = currentValue.add(purchasedValue)
                .divide(BigDecimal.valueOf(newQuantity), 6, java.math.RoundingMode.HALF_UP);
            this.quantity = newQuantity;
            return;
        }

        if (deltaQty > quantity) {
            throw new IllegalArgumentException("Cannot sell more than held quantity");
        }

        this.quantity -= deltaQty;
        if (this.quantity == 0) {
            this.averageCost = BigDecimal.ZERO;
        }
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public Integer getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Integer holdingId) {
        this.holdingId = holdingId;
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
}
