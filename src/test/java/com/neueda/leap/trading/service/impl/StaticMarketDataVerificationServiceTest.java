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
