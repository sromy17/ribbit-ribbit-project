package com.neueda.leap.trading.repository.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.AccessToken;

public interface AccessTokenRepository extends JpaRepository<AccessToken, Integer> {
    Optional<AccessToken> findByAccessTokenHashAndRevokedFalseAndDateExpiresAfter(
        String accessTokenHash,
        Instant now
    );
}
