package com.neueda.leap.trading.security;

import java.util.Objects;

import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountAccessService {
    private final TradingAccountRepository accountRepository;

    public AccountAccessService(TradingAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public void requireAccountOwner(int accountId) {
        TradingAccount account = getAccount(accountId);
        if (!isCurrentUser(account.getUser().getUserId())) {
            deny();
        }
    }

    public void requireAccountAccess(int accountId, String staffPermission) {
        Authentication authentication = currentAuthentication();
        if (authentication.getAuthorities().stream().anyMatch(a -> a != null && staffPermission.equals(a.getAuthority()))) {
            return;
        }
        requireAccountOwner(accountId);
    }

    public void requireUserAccess(Integer userId, String staffPermission) {
        Authentication authentication = currentAuthentication();
        if (authentication.getAuthorities().stream().anyMatch(a -> a != null && staffPermission.equals(a.getAuthority()))) {
            return;
        }
        if (!isCurrentUser(userId)) {
            deny();
        }
    }

    private TradingAccount getAccount(int accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountId));
    }

    private boolean isCurrentUser(Integer userId) {
        Authentication authentication = currentAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }
        Object claim = jwtAuthentication.getToken().getClaim("uid");
        return claim instanceof Number number && Objects.equals(userId, number.intValue());
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            deny();
        }
        return authentication;
    }

    private void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
    }
}