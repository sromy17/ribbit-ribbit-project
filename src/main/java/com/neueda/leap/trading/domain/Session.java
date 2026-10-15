package com.neueda.leap.trading.domain;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_sessions")
public class Session {
    @Id
    @Column(name = "userid")
    private Integer userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @Column(name = "refreshtoken", nullable = false)
    private Integer refreshToken;

    @Column(name = "dateissued", nullable = false)
    private LocalDate dateIssued;

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(Integer refreshToken) {
        this.refreshToken = refreshToken;
    }

    public LocalDate getDateIssued() {
        return dateIssued;
    }

    public void setDateIssued(LocalDate dateIssued) {
        this.dateIssued = dateIssued;
    }
}
