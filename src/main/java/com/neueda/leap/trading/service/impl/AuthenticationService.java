package com.neueda.leap.trading.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.neueda.leap.trading.api.LoginRequest;
import com.neueda.leap.trading.domain.Role;
import com.neueda.leap.trading.domain.Session;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.SessionRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long sessionLifetimeSeconds;

    public AuthenticationService(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        PasswordEncoder passwordEncoder,
        JwtEncoder jwtEncoder,
        @Value("${security.jwt.session-lifetime-seconds:3600}") long sessionLifetimeSeconds
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.sessionLifetimeSeconds = sessionLifetimeSeconds;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getUserHashedSaltedPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(sessionLifetimeSeconds);
        List<String> roles = user.getRoles().stream().map(Role::getRoleName).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(user.getUsername())
            .claim("uid", user.getUserId())
            .claim("roles", roles)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        Session session = new Session();
        session.setUser(user);
        session.setAccessToken(accessToken);
        session.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        sessionRepository.save(session);
        return new LoginResult(accessToken, expiresAt);
    }

    @Transactional
    public void logout(String accessToken) {
        sessionRepository.deleteByAccessToken(accessToken);
    }

    public record LoginResult(String accessToken, Instant expiresAt) {
    }
}