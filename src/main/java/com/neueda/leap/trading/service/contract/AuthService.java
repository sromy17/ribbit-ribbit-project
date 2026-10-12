package com.neueda.leap.trading.service.contract;

import java.util.Optional;

import com.neueda.leap.trading.domain.User;

public interface AuthService {
    RegisterResult register(String username, String password);

    AuthResult login(String username, String password);

    void logout(String accessToken);

    Optional<User> resolveAuthenticatedUser(String accessToken);
}
