package com.neueda.leap.trading.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.neueda.leap.trading.service.contract.MarketQuote;
import com.neueda.leap.trading.service.impl.marketdata.*;

class BatchMarketQuotesTest {
    @Test void normalizesDeduplicatesAndReturnsOnlyActualProviderQuotes() {
        var provider = mock(FauxnanceQuoteClient.class);
        when(provider.fetchMarketQuote("AAPL")).thenReturn(new MarketQuote("AAPL",new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO));
        when(provider.fetchMarketQuote("BAD")).thenThrow(new IllegalStateException("Unavailable"));
        var service = new StaticMarketDataVerificationService(provider,new QuoteCache(),mock(KafkaQuoteProducer.class),1500);
        try {
            var result=service.getQuotes(List.of("aapl","AAPL","BAD"));
            assertEquals(1,result.size());assertEquals("AAPL",result.getFirst().symbol());
            verify(provider,times(1)).fetchMarketQuote("AAPL");
            assertThrows(IllegalStateException.class,()->service.getQuotes(List.of("BAD")));
            assertThrows(IllegalArgumentException.class,()->service.getQuotes(List.of()));
            assertThrows(IllegalArgumentException.class,()->service.getQuotes(java.util.Collections.nCopies(51,"AAPL")));
            assertThrows(IllegalArgumentException.class,()->service.getQuotes(List.of("../bad?")));
        } finally { service.stopQuoteWorkers(); }
    }
}
