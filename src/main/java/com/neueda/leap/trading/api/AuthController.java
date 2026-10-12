package com.neueda.leap.trading.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.leap.trading.api.dto.CreateUserRequest;
import com.neueda.leap.trading.api.dto.LoginRequest;
import com.neueda.leap.trading.api.dto.LogoutRequest;
import com.neueda.leap.trading.service.contract.AuthResult;
import com.neueda.leap.trading.service.contract.AuthService;
import com.neueda.leap.trading.service.contract.RegisterResult;

import jakarta.validation.Valid;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody CreateUserRequest request) {
        RegisterResult registerResult = authService.register(request.username(), request.password());
        return Map.of(
            "userId", String.valueOf(registerResult.userId()),
            "username", registerResult.username()
        );
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        AuthResult authResult = authService.login(request.username(), request.password());
        return Map.of(
            "accessToken", authResult.accessToken(),
            "tokenType", "Bearer",
            "expiresAt", authResult.expiresAt().toString()
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.accessToken());
    }
}
