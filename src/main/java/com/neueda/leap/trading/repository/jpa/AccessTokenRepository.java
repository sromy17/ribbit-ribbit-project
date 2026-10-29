package com.neueda.leap.trading.repository.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.AccessToken;

public interface AccessTokenRepository extends JpaRepository<AccessToken, Integer> {
    @org.springframework.data.jpa.repository.Query("select a from AccessToken a, RefreshToken r where a.refreshTokenId=r.refreshTokenId and a.accessTokenHash=:hash and a.revoked=false and r.revoked=false and a.dateExpires>:now and r.dateExpires>:now and r.userId=:uid")
    Optional<AccessToken> findValidToken(@org.springframework.data.repository.query.Param("hash") String hash,
        @org.springframework.data.repository.query.Param("uid") Integer uid,@org.springframework.data.repository.query.Param("now") Instant now);

    Optional<AccessToken> findByAccessTokenHashAndRevokedFalseAndDateExpiresAfter(
        String accessTokenHash,
        Instant now
    );
}
