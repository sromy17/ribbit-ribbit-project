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
        var news = new MarketNewsController("https://example.invalid", "", "http://127.0.0.1:1/feed");
        assertEquals(503,assertThrows(ResponseStatusException.class,()->news.news("general")).getStatusCode().value());
    }

    @Test void publicFeedWorksWithoutAKeyAndFiltersInvalidAndOutOfRangeEvidence() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var requests=new AtomicInteger();
        server.createContext("/feed", exchange->{
            requests.incrementAndGet();
            assertNull(exchange.getRequestHeaders().getFirst("X-Finnhub-Token"));
            byte[] bytes="""
                <rss><channel>
                <item><title>Recorded headline</title><link>https://example.com/one</link><pubDate>Mon, 26 Oct 2026 12:00:00 GMT</pubDate><description>Publisher summary</description></item>
                <item><title>Duplicate</title><link>https://example.com/one</link><pubDate>Mon, 26 Oct 2026 12:00:00 GMT</pubDate></item>
                <item><title>Old</title><link>https://example.com/old</link><pubDate>Sun, 25 Oct 2026 12:00:00 GMT</pubDate></item>
                <item><title>No date</title><link>https://example.com/no-date</link></item>
                <item><title>Unsafe</title><link>javascript:alert(1)</link><pubDate>Mon, 26 Oct 2026 12:00:00 GMT</pubDate></item>
                </channel></rss>
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var news=new MarketNewsController("https://example.invalid","","http://127.0.0.1:"+server.getAddress().getPort()+"/feed");
            var articles=news.company("AAPL",LocalDate.of(2026,10,26),LocalDate.of(2026,10,26));
            assertEquals(1,articles.size());
            assertEquals("Recorded headline",articles.getFirst().get("headline"));
            assertEquals("Yahoo Finance RSS",articles.getFirst().get("source"));
            assertEquals(1793016000L,articles.getFirst().get("datetime"));
            assertEquals(articles,news.company("AAPL",LocalDate.of(2026,10,26),LocalDate.of(2026,10,26)));
            assertEquals(1,requests.get());
        } finally { server.stop(0); }
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
