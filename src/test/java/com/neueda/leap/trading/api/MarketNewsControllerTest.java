package com.neueda.leap.trading.api;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MarketNewsControllerTest {
    @Test void validatesMissingConfigurationAndCompanyRanges() {
        var news = new MarketNewsController("https://example.invalid", "");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->news.news("general")).getStatusCode().value());
    }

    @Test void keepsProviderKeyOnServerCachesRealArticlesAndDoesNotInventFailures() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/news",exchange -> {
            requests.incrementAndGet();
            assertEquals("server-only-key",exchange.getRequestHeaders().getFirst("X-Finnhub-Token"));
            byte[] bytes = "[{\"id\":1,\"headline\":\"Fixture news\",\"datetime\":1700000000,\"url\":\"https://example.com/news\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var news = new MarketNewsController("http://127.0.0.1:"+server.getAddress().getPort(),"server-only-key");
            assertEquals("Fixture news",news.news("general").getFirst().get("headline"));
            assertEquals(1,news.news("general").size());assertEquals(1,requests.get());
            assertEquals(400,assertThrows(ResponseStatusException.class,()->news.company("../bad",LocalDate.now(),LocalDate.now())).getStatusCode().value());
            assertEquals(400,assertThrows(ResponseStatusException.class,()->news.company("AAPL",LocalDate.now().minusDays(32),LocalDate.now())).getStatusCode().value());
            assertEquals(503,assertThrows(ResponseStatusException.class,()->news.company("AAPL",LocalDate.now().minusDays(1),LocalDate.now())).getStatusCode().value());
        } finally { server.stop(0); }
    }
}
