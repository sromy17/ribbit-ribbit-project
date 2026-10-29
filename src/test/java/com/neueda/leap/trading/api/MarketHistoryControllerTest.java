package com.neueda.leap.trading.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.neueda.leap.trading.service.impl.marketdata.ProviderRequestBudget;

class MarketHistoryControllerTest {
    @TempDir Path temp;

    @Test
    void cachesHistoryAndConvertsPenceWhileSkippingMissingSessions() {
        var builder = RestClient.builder().baseUrl("https://query1.finance.yahoo.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var controller = new MarketHistoryController(builder.build(), new ProviderRequestBudget(1, temp.resolve("budget").toString()));
        server.expect(requestTo("https://query1.finance.yahoo.com/v8/finance/chart/HSBA.L?interval=1d&range=1mo"))
            .andRespond(withSuccess("""
              {"chart":{"result":[{"meta":{"symbol":"HSBA.L","currency":"GBp"},
              "timestamp":[1793000000,1793100000,1793200000],
              "indicators":{"quote":[{"close":[1200,null,1292.6]}]}}]}}
              """, MediaType.APPLICATION_JSON));
        var history = controller.history("hsba.l");
        assertEquals("GBP", history.currency());
        assertEquals(2, history.points().size());
        assertEquals(new BigDecimal("12.926"), history.points().getLast().price());
        assertSame(history, controller.history("HSBA.L"));
        server.verify();
    }

    @Test
    void leavesRupeePricesInTheirNativeUnits() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var envelope = mapper.readValue("""
          {"chart":{"result":[{"meta":{"symbol":"TCS.NS","currency":"INR"},
          "timestamp":[1793000000,1793100000],"indicators":{"quote":[{"close":[2460,2478]}]}}]}}
          """, MarketHistoryController.Envelope.class);
        var history = MarketHistoryController.parse("TCS.NS", envelope);
        assertEquals("INR", history.currency());
        assertEquals(new BigDecimal("2478"), history.points().getLast().price());
        assertThrows(IllegalArgumentException.class, () -> MarketHistoryController.parse("HSBA.L", envelope));
    }
}
