package com.neueda.leap.trading.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
public class AuthController {

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest request) {
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Login endpoint is not implemented until security scheme is finalized"
        );
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody LogoutRequest request) {
        throw new ResponseStatusException(
            HttpStatus.NOT_IMPLEMENTED,
            "Logout endpoint is not implemented until security scheme is finalized"
        );
    }
}
