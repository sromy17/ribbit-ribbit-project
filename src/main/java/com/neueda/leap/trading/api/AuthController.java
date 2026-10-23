package com.neueda.leap.trading.api;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.neueda.leap.trading.api.dto.LoginRequest;
import com.neueda.leap.trading.api.dto.LogoutRequest;
import com.neueda.leap.trading.service.impl.AuthenticationService;

@RestController
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.LoginResult result = authenticationService.login(request);
        return response(result);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request.accessToken());
        return Map.of("status", "revoked");
    }
    @PostMapping("/refresh")
    public Map<String,Object> refresh(@RequestBody Map<String,String> request) { return response(authenticationService.refresh(request.get("refreshToken"))); }
    private Map<String,Object> response(AuthenticationService.LoginResult result) {
        return Map.of("token",result.accessToken(),"expiresAt",result.expiresAt().toString(),"expiresInSeconds",result.expiresInSeconds(),
            "userId",result.userId(),"username",result.username(),"firstName",result.firstName()==null?"":result.firstName(),
            "lastName",result.lastName()==null?"":result.lastName(),"refreshToken",result.refreshToken(),"tokenType","Bearer");
    }

}
