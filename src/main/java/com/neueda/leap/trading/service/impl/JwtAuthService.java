package com.neueda.leap.trading.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.domain.Session;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.SessionRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import com.neueda.leap.trading.security.JwtService;
import com.neueda.leap.trading.service.contract.AuthResult;
import com.neueda.leap.trading.service.contract.AuthService;
import com.neueda.leap.trading.service.contract.RegisterResult;

@Service
public class JwtAuthService implements AuthService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public JwtAuthService(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public RegisterResult register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        User user = new User();
        user.setUserId(generateUserId());
        user.setUsername(username);
        user.setSalt("bcrypt");
        user.setUserHashedSaltedPassword(passwordEncoder.encode(password));

        User savedUser = userRepository.save(user);
        return new RegisterResult(savedUser.getUserId(), savedUser.getUsername());
    }

    @Override
    @Transactional
    public AuthResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!matchesPassword(password, user.getUserHashedSaltedPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtService.generateToken(user);
        Instant expiresAt = jwtService.extractExpiration(token);

        Session session = new Session();
        session.setUser(user);
        session.setAccessToken(token);
        session.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        sessionRepository.save(session);

        return new AuthResult(token, expiresAt);
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        sessionRepository.deleteByAccessToken(accessToken);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> resolveAuthenticatedUser(String accessToken) {
        if (!jwtService.isTokenValid(accessToken)) {
            return Optional.empty();
        }

        Optional<Session> session = sessionRepository.findByAccessToken(accessToken)
            .filter(existingSession -> existingSession != null && existingSession.isValid());
        if (session.isEmpty()) {
            return Optional.empty();
        }

        String username = jwtService.extractUsername(accessToken);
        return userRepository.findByUsername(username);
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedPassword) || storedPassword.equals(rawPassword);
    }

    private int generateUserId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(100000, 999999);
            if (!userRepository.existsById(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to allocate a user id");
    }
}
