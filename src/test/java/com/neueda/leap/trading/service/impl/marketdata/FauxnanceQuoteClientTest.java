package com.neueda.leap.trading.service.impl.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class FauxnanceQuoteClientTest {
    private MockRestServiceServer server;
    private FauxnanceQuoteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FauxnanceQuoteClient(builder, JsonMapper.builder().build(), "https://quotes.test", "test-key");
    }

    @Test
    void fullQuotePreservesChangeFieldsAndNormalizesSymbol() {
        server.expect(requestTo("https://quotes.test/quotes/AAPL"))
            .andExpect(header("X-Api-Key", "test-key"))
            .andRespond(withSuccess("""
                {"data":{"price":123.45,"change":-1.25,"changePercent":-1.0}}
                """, MediaType.APPLICATION_JSON));
        var quote = client.fetchMarketQuote(" aapl ");
        assertEquals("AAPL", quote.symbol());
        assertEquals(new BigDecimal("123.45"), quote.price());
        assertEquals(new BigDecimal("-1.25"), quote.change());
        assertEquals(new BigDecimal("-1.0"), quote.changePercent());
        server.verify();
    }

    @Test
    void priceOnlyCallerStillWorksWithoutOptionalChangeFields() {
        server.expect(requestTo("https://quotes.test/quotes/AAPL"))
            .andRespond(withSuccess("{\"data\":{\"price\":123.45}}", MediaType.APPLICATION_JSON));
        assertEquals(new BigDecimal("123.45"), client.fetchQuote("AAPL"));
        server.verify();
    }

    @Test
    void invalidPriceIsRejected() {
        server.expect(requestTo("https://quotes.test/quotes/AAPL"))
            .andRespond(withSuccess("{\"data\":{\"price\":0}}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalArgumentException.class, () -> client.fetchMarketQuote("AAPL"));
        server.verify();
    }
}
