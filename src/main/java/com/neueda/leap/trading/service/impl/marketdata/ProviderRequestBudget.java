package com.neueda.leap.trading.service.impl.marketdata;

import java.nio.file.*;
import java.time.*;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Counts every attempted provider request before sending it, including failures. */
@Component
public class ProviderRequestBudget {
    private final int limit;
    private final Path file;

    public ProviderRequestBudget(
            @Value("${market-data.fauxnance.daily-request-limit:1000}") int limit,
            @Value("${market-data.fauxnance.budget-file:state/fauxnance-budget.properties}") String file) {
        this.limit = Math.max(0, limit);
        this.file = Path.of(file).toAbsolutePath();
    }

    public synchronized void reserve() {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        try {
            Properties budget = new Properties();
            if (Files.exists(file)) {
                try (var input = Files.newInputStream(file)) { budget.load(input); }
            }
            int used = today.equals(budget.getProperty("date"))
                ? Integer.parseInt(budget.getProperty("used", "0")) : 0;
            if (used < 0 || used >= limit) {
                throw new IllegalStateException("Market data daily budget reached; resets at 00:00 UTC");
            }
            budget.setProperty("date", today);
            budget.setProperty("used", Integer.toString(used + 1));
            Files.createDirectories(file.getParent());
            Path pending = Files.createTempFile(file.getParent(), "quote-budget-", ".tmp");
            try {
                try (var output = Files.newOutputStream(pending)) { budget.store(output, "Provider attempts (UTC)"); }
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(pending); }
        } catch (java.io.IOException | NumberFormatException ex) {
            throw new IllegalStateException("Market data budget could not be verified; provider request blocked", ex);
        }
    }
}
