package com.neueda.leap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Objects;

import com.neueda.leap.trading.service.impl.marketdata.QuoteSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.repository.jpa.UserRepository;

@SpringBootTest(properties = {
    "spring.config.location=file:src/main/resources/application.yml,file:src/test/resources/application.yml",
    "market-data.kafka.enabled=false",
    "market-data.execution.kafka.enabled=false",
    "etl.schedule.cron=-"
})
class ApplicationContextTest {
    @Autowired
    private Environment environment;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Test
    void jpaRepositoryHasWorkingTransactionManager() {
        assertDoesNotThrow(() -> users.count());
    }

    @Test
    void configurationPreservesEscapedMapKeys() {
        Binder binder = Binder.get(Objects.requireNonNull(environment));
        var jpa = binder.bind(
            "spring.jpa.properties", Bindable.mapOf(String.class, String.class)).get();
        var logging = binder.bind(
            "logging.level", Bindable.mapOf(String.class, String.class)).get();

        assertEquals("true", jpa.get("hibernate.format_sql"));
        assertEquals("INFO", logging.get("com.neueda.leap"));
        assertNotNull(environment.getProperty("app.datasource.olap.url"));
    }

    @Test
    void quoteSnapshotsRoundTripWithJackson3() {
        QuoteSnapshot snapshot = new QuoteSnapshot("AAPL", new BigDecimal("123.45"), 123456789L, "test");
        String json = objectMapper.writeValueAsString(snapshot);
        assertEquals(snapshot, objectMapper.readValue(json, QuoteSnapshot.class));
    }
}
