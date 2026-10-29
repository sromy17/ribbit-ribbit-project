package com.neueda.leap.trading.service.impl.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UkEquityQuoteClientTest {
    @Test
    void normalizesPenceToPoundsWithoutChangingPercentageOrTimestamp() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new UkEquityQuoteClient(builder.baseUrl("https://query1.finance.yahoo.com").build());
        server.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/HSBA.L?interval=1d&range=1d"))
            .andRespond(withSuccess("""
                {"chart":{"result":[{"meta":{"symbol":"HSBA.L","currency":"GBp",
                "regularMarketPrice":1292.6,"chartPreviousClose":1296.6,"regularMarketTime":1793292529}}]}}
                """, MediaType.APPLICATION_JSON));
        var quote = client.fetchMarketQuote("HSBA.L");
        assertEquals(new BigDecimal("12.926"), quote.price());
        assertEquals(new BigDecimal("-0.040"), quote.change());
        assertEquals("GBP", quote.currency());
        assertEquals(1793292529L, quote.quoteAtUtc().getEpochSecond());
        assertTrue(quote.changePercent().signum() < 0);
        server.verify();
    }

    @Test
    void rejectsUnexpectedCurrencyInsteadOfMislabelingItAsPounds() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new UkEquityQuoteClient(builder.baseUrl("https://query1.finance.yahoo.com").build());
        server.expect(anything()).andRespond(withSuccess("""
            {"chart":{"result":[{"meta":{"symbol":"HSBA.L","currency":"USD",
            "regularMarketPrice":20,"regularMarketTime":1793292529}}]}}
            """, MediaType.APPLICATION_JSON));
        assertThrows(IllegalArgumentException.class, () -> client.fetchMarketQuote("HSBA.L"));
        server.verify();
    }
}
