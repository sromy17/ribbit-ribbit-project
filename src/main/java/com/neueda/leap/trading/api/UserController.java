package com.neueda.leap.trading.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.neueda.leap.trading.security.AccountAccessService;

import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.UserRepository;

import jakarta.validation.Valid;

@RequestMapping("/users")
@RestController
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountAccessService accountAccessService;

    public UserController(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AccountAccessService accountAccessService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountAccessService = accountAccessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = new User();
        user.setUserId((int) System.currentTimeMillis() % 1000000); // Simple ID generation
        user.setUsername(request.username());
        user.setSalt("contract-salt");
        user.setUserHashedSaltedPassword(passwordEncoder.encode(request.password()));
        return UserResponse.from(userRepository.save(user));
    }

    @PutMapping("/{userId}")
    public UserResponse updateUser(@PathVariable("userId") int userId, @Valid @RequestBody UpdateUserRequest request) {
        accountAccessService.requireUserAccess(userId, "PERM_ACCOUNT_DETAILS");
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        user.updateProfile(request.username());
        return UserResponse.from(userRepository.save(user));
    }
}
