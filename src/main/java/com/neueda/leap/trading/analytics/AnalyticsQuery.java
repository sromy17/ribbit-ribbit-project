package com.neueda.leap.trading.analytics;

import java.time.*;
import java.time.temporal.ChronoUnit;

/** Calendar dates are inclusive in the selected zone; SQL boundaries are half-open UTC instants. */
public record AnalyticsQuery(LocalDate from, LocalDate to, String timezone, Integer instrumentId,
                             String side, String segment, int page, int size, String sort, String direction) {
    public AnalyticsQuery {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= 366)
            throw new IllegalArgumentException("Choose a period of 1 to 366 days");
        try { ZoneId.of(timezone); } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid timezone"); }
        if (instrumentId != null && instrumentId < 1) throw new IllegalArgumentException("Invalid instrument ID");
        if (side != null && !java.util.Set.of("BUY", "SELL").contains(side)) throw new IllegalArgumentException("Invalid side");
        if (segment != null && (segment.isBlank() || segment.length() > 60)) throw new IllegalArgumentException("Invalid segment");
        if (page < 0 || page > 10000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        if (!java.util.Set.of("fills", "instrument", "period", "segment").contains(sort)) throw new IllegalArgumentException("Invalid sort");
        if (!java.util.Set.of("asc", "desc").contains(direction)) throw new IllegalArgumentException("Invalid sort direction");
    }
    public Instant start() { return from.atStartOfDay(ZoneId.of(timezone)).toInstant(); }
    public Instant end() { return to.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant(); }
}
