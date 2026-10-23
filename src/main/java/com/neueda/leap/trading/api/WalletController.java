package com.neueda.leap.trading.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.api.dto.CreateBankAccountRequest;
import com.neueda.leap.trading.api.dto.WalletTransactionRequest;
import com.neueda.leap.trading.api.dto.WalletTransactionResponse;
import com.neueda.leap.trading.domain.Bank;
import com.neueda.leap.trading.domain.BankMovement;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.BankMovementRepository;
import com.neueda.leap.trading.repository.jpa.BankRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/accounts/{accountId}/wallet")
public class WalletController {
    private final BankRepository bankRepository;
    private final BankMovementRepository movementRepository;
    private final TradingAccountRepository accountRepository;

    public WalletController(
        BankRepository bankRepository,
        BankMovementRepository movementRepository,
        TradingAccountRepository accountRepository
    ) {
        this.bankRepository = bankRepository;
        this.movementRepository = movementRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/banks")
    public List<Bank> getBankAccounts(@PathVariable Integer accountId) {
        requireAccount(accountId);
        return bankRepository.findByAccountIdOrderByBankIdAsc(accountId);
    }

    @PostMapping("/banks")
    @ResponseStatus(HttpStatus.CREATED)
    public Bank createBankAccount(
        @PathVariable Integer accountId,
        @Valid @RequestBody CreateBankAccountRequest request
    ) {
        requireAccount(accountId);
        if (bankRepository.existsByAccountIdAndRoutingNumberAndAccountNumber(
            accountId,
            request.routingNumber(),
            request.accountNumber()
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank account is already connected");
        }
        Bank bank = new Bank();
        bank.setBankId(nextBankId());
        bank.setAccountId(accountId);
        bank.setAccountName(request.nickname().trim());
        bank.setRoutingNumber(request.routingNumber());
        bank.setAccountNumber(request.accountNumber());
        bank.setBalance(BigDecimal.ZERO);
        return bankRepository.save(bank);
    }

    @GetMapping("/activity")
    public List<BankMovement> getActivity(@PathVariable Integer accountId) {
        requireAccount(accountId);
        List<Integer> bankIds = bankRepository.findByAccountIdOrderByBankIdAsc(accountId)
            .stream()
            .map(Bank::getBankId)
            .toList();
        return bankIds.isEmpty()
            ? List.of()
            : movementRepository.findByBankIdInOrderByMovementDateDesc(bankIds);
    }

    @PostMapping("/deposit")
    @Transactional
    public WalletTransactionResponse deposit(
        @PathVariable Integer accountId,
        @Valid @RequestBody WalletTransactionRequest request
    ) {
        return moveFunds(accountId, request, true);
    }

    @PostMapping("/withdraw")
    @Transactional
    public WalletTransactionResponse withdraw(
        @PathVariable Integer accountId,
        @Valid @RequestBody WalletTransactionRequest request
    ) {
        return moveFunds(accountId, request, false);
    }

    private WalletTransactionResponse moveFunds(
        Integer accountId,
        WalletTransactionRequest request,
        boolean deposit
    ) {
        TradingAccount account = requireAccount(accountId);
        Bank bank = bankRepository.findById(request.bankId())
            .filter(candidate -> candidate.getAccountId().equals(accountId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

        BigDecimal amount = request.amount();
        if (deposit) {
            account.depositFunds(amount);
        } else {
            if (account.getAvailableFunds().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient cash balance");
            }
            account.withdrawFunds(amount);
            bank.setBalance(bank.getBalance().add(amount));
        }

        BankMovement movement = new BankMovement();
        movement.setMovementId(nextMovementId());
        movement.setBankId(bank.getBankId());
        movement.setAmount(amount);
        movement.setMovementType(deposit ? "DEPOSIT" : "WITHDRAWAL");
        movement.setMovementDate(Instant.now());
        bankRepository.save(bank);
        accountRepository.save(account);
        movementRepository.save(movement);

        return new WalletTransactionResponse(
            movement.getMovementId(),
            bank.getBankId(),
            movement.getMovementType(),
            amount,
            movement.getMovementDate(),
            account.getAvailableFunds()
        );
    }

    private TradingAccount requireAccount(Integer accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private Integer nextBankId() {
        return bankRepository.findAll().stream()
            .map(Bank::getBankId)
            .max(Integer::compareTo)
            .orElse(10000) + 1;
    }

    private Integer nextMovementId() {
        return movementRepository.findAll().stream()
            .map(BankMovement::getMovementId)
            .max(Integer::compareTo)
            .orElse(11000) + 1;
    }
}
