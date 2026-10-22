package com.neueda.leap.trading.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "access_tokens")
public class AccessToken {
    @Id
    @Column(name = "accesstokenid")
    private Integer accessTokenId;

    @Column(name = "refreshtokenid", nullable = false)
    private Integer refreshTokenId;

    @Column(name = "accesstokenhash", nullable = false, length = 64)
    private String accessTokenHash;

    @Column(name = "dateissued", nullable = false)
    private Instant dateIssued;

    @Column(name = "dateexpires", nullable = false)
    private Instant dateExpires;

    @Column(name = "isrevoked", nullable = false)
    private boolean revoked;

    public String getAccessTokenHash() { return accessTokenHash; }
    public Instant getDateIssued() { return dateIssued; }
    public Instant getDateExpires() { return dateExpires; }
    public boolean isRevoked() { return revoked; }
}
