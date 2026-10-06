package com.neueda.leap.trading.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.Session;

public interface SessionRepository extends JpaRepository<Session, Integer> {
    Optional<Session> findByAccessToken(String accessToken);

    void deleteByAccessToken(String accessToken);
}
