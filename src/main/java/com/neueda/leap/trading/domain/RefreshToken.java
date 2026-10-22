package com.neueda.leap.trading.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @Column(name = "refreshtokenid")
    private Integer refreshTokenId;

    @Column(name = "userid", nullable = false)
    private Integer userId;

    @Column(name = "refreshtokenhash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "dateissued", nullable = false)
    private Instant dateIssued;

    @Column(name = "dateexpires", nullable = false)
    private Instant dateExpires;

    @Column(name = "isrevoked", nullable = false)
    private boolean revoked;

    public Integer getRefreshTokenId() { return refreshTokenId; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public Instant getDateIssued() { return dateIssued; }
    public Instant getDateExpires() { return dateExpires; }
    public boolean isRevoked() { return revoked; }
}
