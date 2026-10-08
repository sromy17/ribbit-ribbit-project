package com.neueda.leap.trading.domain;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "holdings")
@IdClass(HoldingId.class)
public class Holding {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accountid", nullable = false)
    @JsonIgnore
    private TradingAccount account;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrumentid", nullable = false)
    @JsonIgnore
    private Instrument instrument;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
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
