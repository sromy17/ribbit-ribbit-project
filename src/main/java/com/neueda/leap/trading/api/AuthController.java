package com.neueda.leap.trading.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import com.neueda.leap.trading.service.impl.AuthenticationService;

@RestController
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.LoginResult result = authenticationService.login(request);
        return Map.of("token", result.accessToken(), "expiresAt", result.expiresAt().toString());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request.accessToken());
        return Map.of("status", "revoked");
    }
}
