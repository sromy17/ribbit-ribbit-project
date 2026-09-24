package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "accounts")
public class TradingAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accountid")
    private Integer accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "availablefunds", nullable = false)
    private BigDecimal availableFunds;

    @Column(name = "creationdate", nullable = false)
    private LocalDate creationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType type;

    @JsonIgnore
    @OneToMany(mappedBy = "account")
    private List<Order> orders = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "account")
    private List<Holding> holdings = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "account")
    private List<TransactionRecord> transactionRecords = new ArrayList<>();

    public void depositFunds(BigDecimal amount) {
        this.availableFunds = this.availableFunds.add(amount);
    }

    public void withdrawFunds(BigDecimal amount) {
        this.availableFunds = this.availableFunds.subtract(amount);
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BigDecimal getAvailableFunds() {
        return availableFunds;
    }

    public void setAvailableFunds(BigDecimal availableFunds) {
        this.availableFunds = availableFunds;
    }

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    @Transient
    @JsonProperty("trades")
    public List<Trade> getTrades() {
        return orders.stream()
            .flatMap(order -> order.getTrades().stream())
            .collect(Collectors.toList());
    }

    public List<Holding> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<Holding> holdings) {
        this.holdings = holdings;
    }

    public List<TransactionRecord> getTransactionRecords() {
        return transactionRecords;
    }

    public void setTransactionRecords(List<TransactionRecord> transactionRecords) {
        this.transactionRecords = transactionRecords;
    }
}
