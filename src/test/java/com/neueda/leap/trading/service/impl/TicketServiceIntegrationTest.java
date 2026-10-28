package com.neueda.leap.trading.service.impl;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfEnvironmentVariable(named = "TICKET_TEST_DATABASE_URL", matches = ".+")
class TicketServiceIntegrationTest {
    private static JdbcTemplate admin;
    private static JdbcTemplate jdbc;
    private static TicketService service;
    private static String schema;

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv("TICKET_TEST_DATABASE_URL");
        schema = "ticket_test_" + UUID.randomUUID().toString().replace("-", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(url));
        admin.execute("CREATE SCHEMA " + schema);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema));
        jdbc.execute(Files.readString(Path.of("basedSchema.sql")));
        jdbc.execute(Files.readString(Path.of("insert-data.sql")));
        service = new TicketService(jdbc);
    }

    @AfterAll
    static void tearDown() {
        if (admin != null && schema != null) admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void createsAndResolvesAnOrderDisputeWithoutChangingFinancialRecords() {
        var before = jdbc.queryForList("SELECT * FROM accounts WHERE accountid = 101");
        var orders = jdbc.queryForList("SELECT * FROM trade_request WHERE accountid = 101");
        var holdings = jdbc.queryForList("SELECT * FROM holdings ORDER BY holdingid");
        var trades = jdbc.queryForList("SELECT * FROM trades_executed ORDER BY executeid");
        int id = service.createDispute(investor(1), 101, 2006, "Accident", "  Please review this order  ");
        var ticket = service.list("dispute", 101).stream().filter(t -> t.ticketId() == id).findFirst().orElseThrow();
        assertThat(ticket.reason()).isEqualTo("Please review this order");
        assertThat(ticket.symbol()).isEqualTo("META");
        assertThat(ticket.userId()).isEqualTo(1);
        assertThat(ticket.status()).isEqualTo("DISPUTED");
        assertThat(ticket.createdAt()).isNull();
        service.update("dispute", id, "UNDER-REVIEW", "DISPUTED", admin());
        service.update("dispute", id, "COMPLETED", "UNDER-REVIEW", admin());
        var closed = service.list("dispute", 101).stream().filter(t -> t.ticketId() == id).findFirst().orElseThrow();
        assertThat(closed.status()).isEqualTo("COMPLETED");
        assertThat(closed.employeeId()).isEqualTo(1);
        assertThat(closed.employeeUsername()).isEqualTo("admin_user");
        assertThat(jdbc.queryForList("SELECT * FROM accounts WHERE accountid = 101")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM trade_request WHERE accountid = 101")).isEqualTo(orders);
        assertThat(jdbc.queryForList("SELECT * FROM holdings ORDER BY holdingid")).isEqualTo(holdings);
        assertThat(jdbc.queryForList("SELECT * FROM trades_executed ORDER BY executeid")).isEqualTo(trades);
    }

    @Test
    void createsGeneralSupportAndClosesItWithEmployeeAndTimestamp() {
        int id = service.createSupport(investor(2), 102, "Help with account access");
        service.update("support", id, "IN_PROGRESS", "OPEN", admin());
        service.update("support", id, "RESOLVED", "IN_PROGRESS", admin());
        var ticket = service.list("support", 102).stream().filter(t -> t.ticketId() == id).findFirst().orElseThrow();
        assertThat(ticket.createdAt()).isNotNull();
        assertThat(ticket.closedAt()).isNotNull();
        assertThat(ticket.status()).isEqualTo("RESOLVED");
        assertThat(ticket.orderId()).isNull();
        assertThat(service.list("support", 101)).noneMatch(t -> t.ticketId() == id);
    }

    @Test
    void forbidsOtherInvestorsAccountsEmployeesAndMismatchedOrders() {
        assertThatThrownBy(() -> service.createSupport(investor(2), 101, "Not my account"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> service.createDispute(investor(1), 101, 2002, "Fraud", "Wrong account"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> service.createSupport(admin(), 101, "Staff cannot file"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void onlyOneConcurrentOpenDisputeIsCreatedPerOrder() throws Exception {
        Callable<String> submit = () -> {
            try {
                service.createDispute(investor(5), 105, 2005, "Accident", "Concurrent dispute");
                return "created";
            } catch (ResponseStatusException error) {
                return error.getStatusCode().toString();
            }
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.of(submit, submit));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                .containsExactlyInAnyOrder("created", "409 CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disputed_orders WHERE requestid = 2005", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void rejectsStaleAndInvalidStatusChangesAndCannotReopenClosedTickets() {
        int id = service.createSupport(investor(1), 101, "Status transition test");
        service.update("support", id, "IN_PROGRESS", "OPEN", admin());
        assertThatThrownBy(() -> service.update("support", id, "RESOLVED", "OPEN", admin()))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(() -> service.update("support", id, "INVALID", "IN_PROGRESS", admin()))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        service.update("support", id, "RESOLVED", "IN_PROGRESS", admin());
        assertThatThrownBy(() -> service.update("support", id, "IN_PROGRESS", "RESOLVED", admin()))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    private static Jwt investor(int id) {
        return Jwt.withTokenValue("investor-test").header("alg", "HS256")
            .subject("investor").claim("uid", id).build();
    }

    private static Jwt admin() {
        return Jwt.withTokenValue("admin-test").header("alg", "HS256").subject("admin_user")
            .claim("principal_type", "employee").claim("eid", 1).claim("roles", List.of("ADMIN")).build();
    }
}
