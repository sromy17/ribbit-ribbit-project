package com.neueda.leap.trading.api;

import com.neueda.leap.trading.domain.User;

public record UserResponse(Integer userId, String username) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getUsername());
    }
}