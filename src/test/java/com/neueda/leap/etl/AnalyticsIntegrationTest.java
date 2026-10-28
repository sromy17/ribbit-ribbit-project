package com.neueda.leap.etl;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.neueda.leap.trading.service.impl.AnalyticsService;
import com.neueda.leap.trading.service.impl.AnalyticsInsightsService;

@EnabledIfEnvironmentVariable(named = "ANALYTICS_TEST_DATABASE_URL", matches = ".+")
class AnalyticsIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    @Test
    void realEtlPublishesExactAnalyticsAndRollsBackFailures() throws Exception {
        String adminUrl = System.getenv("ANALYTICS_TEST_DATABASE_URL");
        int queryAt = adminUrl.indexOf('?');
        String base = queryAt < 0 ? adminUrl : adminUrl.substring(0, queryAt);
        String parameters = queryAt < 0 ? "" : adminUrl.substring(queryAt);
        String prefix = base.substring(0, base.lastIndexOf('/') + 1);
        String id = UUID.randomUUID().toString().replace("-", "");
        String sourceDb = "analytics_source_" + id;
        String reportingDb = "analytics_reporting_" + id;
        var admin = new JdbcTemplate(new DriverManagerDataSource(adminUrl));
        admin.execute("CREATE DATABASE " + sourceDb);
        try {
            admin.execute("CREATE DATABASE " + reportingDb);
            try {
                var sourceDs = new DriverManagerDataSource(prefix + sourceDb + parameters);
                var reportingDs = new DriverManagerDataSource(prefix + reportingDb + parameters);
                var source = new JdbcTemplate(sourceDs);
                var reporting = new JdbcTemplate(reportingDs);
                source.execute(Files.readString(Path.of("basedSchema.sql")));
                source.execute(Files.readString(Path.of("insert-data.sql")));
                reporting.execute(Files.readString(Path.of("olap.sql")));
                source.update("""
                    INSERT INTO trade_request (requestid, accountid, instrumentid, side, quantity, bidprice, date_request, status)
                    VALUES (2008, 101, 1001, 'BUY', 10, 100, '2026-10-28T10:00:00Z', 'PENDING')
                    """);
                source.update("""
                    INSERT INTO trades_executed (executeid, requestid, askprice, date_execution, quantity_executed)
                    VALUES (3010, 2001, 100, CURRENT_TIMESTAMP, 3), (3011, 2001, 200, CURRENT_TIMESTAMP, 7)
                    """);
                source.update("""
                    INSERT INTO disputed_orders (disputeid, requestid, status, category, reason)
                    VALUES (4010, 2001, 'COMPLETED', 'Accident', 'Another closed claim'),
                           (4011, 2001, 'DISPUTED', 'Fraud', 'Open claim')
                    """);
                new ApplicationContextRunner()
                    .withPropertyValues("olap.enabled=true")
                    .withUserConfiguration(Transactions.class)
                    .withBean("oltpJdbcTemplate", JdbcTemplate.class, () -> source)
                    .withBean("olapJdbcTemplate", JdbcTemplate.class, () -> reporting)
                    .withBean("olapTransactionManager", DataSourceTransactionManager.class,
                        () -> new DataSourceTransactionManager(reportingDs))
                    .withBean(OlapEtlService.class)
                    .withBean(AnalyticsService.class)
                    .withBean(AnalyticsInsightsService.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var etl = context.getBean(OlapEtlService.class);
                        var analytics = context.getBean(AnalyticsService.class);
                        assertThat(analytics.report().summary().orders()).isZero();
                        assertThat(analytics.report().summary().medianOrderSize()).isNull();
                        assertThat(analytics.report().summary().executionRate()).isNull();
                        assertThat(analytics.report().lastSyncedAt()).isNull();
                        assertThat(etl.runNow().successful()).isTrue();
                        var report = analytics.report();
                        var stats = report.summary();
                        assertThat(stats.orders()).isEqualTo(8);
                        assertThat(stats.executedOrders()).isEqualTo(4);
                        assertThat(stats.fills()).isEqualTo(6);
                        assertThat(stats.executedShares()).isEqualTo(58);
                        assertThat(stats.buyOrders()).isEqualTo(6);
                        assertThat(stats.sellOrders()).isEqualTo(2);
                        assertThat(stats.meanOrderSize()).isEqualByComparingTo("13.125");
                        assertThat(stats.medianOrderSize()).isEqualByComparingTo("11");
                        assertThat(stats.modeOrderSizes()).containsExactly(10);
                        assertThat(stats.modeFrequency()).isEqualTo(2);
                        assertThat(stats.executionRate()).isEqualByComparingTo("50");
                        assertThat(stats.activeClients()).isEqualTo(5);
                        assertThat(stats.totalAccounts()).isEqualTo(5);
                        assertThat(stats.availableCash()).isEqualByComparingTo("975000");
                        assertThat(stats.supportTickets()).isEqualTo(3);
                        assertThat(stats.openSupportTickets()).isEqualTo(2);
                        assertThat(stats.ordersWithDisputes()).isEqualTo(3);
                        assertThat(stats.ordersWithOpenDisputes()).isEqualTo(3);
                        assertThat(report.lastSyncedAt()).isNotNull();
                        assertThat(report.topStocks()).extracting(AnalyticsService.Stock::symbol)
                            .containsExactly("MSFT", "AAPL", "META", "GOOGL");
                        assertThat(report.topStocks().get(0).executedShares()).isEqualTo(25);
                        assertThat(report.topStocks().get(1).buyExecutedShares()).isEqualTo(20);
                        assertThat(report.topStocks().get(3).sellExecutedShares()).isEqualTo(5);
                        assertThat(report.dailyActivity().get(0).date()).isEqualTo("2026-10-28");
                        assertThat(reporting.queryForObject("SELECT disputeid FROM trade_facts WHERE requestid=2001", Integer.class))
                            .isEqualTo(4011);
                        var insights = report.insights();
                        assertThat(insights.funding().deposits()).isEqualByComparingTo("75000");
                        assertThat(insights.funding().withdrawals()).isEqualByComparingTo("2500");
                        assertThat(insights.funding().netDeposits()).isEqualByComparingTo("72500");
                        assertThat(insights.funding().depositCount()).isEqualTo(3);
                        assertThat(insights.funding().depositingClients()).isEqualTo(3);
                        assertThat(insights.funding().meanDeposit()).isEqualByComparingTo("25000");
                        assertThat(insights.funding().medianDeposit()).isEqualByComparingTo("25000");
                        assertThat(insights.depositDistribution().get(3).deposits()).isEqualTo(3);
                        assertThat(insights.execution().requestedShares()).isEqualTo(105);
                        assertThat(insights.execution().shareFillRate()).isEqualByComparingTo("55.24");
                        assertThat(insights.execution().outcomes()).extracting(AnalyticsInsightsService.Outcome::orders)
                            .containsExactly(4L, 0L, 1L, 2L, 1L);
                        assertThat(insights.participation().newAccounts()).isEqualTo(5);
                        assertThat(insights.participation().tradingAccountRate()).isEqualByComparingTo("100");
                        assertThat(insights.weeks().stream().mapToLong(AnalyticsInsightsService.Week::newAccounts).sum()).isEqualTo(5);
                        assertThat(insights.weeks().stream().map(AnalyticsInsightsService.Week::netDeposits)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)).isEqualByComparingTo("72500");
                        assertThat(insights.stockActivity()).anySatisfy(row -> {
                            assertThat(row.symbol()).isEqualTo("MSFT");
                            assertThat(row.orders()).isEqualTo(1);
                        });

                        source.execute("ALTER TABLE support_ticket RENAME TO support_ticket_unavailable");
                        assertThat(etl.runNow().successful()).isFalse();
                        assertThat(analytics.report().summary()).isEqualTo(stats);
                        source.execute("ALTER TABLE support_ticket_unavailable RENAME TO support_ticket");

                        // All tied most-frequent values are returned; each unique size is not a mode.
                        reporting.update("UPDATE trade_facts SET quantity_requested = 25 WHERE requestid = 2007");
                        assertThat(analytics.report().summary().modeOrderSizes()).containsExactly(10, 25);
                        reporting.update("UPDATE trade_facts SET quantity_requested = requestid");
                        assertThat(analytics.report().summary().modeOrderSizes()).isEmpty();
                        assertThat(analytics.report().summary().modeFrequency()).isZero();

                        Callable<Boolean> refresh = () -> etl.runNow().successful();
                        var executor = Executors.newFixedThreadPool(2);
                        try {
                            Callable<Boolean> scheduled = () -> { etl.runScheduled(); return true; };
                            var results = executor.invokeAll(List.of(refresh, scheduled));
                            assertThat(results.get(0).get()).isTrue();
                            assertThat(results.get(1).get()).isTrue();

                            // A report must wait until an in-progress publisher releases its transaction lock.
                            try (var connection = reportingDs.getConnection(); var statement = connection.createStatement()) {
                                connection.setAutoCommit(false);
                                statement.execute("SELECT pg_advisory_xact_lock(724182031)");
                                Callable<AnalyticsService.Report> readReport = analytics::report;
                                var pendingReport = executor.submit(readReport);
                                try {
                                    assertThatThrownBy(() -> pendingReport.get(200, TimeUnit.MILLISECONDS))
                                        .isInstanceOf(TimeoutException.class);
                                } finally {
                                    connection.commit();
                                }
                                assertThat(pendingReport.get(5, TimeUnit.SECONDS).summary().orders()).isEqualTo(8);
                            }
                        } finally {
                            executor.shutdownNow();
                        }
                        assertThat(analytics.report().summary().orders()).isEqualTo(8);
                        assertThat(source.queryForObject("SELECT COUNT(*) FROM trade_request", Integer.class)).isEqualTo(8);
                        verifyInsightBoundaries(reporting, analytics);
                    });
            } finally {
                admin.execute("DROP DATABASE " + reportingDb + " WITH (FORCE)");
            }
        } finally {
            admin.execute("DROP DATABASE " + sourceDb + " WITH (FORCE)");
        }
    }

    private static void verifyInsightBoundaries(JdbcTemplate reporting, AnalyticsService analytics) {
        reporting.execute("TRUNCATE public.trade_facts, public.accounts_info, public.bank_movement_facts");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var start = java.sql.Timestamp.from(today.minusDays(29).atStartOfDay().toInstant(ZoneOffset.UTC));
        var end = java.sql.Timestamp.from(today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        var old = java.sql.Timestamp.from(start.toInstant().minusSeconds(1));
        reporting.update("""
            INSERT INTO accounts_info(accountid,userid,creationdate) VALUES (1,1,?),(2,1,?),(3,2,?)
            """, old, start, end);
        double[] amounts = { 99, 100, 999, 1000, 9999, 10000, 99999, 100000 };
        for (int i = 0; i < amounts.length; i++) {
            reporting.update("""
                INSERT INTO bank_movement_facts(movement_id,bankid,accountid,userid,amount,movement_type,movement_date)
                VALUES (?,1,?,1,?,'DEPOSIT',?)
                """, i + 1, i % 2 + 1, amounts[i], start);
        }
        reporting.update("""
            INSERT INTO bank_movement_facts(movement_id,bankid,accountid,userid,amount,movement_type,movement_date)
            VALUES (20,1,1,1,300000,'WITHDRAWAL',?),(21,1,1,1,999999,'DEPOSIT',?),
                   (22,1,1,1,999999,'DEPOSIT',?)
            """, start, old, end);
        for (int i = 1; i <= 11; i++) {
            reporting.update("""
                INSERT INTO trade_facts(requestid,accountid,userid,ticker,instrumentname,side,
                  quantity_requested,quantity_executed,order_status,date_request)
                VALUES (?,1,1,?,?,'BUY',10,5,'PENDING',?)
                """, i, "STOCK" + String.format("%02d", i), "Test stock", start);
        }
        reporting.update("""
            INSERT INTO trade_facts(requestid,accountid,userid,ticker,instrumentname,side,
              quantity_requested,quantity_executed,order_status,date_request)
            VALUES (20,2,1,'OLD','Old','SELL',100,100,'EXECUTED',?),
                   (21,2,1,'FUTURE','Future','BUY',100,100,'EXECUTED',?),
                   (22,2,1,'CANCEL','Cancelled','SELL',10,2,'CANCELLED',?)
            """, old, end, start);
        var insights = analytics.report(AnalyticsInsightsService.Period.LAST_30_DAYS).insights();
        assertThat(insights.fromDate()).isEqualTo(today.minusDays(29).toString());
        assertThat(insights.toDate()).isEqualTo(today.toString());
        assertThat(insights.funding().depositCount()).isEqualTo(8);
        assertThat(insights.funding().depositingClients()).isEqualTo(1);
        assertThat(insights.funding().deposits()).isEqualByComparingTo("222196");
        assertThat(insights.funding().withdrawals()).isEqualByComparingTo("300000");
        assertThat(insights.funding().netDeposits()).isEqualByComparingTo("-77804");
        assertThat(insights.funding().meanDeposit()).isEqualByComparingTo("27774.5");
        assertThat(insights.funding().medianDeposit()).isEqualByComparingTo("5499.5");
        assertThat(insights.depositDistribution()).extracting(AnalyticsInsightsService.Bucket::deposits)
            .containsExactly(1L, 2L, 2L, 2L, 1L);
        assertThat(insights.participation().eligibleAccounts()).isEqualTo(2);
        assertThat(insights.participation().newAccounts()).isEqualTo(1);
        assertThat(insights.participation().tradingAccounts()).isEqualTo(2);
        assertThat(insights.participation().tradingAccountRate()).isEqualByComparingTo("100");
        assertThat(insights.execution().outcomes()).extracting(AnalyticsInsightsService.Outcome::orders)
            .containsExactly(0L, 11L, 0L, 0L, 1L);
        assertThat(insights.execution().shareFillRate()).isEqualByComparingTo("47.5");
        assertThat(insights.topStocks()).extracting(AnalyticsService.Stock::symbol)
            .containsExactly("STOCK01", "STOCK02", "STOCK03", "STOCK04", "STOCK05",
                "STOCK06", "STOCK07", "STOCK08", "STOCK09", "STOCK10");
        assertThat(insights.stockActivity()).hasSize(10);
        assertThat(insights.weeks().get(0).deposits()).isEqualByComparingTo("222196");
        assertThat(insights.weeks()).anySatisfy(week -> {
            assertThat(week.deposits()).isZero();
            assertThat(week.meanDeposit()).isNull();
            assertThat(week.tradingAccounts()).isZero();
        });
        reporting.execute("TRUNCATE public.trade_facts, public.accounts_info, public.bank_movement_facts");
        var empty = analytics.report(AnalyticsInsightsService.Period.LAST_90_DAYS).insights();
        assertThat(empty.funding().deposits()).isZero();
        assertThat(empty.funding().medianDeposit()).isNull();
        assertThat(empty.execution().shareFillRate()).isNull();
        assertThat(empty.participation().tradingAccountRate()).isNull();
        assertThat(empty.topStocks()).isEmpty();
        assertThat(empty.stockActivity()).isEmpty();
        assertThat(empty.weeks()).allSatisfy(week -> {
            assertThat(week.deposits()).isZero();
            assertThat(week.tradingAccountRate()).isNull();
        });
    }
}
