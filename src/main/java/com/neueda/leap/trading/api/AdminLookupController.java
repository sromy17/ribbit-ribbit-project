package com.neueda.leap.trading.api;

import java.util.List;

import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('PERM_USER_LOOKUP')")
public class AdminLookupController {
    private final UserRepository userRepository;
    private final TradingAccountRepository accountRepository;

    public AdminLookupController(UserRepository userRepository, TradingAccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/{userId}")
    public UserResponse user(@PathVariable Integer userId) {
        return userRepository.findById(userId)
            .map(UserResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    @GetMapping("/{userId}/accounts")
    public List<TradingAccount> accounts(@PathVariable Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        }
        return accountRepository.findAllByUserUserIdOrderByAccountIdAsc(userId);
    }
}