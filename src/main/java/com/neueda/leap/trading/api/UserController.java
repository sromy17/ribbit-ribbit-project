package com.neueda.leap.trading.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.UserRepository;

import jakarta.validation.Valid;

@RestController
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = new User();
        user.setUserId((int) System.currentTimeMillis() % 1000000); // Simple ID generation
        user.setUsername(request.username());
        user.setSalt("contract-salt");
        user.setUserHashedSaltedPassword(request.password());
        return userRepository.save(user);
    }

    @PutMapping("/users/{userId}")
    public User updateUser(@PathVariable("userId") Integer userId, @Valid @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        user.updateProfile(request.username());
        return userRepository.save(user);
    }
}
