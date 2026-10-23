package com.neueda.leap.trading.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rejected_orders")
public class RejectedOrder {
    @Id
    @Column(name = "reject_id")
    private Integer rejectId;

    @Column(name = "request_id", nullable = false)
    private Integer requestId;

    @Column(name = "reason", length = 50)
    private String reason;

    @Column(name = "reject_date", nullable = false)
    private Instant rejectDate;

    public Integer getRejectId() {
        return rejectId;
    }

    public void setRejectId(Integer rejectId) {
        this.rejectId = rejectId;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public void setRequestId(Integer requestId) {
        this.requestId = requestId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getRejectDate() {
        return rejectDate;
    }

    public void setRejectDate(Instant rejectDate) {
        this.rejectDate = rejectDate;
    }
}
