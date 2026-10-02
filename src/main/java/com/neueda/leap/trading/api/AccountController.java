package com.neueda.leap.trading.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;

import jakarta.validation.Valid;

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

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public TradingAccount createAccount(@Valid @RequestBody CreateAccountRequest request) {
        User user = userRepository.findById(request.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + request.userId()));

        TradingAccount account = new TradingAccount();
        account.setUser(user);
        account.setType(request.accountType());
        account.setAvailableFunds(request.initialCashBalance());
        account.setCreationDate(LocalDate.now());
        return accountRepository.save(account);
    }

    @GetMapping("/accounts/{accountId}")
    public TradingAccount getAccount(@PathVariable("accountId") Integer accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
    }

    @PutMapping("/accounts/{accountId}")
    public TradingAccount updateAccount(
        @PathVariable("accountId") Integer accountId,
        @Valid @RequestBody UpdateAccountRequest request
    ) {
        TradingAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));

        account.setType(request.accountType());
        return accountRepository.save(account);
    }

    @DeleteMapping("/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable("accountId") Integer accountId) {
        TradingAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));

        accountRepository.delete(account);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public Map<String, Object> getBalance(@PathVariable("accountId") Integer accountId) {
        TradingAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));

        return Map.of(
            "accountId", account.getAccountId(),
            "cashBalance", account.getAvailableFunds()
        );
    }

    @PutMapping("/accounts/{accountId}/balance")
    public Map<String, Object> updateBalance(
        @PathVariable("accountId") Integer accountId,
        @Valid @RequestBody UpdateBalanceRequest request
    ) {
        TradingAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));

        account.setAvailableFunds(request.cashBalance());
        TradingAccount saved = accountRepository.save(account);

        return Map.of(
            "accountId", saved.getAccountId(),
            "cashBalance", saved.getAvailableFunds()
        );
    }

    @GetMapping("/accounts/{accountId}/holdings")
    public List<Holding> getHoldings(@PathVariable("accountId") Integer accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }

        return holdingRepository.findByAccountAccountId(accountId);
    }

    @GetMapping("/accounts/{accountId}/trades")
    public List<Trade> getTrades(@PathVariable("accountId") Integer accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId);
        }

        return tradeRepository.findByOrderAccountAccountId(accountId);
    }
}
