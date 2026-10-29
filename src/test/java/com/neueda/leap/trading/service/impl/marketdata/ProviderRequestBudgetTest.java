package com.neueda.leap.trading.service.impl.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderRequestBudgetTest {
    @TempDir Path directory;

    @Test void persistsAcrossRestartsAndResetsOnNewUtcDay() throws Exception {
        Path file = directory.resolve("budget.properties");
        new ProviderRequestBudget(1, file.toString()).reserve();
        assertThrows(IllegalStateException.class, () -> new ProviderRequestBudget(1, file.toString()).reserve());
        Files.writeString(file, "date=" + LocalDate.now(ZoneOffset.UTC).minusDays(1) + "\nused=1000\n");
        assertDoesNotThrow(() -> new ProviderRequestBudget(1, file.toString()).reserve());
    }

    @Test void blocksWhenBudgetCannotBeRead() throws Exception {
        Path file = directory.resolve("budget.properties");
        Files.writeString(file, "date=" + LocalDate.now(ZoneOffset.UTC) + "\nused=broken\n");
        assertThrows(IllegalStateException.class, () -> new ProviderRequestBudget(1000, file.toString()).reserve());
    }
}
