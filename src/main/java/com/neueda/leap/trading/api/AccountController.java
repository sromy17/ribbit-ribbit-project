package com.neueda.leap.trading.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.api.dto.CreateAccountRequest;
import com.neueda.leap.trading.api.dto.UpdateAccountRequest;
import com.neueda.leap.trading.api.dto.UpdateBalanceRequest;
import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;

import jakarta.validation.Valid;

@RequestMapping("/accounts")
@RestController
public class AccountController {
    private final TradingAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;

    public AccountController(
        TradingAccountRepository accountRepository,
        UserRepository userRepository,
        HoldingRepository holdingRepository,
        TradeRepository tradeRepository
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradingAccount createAccount(@Valid @RequestBody CreateAccountRequest request) {
        int userId = request.userId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        int nextAccountId = generateAccountId();
        TradingAccount account = new TradingAccount();
        account.setAccountId(nextAccountId);
        account.setUser(user);
        account.setType(request.accountType());
        account.setAvailableFunds(BigDecimal.ZERO);
        account.setCreationDate(LocalDate.now());
        return accountRepository.save(account);
    }

    private int generateAccountId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
            if (!accountRepository.existsById(candidate)) {
                return candidate;
            }
        }

        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to allocate an account id");
    }

    @GetMapping("/{accountId}")
    public TradingAccount getAccount(@PathVariable("accountId") int accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }

        return accountRepository.findById(accountId).get();
    }

    @GetMapping("/user/{userId}")
    public List<TradingAccount> getAccountsForUser(@PathVariable("userId") Integer userId) {
        return accountRepository.findAllByUserUserIdOrderByAccountIdAsc(userId);
    }

    @PutMapping("/{accountId}")
    public TradingAccount updateAccount(
        @PathVariable("accountId") Integer accountId,
        @Valid @RequestBody UpdateAccountRequest request
    ) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }
        TradingAccount account = accountRepository.findById(accountId).get();

        account.setType(request.accountType());
        return accountRepository.save(account);
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable("accountId") Integer accountId) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }
        accountRepository.deleteById(accountId);
    }

    @GetMapping("/{accountId}/balance")
    public Map<String, Object> getBalance(@PathVariable("accountId") Integer accountId) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }
        TradingAccount account = accountRepository.findById(accountId).get();

        return Map.of(
            "accountId", account.getAccountId(),
            "cashBalance", account.getAvailableFunds()
        );
    }

    @PutMapping("/{accountId}/balance")
    public Map<String, Object> updateBalance(
        @PathVariable("accountId") Integer accountId,
        @Valid @RequestBody UpdateBalanceRequest request
    ) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }
        TradingAccount account = accountRepository.findById(accountId).get();

        account.setAvailableFunds(request.cashBalance());
        TradingAccount saved = accountRepository.save(account);

        return Map.of(
            "accountId", saved.getAccountId(),
            "cashBalance", saved.getAvailableFunds()
        );
    }

    @GetMapping("/{accountId}/holdings")
    public List<Holding> getHoldings(@PathVariable("accountId") Integer accountId) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }

        return holdingRepository.findByAccountAccountId(accountId);
    }

    @GetMapping("/{accountId}/trades")
    public List<Trade> getTrades(@PathVariable("accountId") Integer accountId) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }

        return tradeRepository.findByOrderAccountAccountId(accountId);
    }
}
