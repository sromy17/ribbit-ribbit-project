package com.neueda.leap.trading.analytics;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalyticsQueryTest {
    private AnalyticsQuery day(String date) {
        return new AnalyticsQuery(LocalDate.parse(date),LocalDate.parse(date),"America/New_York",null,null,null,0,25,"period","asc");
    }
    @Test void springAndAutumnDaysRespectDaylightSaving() {
        assertEquals(23,Duration.between(day("2026-03-08").start(),day("2026-03-08").end()).toHours());
        assertEquals(25,Duration.between(day("2026-11-01").start(),day("2026-11-01").end()).toHours());
    }
    @Test void leapDayEndsAtNextCalendarMidnight() {
        var q=day("2024-02-29");assertEquals(Instant.parse("2024-03-01T05:00:00Z"),q.end());
    }
    @Test void oversizedOrReversedPeriodsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(LocalDate.parse("2026-01-01"),LocalDate.parse("2027-01-02"),"UTC",null,null,null,0,25,"fills","desc"));
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(LocalDate.parse("2026-02-01"),LocalDate.parse("2026-01-01"),"UTC",null,null,null,0,25,"fills","desc"));
    }
    @Test void rejectsUnsupportedSideAndPagination() {
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(LocalDate.now(),LocalDate.now(),"UTC",null,"BUY OR TRUE",null,0,25,"fills","desc"));
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(LocalDate.now(),LocalDate.now(),"UTC",null,null,null,0,101,"fills","desc"));
    }
}
