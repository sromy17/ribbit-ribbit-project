package com.neueda.leap.trading.service.impl.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuoteCacheTest {
    @Test void outOfOrderAndEqualTimestampDeliveriesDoNotReplaceNewerEvidence() {
        var cache=new QuoteCache();long time=System.currentTimeMillis()-100;
        var newest=new QuoteSnapshot("ABC",BigDecimal.TEN,time,"first");cache.upsert(newest);
        cache.upsert(new QuoteSnapshot("ABC",BigDecimal.ONE,time-1,"late"));
        cache.upsert(new QuoteSnapshot("ABC",BigDecimal.ONE,time,"duplicate"));
        assertEquals(newest,cache.getFreshSnapshot("ABC",Duration.ofSeconds(5)).orElseThrow());
    }
    @Test void futureClockAndNonPositivePricesDoNotPoisonLastGoodQuote() {
        var cache=new QuoteCache();long time=System.currentTimeMillis()-10;
        var good=new QuoteSnapshot("ABC",BigDecimal.TEN,time,"good");cache.upsert(good);
        cache.upsert(new QuoteSnapshot("ABC",BigDecimal.ONE,time+60000,"clock-skew"));
        cache.upsert(new QuoteSnapshot("ABC",BigDecimal.ZERO,time+1,"invalid"));
        assertEquals(good,cache.getFreshSnapshot("ABC",Duration.ofSeconds(5)).orElseThrow());
    }
    @Test void staleOrMissingQuotesAreUnavailableRatherThanZero() {
        var cache=new QuoteCache();cache.upsert(new QuoteSnapshot("ABC",BigDecimal.TEN,System.currentTimeMillis()-10000,"old"));
        assertTrue(cache.getFreshPrice("ABC",Duration.ofMillis(10)).isEmpty());
        assertTrue(cache.getFreshPrice("missing",Duration.ofSeconds(1)).isEmpty());
    }
}
