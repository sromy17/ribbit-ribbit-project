package com.neueda.leap.trading.api;

import java.util.Locale;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.service.contract.MarketDataVerificationService;
import com.neueda.leap.trading.service.contract.MarketQuote;

@RequestMapping("/market")
@RestController
public class MarketController {
    private final MarketDataVerificationService marketDataService;

    public MarketController(MarketDataVerificationService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/quotes/{symbol}")
    public MarketQuote getQuote(@PathVariable("symbol") String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        try {
            return marketDataService.getQuote(normalized);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                unavailableReason(ex, "Market data provider is unavailable for " + normalized),
                ex
            );
        }
    }

    private String unavailableReason(Throwable error, String fallback) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && (cause.getMessage().startsWith("Market data daily quota exhausted;") || cause.getMessage().startsWith("Market data daily budget reached;"))) {
                return cause.getMessage();
            }
        }
        return fallback;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ProblemDetail marketError(ResponseStatusException error) {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason());
    }

    @GetMapping("/quotes")
    public List<MarketQuote> getQuotes(@RequestParam("symbols") String symbols) {
        try {
            List<String> requested = Arrays.stream(symbols == null ? new String[0] : symbols.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
            return marketDataService.getQuotes(requested);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Market data unavailable", ex);
        }
    }
}
