package com.neueda.leap.trading.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank")
public class Bank {
    @Id
    @Column(name = "bankid")
    private Integer bankId;

    @Column(name = "accountid", nullable = false)
    private Integer accountId;

    @Column(name = "accountname", nullable = false, length = 50)
    private String accountName;

    @Column(name = "routing_number", nullable = false, length = 9)
    private String routingNumber;

    @Column(name = "account_number", nullable = false, length = 17)
    private String accountNumber;

    @Column(name = "balance", nullable = false, precision = 30, scale = 10)
    private BigDecimal balance;

    public Integer getBankId() { return bankId; }
    public Integer getAccountId() { return accountId; }
    public String getAccountName() { return accountName; }
    public String getRoutingNumber() { return routingNumber; }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public void setBankId(Integer bankId) { this.bankId = bankId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public void setRoutingNumber(String routingNumber) { this.routingNumber = routingNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
