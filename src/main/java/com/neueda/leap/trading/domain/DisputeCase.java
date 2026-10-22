package com.neueda.leap.trading.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "dispute_cases", uniqueConstraints = @UniqueConstraint(name = "uk_dispute_idempotency", columnNames = {"customer_user_id", "idempotency_key"}))
public class DisputeCase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id") private Long caseId;
    @Column(name = "order_id") private Integer orderId;
    @Column(name = "transaction_type", nullable = false, length = 20) private String transactionType;
    @Column(name = "transaction_reference", nullable = false, length = 100) private String transactionReference;
    @Column(name = "cash_movement_id") private Long cashMovementId;
    @Column(name = "account_id", nullable = false) private Integer accountId;
    @Column(name = "customer_user_id", nullable = false) private Integer customerUserId;
    @Column(name = "reason", nullable = false, length = 1000) private String reason;
    @Column(name = "title", nullable = false, length = 120) private String title;
    @Column(name = "fee_related", nullable = false) private boolean feeRelated;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    private DisputeCaseStatus status = DisputeCaseStatus.OPEN;
    @Column(name = "assigned_staff_user_id") private Integer assignedStaffUserId;
    @Column(name = "resolution_reason", length = 1000) private String resolutionReason;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Column(name = "created_at_utc", nullable = false) private Instant createdAtUtc;
    @Column(name = "updated_at_utc", nullable = false) private Instant updatedAtUtc;
    @Version @Column(name = "version", nullable = false) private Long version;
    @JsonIgnore @OneToMany(mappedBy = "disputeCase", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<DisputeCaseEvent> events = new ArrayList<>();

    public Long getCaseId() { return caseId; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer v) { orderId = v; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { transactionType = v; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String v) { transactionReference = v; }
    public Long getCashMovementId() { return cashMovementId; }
    public void setCashMovementId(Long v) { cashMovementId = v; }
    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer v) { accountId = v; }
    public Integer getCustomerUserId() { return customerUserId; }
    public void setCustomerUserId(Integer v) { customerUserId = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public boolean isFeeRelated() { return feeRelated; }
    public void setFeeRelated(boolean v) { feeRelated = v; }
    public DisputeCaseStatus getStatus() { return status; }
    public void setStatus(DisputeCaseStatus v) { status = v; }
    public Integer getAssignedStaffUserId() { return assignedStaffUserId; }
    public void setAssignedStaffUserId(Integer v) { assignedStaffUserId = v; }
    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String v) { resolutionReason = v; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String v) { payloadHash = v; }
    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant v) { createdAtUtc = v; }
    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(Instant v) { updatedAtUtc = v; }
    public Long getVersion() { return version; }
    public List<DisputeCaseEvent> getEvents() { return events; }
}