package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.neueda.leap.Main;
import com.neueda.leap.trading.service.impl.OltpToOlapEtlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses only the disposable analytics databases created by verify-admin-postgres.sh. */
@EnabledIfEnvironmentVariable(named = "ADMIN_PG_URL", matches = ".+")
class AnalyticsPostgresTest {
    @Test
    void migrationsEvidenceGrainsCohortsAndEventTimeSegmentsReconcile() throws Exception {
        String sourceUrl = System.getenv("ADMIN_PG_URL").replace("admin_verification", "analytics_verification");
        String reportingUrl = sourceUrl.replace("analytics_verification", "analytics_reporting");
        String savedId, savedPayload;
        int replayId;
        BigDecimal balanceBeforeRestart;
        try (var context = start(sourceUrl, reportingUrl)) {
            var source = context.getBean("oltpJdbcTemplate", JdbcTemplate.class);
            var reporting = context.getBean("olapJdbcTemplate", JdbcTemplate.class);
            reporting.execute(Files.readString(Path.of("olap.sql")));
            // Model the pre-analytics reporting schema, not only a fresh install.
            reporting.execute("ALTER TABLE instruments_info DROP COLUMN dataset_run_id");
            source.execute("CREATE TABLE IF NOT EXISTS employees(employeeid INT PRIMARY KEY, employeeusername VARCHAR(50), employeeroles VARCHAR(20))");
            source.execute("CREATE TABLE IF NOT EXISTS disputed_orders(disputeid INT PRIMARY KEY, requestid INT, employeeid INT, status VARCHAR(20), category VARCHAR(20), reason VARCHAR(75))");
            source.execute("CREATE TABLE IF NOT EXISTS canceled_order(cancel_id INT PRIMARY KEY, request_id INT, reason VARCHAR(50), cancel_date DATE)");
            source.execute("CREATE TABLE IF NOT EXISTS support_ticket(ticket_id INT PRIMARY KEY, accountid INT, reason VARCHAR(75), created_at TIMESTAMP, status VARCHAR(20), assigned_to INT, closed_at TIMESTAMP)");
            source.execute("ALTER TABLE instruments ADD CONSTRAINT legacy_ticker_unique UNIQUE (ticker)");
            for (int replay = 0; replay < 2; replay++) {
                source.execute(Files.readString(Path.of("scripts/analytics-oltp-migration.sql")));
                reporting.execute(Files.readString(Path.of("scripts/analytics-olap-migration.sql")));
            }
            source.update("INSERT INTO users(userid,username,salt,user_hashed_salted_password) VALUES (1,'one','salt','hash'),(2,'two','salt','hash')");
            source.update("""
                INSERT INTO accounts(accountid,userid,availablefunds,creationdate,account_type,currency)
                VALUES (1,1,10000,CURRENT_DATE,'TRADING','GBP'),(2,1,10000,CURRENT_DATE,'TRADING','USD'),
                       (3,2,10000,CURRENT_DATE,'TRADING',NULL)
                """);
            source.update("""
                INSERT INTO instruments(instrumentid,ticker,instrumentname,market,market_data_symbol,
                    quantity_unit,quote_currency,contract_multiplier,product_convention)
                VALUES (1,'ABC','London ABC','UK','ABC.L','SHARE','GBP',1,'PRICE_PER_UNIT'),
                       (2,'ABC','New York ABC','US','ABC.N','SHARE','USD',1,'PRICE_PER_UNIT')
                """);
            source.update("""
                INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,
                    date_request,status,created_at_utc,correlation_id)
                VALUES (1,1,1,'BUY',10,100,'2026-01-10','EXECUTED','2026-01-10T12:00:00Z','one'),
                       (2,3,1,'BUY',5,100,'2026-01-31','REJECTED','2026-01-31T12:00:00Z','two'),
                       (3,2,2,'BUY',1,50,'2026-01-20','EXECUTED','2026-01-20T12:00:00Z','three'),
                       (4,1,1,'BUY',1,10,'2025-01-01','EXECUTED',NULL,'legacy'),
                       (5,1,1,'BUY',1,10,'2026-01-21','PENDING','2026-01-21T12:00:00Z','pending')
                """);
            source.update("""
                INSERT INTO trades_executed(executeid,requestid,askprice,quantity_executed,date_execution,
                    executed_at_utc,fee,client_id,execution_currency,fee_currency,quantity_unit,
                    contract_multiplier,product_convention)
                VALUES (1,1,100,4,'2026-01-10','2026-01-10T12:01:00Z',1,1,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT'),
                       (2,1,102,6,'2026-01-10','2026-01-10T12:02:00Z',1,1,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT'),
                       (3,3,50,1,'2026-01-20','2026-01-20T12:01:00Z',0,1,'USD','USD','SHARE',1,'PRICE_PER_UNIT'),
                       (4,4,10,1,'2025-01-01',NULL,0,NULL,NULL,NULL,NULL,NULL,NULL)
                """);
            source.update("INSERT INTO disputed_orders(disputeid,requestid) VALUES (1,1),(2,1)");
            source.update("""
                INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,
                    event_sequence,event_version,occurred_at_utc,correlation_id)
                SELECT requestid,accountid,instrumentid,'ORDER_SUBMITTED',1,1,created_at_utc,correlation_id
                FROM trade_request WHERE requestid <> 4
                """);
            source.update("""
                INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,
                    event_sequence,event_version,occurred_at_utc,correlation_id)
                SELECT requestid,accountid,instrumentid,'ORDER_ACCEPTED',2,1,created_at_utc,correlation_id
                FROM trade_request WHERE requestid IN (1,3,5)
                """);
            source.update("""
                INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,
                    event_sequence,event_version,occurred_at_utc,correlation_id)
                VALUES (2,3,1,'VALIDATION_REJECTED',2,1,'2026-02-02T12:00:00Z','two')
                """);
            source.update("""
                INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,
                    event_sequence,event_version,occurred_at_utc,correlation_id,quote_source,quote_at_utc,
                    quote_received_at_utc,quote_currency,quote_price_type,quote_price,decision_price)
                VALUES (1,1,1,'PRICING_DECISION',3,1,'2026-01-10T12:00:59Z','one','actual-feed',
                    '2026-01-10T12:00:57Z','2026-01-10T12:00:58Z','GBP','ASK',100,100)
                """);
            source.update("""
                INSERT INTO cash_movements(order_id,account_id,amount,currency,currency_source,
                    movement_type,occurred_at_utc,correlation_id)
                VALUES (1,1,-1014,'GBP','ACCOUNT_METADATA','BUY_SETTLEMENT','2026-01-10T12:02:00Z','one'),
                       (4,1,-10,'USD',NULL,'BUY_SETTLEMENT','2025-01-01T12:00:00Z','legacy')
                """);
            source.update("""
                INSERT INTO client_segment_history(client_id,segment_code,source,effective_from_utc,
                    effective_to_utc,recorded_at_utc)
                VALUES (1,'RETAIL','test-approved-source','2026-01-01Z','2026-01-15Z','2026-01-01Z'),
                       (1,'COMMERCIAL','test-approved-source','2026-01-15Z',NULL,'2026-01-15Z')
                """);
            var etl = context.getBean(OltpToOlapEtlService.class);
            String runId = etl.runFullLoad();
            assertEquals(5L, reporting.queryForObject("SELECT COUNT(*) FROM order_facts", Long.class));
            assertEquals(4L, reporting.queryForObject("SELECT COUNT(*) FROM fill_facts", Long.class));
            var example = reporting.queryForMap("""
                SELECT COUNT(DISTINCT executeid) AS fills, COUNT(DISTINCT requestid) AS orders,
                    SUM(executed_quantity) AS quantity,
                    SUM(execution_price * executed_quantity * contract_multiplier) AS notional
                FROM fill_facts WHERE requestid=1
                """);
            assertEquals(2L, example.get("fills"));
            assertEquals(1L, example.get("orders"));
            assertEquals(10L, example.get("quantity"));
            assertEquals(0, new BigDecimal("1012").compareTo((BigDecimal) example.get("notional")));
            assertEquals(2, reporting.queryForObject("SELECT execution_count FROM trade_facts WHERE requestid=1", Integer.class));
            assertEquals(10, reporting.queryForObject("SELECT quantity_executed FROM trade_facts WHERE requestid=1", Integer.class));
            assertEquals(List.of("GBP", "USD"), reporting.queryForList(
                "SELECT DISTINCT execution_currency FROM fill_facts WHERE execution_currency IS NOT NULL ORDER BY execution_currency", String.class));
            assertEquals(1L, reporting.queryForObject("SELECT COUNT(DISTINCT client_id) FROM fill_facts WHERE executed_at_utc >= '2026-01-01Z' AND executed_at_utc < '2026-02-01Z'", Long.class));
            assertEquals(List.of("COMMERCIAL", "RETAIL"), reporting.queryForList("""
                SELECT s.segment_code FROM fill_facts f
                JOIN client_segment_history s ON s.client_id=f.client_id
                  AND s.effective_from_utc <= f.executed_at_utc
                  AND (s.effective_to_utc IS NULL OR f.executed_at_utc < s.effective_to_utc)
                GROUP BY s.segment_code ORDER BY s.segment_code
                """, String.class));
            assertEquals(4L, reporting.queryForObject("""
                SELECT COUNT(DISTINCT requestid) FROM lifecycle_event_facts
                WHERE event_type='ORDER_SUBMITTED' AND occurred_at_utc >= '2026-01-01Z'
                  AND occurred_at_utc < '2026-02-01Z'
                """, Long.class));
            assertEquals(1L, reporting.queryForObject("""
                SELECT COUNT(DISTINCT s.requestid) FROM lifecycle_event_facts s
                WHERE s.event_type='ORDER_SUBMITTED' AND s.occurred_at_utc >= '2026-01-01Z'
                  AND s.occurred_at_utc < '2026-02-01Z'
                  AND EXISTS (SELECT 1 FROM lifecycle_event_facts r WHERE r.requestid=s.requestid
                    AND r.event_type='VALIDATION_REJECTED' AND r.occurred_at_utc <= '2026-02-03Z')
                """, Long.class), "February rejection still belongs to the January submission cohort");
            assertEquals(1L, reporting.queryForObject("SELECT COUNT(*) FROM fill_facts WHERE executed_at_utc IS NULL AND execution_currency IS NULL AND contract_multiplier IS NULL", Long.class));
            assertEquals(2L, reporting.queryForObject("SELECT COUNT(*) FROM clients_info WHERE registered_at_utc IS NULL", Long.class));
            assertNull(reporting.queryForObject("SELECT currency_source FROM cash_movement_facts WHERE requestid=4", String.class));
            var pricing = reporting.queryForMap("SELECT * FROM lifecycle_event_facts WHERE event_type='PRICING_DECISION'");
            assertEquals("actual-feed", pricing.get("quote_source"));
            assertEquals("GBP", pricing.get("quote_currency"));
            assertNotEquals(pricing.get("quote_at_utc"), pricing.get("quote_received_at_utc"));

            AnalyticsAssertions.verify(context, source, reporting);
            PersonaLoadAssertions.verify(context, source, reporting);
            AnalyticsReleaseAssertions.verify(context, source, reporting);
            long failuresBefore=reporting.queryForObject("SELECT COUNT(*) FROM etl_refresh_runs WHERE status='FAILED'",Long.class);
            runId=reporting.queryForObject("SELECT DISTINCT dataset_run_id FROM fill_facts",String.class);

            // Publication failure must not replace the previous consistent dataset.
            source.update("""
                INSERT INTO client_segment_history(client_id,segment_code,source,effective_from_utc,recorded_at_utc)
                VALUES (1,'OVERLAP','bad-source','2026-01-14Z','2026-01-14Z')
                """);
            assertThrows(IllegalStateException.class, etl::runFullLoad);
            assertEquals(runId, reporting.queryForObject("SELECT DISTINCT dataset_run_id FROM fill_facts", String.class));
            assertEquals(2L, reporting.queryForObject("SELECT COUNT(*) FROM client_segment_history", Long.class));
            assertEquals(failuresBefore+1, reporting.queryForObject("SELECT COUNT(*) FROM etl_refresh_runs WHERE status='FAILED'", Long.class));
            savedId=reporting.queryForObject("SELECT report_id FROM analytics_reports WHERE version=1",String.class);
            savedPayload=reporting.queryForObject("SELECT snapshot::text FROM analytics_reports WHERE report_id=?",String.class,savedId);
            replayId=source.queryForObject("SELECT MAX(requestid) FROM trades_executed",Integer.class);
            balanceBeforeRestart=source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class);
        }
        try (var restarted=start(sourceUrl,reportingUrl)) {
            var source=restarted.getBean("oltpJdbcTemplate",JdbcTemplate.class);
            var reporting=restarted.getBean("olapJdbcTemplate",JdbcTemplate.class);
            assertEquals(savedPayload,reporting.queryForObject("SELECT snapshot::text FROM analytics_reports WHERE report_id=?",String.class,savedId));
            assertEquals(savedId,restarted.getBean(com.neueda.leap.trading.analytics.AnalyticsService.class).report(savedId).get("reportId"));
            assertThrows(IllegalArgumentException.class,()->restarted.getBean(com.neueda.leap.trading.service.impl.TradingOrderService.class).executeOrder(replayId));
            assertEquals(balanceBeforeRestart,source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class));
            assertEquals(1,source.queryForObject("SELECT COUNT(*) FROM trades_executed WHERE requestid=?",Integer.class,replayId));
            assertEquals(1,source.queryForObject("SELECT COUNT(*) FROM cash_movements WHERE order_id=?",Integer.class,replayId));
            assertEquals(1,source.queryForObject("SELECT COUNT(*) FROM holding_movements WHERE order_id=?",Integer.class,replayId));
        }
    }
    private org.springframework.context.ConfigurableApplicationContext start(String sourceUrl,String reportingUrl) {
        return new SpringApplicationBuilder(Main.class).run(
                "--server.port=0", "--spring.config.location=file:src/main/resources/application.yml",
                "--spring.datasource.url=" + sourceUrl, "--spring.datasource.username=postgres",
                "--spring.datasource.password=admin-test-only",
                "--app.datasource.olap.url=" + reportingUrl, "--app.datasource.olap.username=postgres",
                "--app.datasource.olap.password=admin-test-only",
                "--market-data.kafka.enabled=false", "--market-data.execution.kafka.enabled=false",
                "--analytics.segment-disclosure-approved=true", "--etl.schedule.cron=-");
    }
}
