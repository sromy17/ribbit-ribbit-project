package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_movement")
public class BankMovement {
    @Id
    @Column(name = "movement_id")
    private Integer movementId;

    @Column(name = "bankid", nullable = false)
    private Integer bankId;

    @Column(name = "amount", nullable = false, precision = 30, scale = 10)
    private BigDecimal amount;

    @Column(name = "movement_type", nullable = false, length = 10)
    private String movementType;

    @Column(name = "movement_date", nullable = false)
    private Instant movementDate;

    public Integer getMovementId() { return movementId; }
    public Integer getBankId() { return bankId; }
    public BigDecimal getAmount() { return amount; }
    public String getMovementType() { return movementType; }
    public Instant getMovementDate() { return movementDate; }
    public void setMovementId(Integer movementId) { this.movementId = movementId; }
    public void setBankId(Integer bankId) { this.bankId = bankId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setMovementType(String movementType) { this.movementType = movementType; }
    public void setMovementDate(Instant movementDate) { this.movementDate = movementDate; }
}
