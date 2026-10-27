package com.neueda.leap.trading.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import com.neueda.leap.trading.domain.*;
import com.neueda.leap.trading.service.contract.MarketQuote;
import com.neueda.leap.trading.service.impl.marketdata.*;
import org.junit.jupiter.api.Test;

class StaticMarketDataVerificationServiceTest {
    @Test
    void concurrentDisplayRequestsShareRefreshButExecutionRequiresFreshPrice() throws Exception {
        var provider = mock(FauxnanceQuoteClient.class);
        var service = new StaticMarketDataVerificationService(provider, new QuoteCache(), mock(KafkaQuoteProducer.class), 1500);
        var oldQuote = new MarketQuote("AAPL", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
            "feed", Instant.now().minusSeconds(120), Instant.now().minusSeconds(120), "USD", "LAST");
        when(provider.fetchMarketQuote("AAPL")).thenReturn(oldQuote);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> workers.submit(() -> service.getQuote("aapl"))).toList();
            for (var future : futures) assertEquals(oldQuote, future.get());
            assertEquals(oldQuote, service.getQuote("AAPL"));
            verify(provider, times(1)).fetchMarketQuote("AAPL");
            var instrument = new Instrument();
            instrument.setTicker("AAPL");
            var order = new Order();
            order.setInstrument(instrument);
            service.verifyExecutionQuote(order);
            verify(provider, times(2)).fetchMarketQuote("AAPL");
        } finally { service.stopQuoteWorkers(); }
    }

    @Test
    void liveAndCachedExecutionPreserveSameProvenanceAndQualifiedSymbol() {
        var provider = mock(FauxnanceQuoteClient.class);
        var publisher = mock(KafkaQuoteProducer.class);
        var cache = new QuoteCache();
        var service = new StaticMarketDataVerificationService(provider, cache, publisher, 60_000);
        var instrument = new Instrument();
        instrument.setTicker("ABC");
        instrument.setMarketDataSymbol("ABC.L");
        var order = new Order();
        order.setInstrument(instrument);
        Instant received = Instant.now();
        var live = new MarketQuote("ABC.L", BigDecimal.TEN, null, null, "actual-feed",
            received.minusSeconds(2), received, "GBP", "ASK");
        when(provider.fetchMarketQuote("ABC.L")).thenReturn(live);

        assertEquals(live, service.verifyExecutionQuote(order));
        var cached = service.verifyExecutionQuote(order);
        assertEquals(live.source(), cached.source());
        assertEquals(live.quoteAtUtc(), cached.quoteAtUtc());
        assertEquals(live.receivedAtUtc().toEpochMilli(), cached.receivedAtUtc().toEpochMilli());
        assertEquals(live.currency(), cached.currency());
        assertEquals(live.priceType(), cached.priceType());
        verify(provider, times(1)).fetchMarketQuote("ABC.L");
        verify(publisher).produce(QuoteSnapshot.from(live));
    }

}
