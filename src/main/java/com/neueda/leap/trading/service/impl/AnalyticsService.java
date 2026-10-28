package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    public record Summary(long orders, long executedOrders, long fills, long executedShares,
        long buyOrders, long sellOrders, long activeClients, long activeAccounts, long totalAccounts,
        long rejectedOrders, long cancelledOrders, long pendingOrders, BigDecimal executionRate,
        BigDecimal meanOrderSize, BigDecimal medianOrderSize, List<Integer> modeOrderSizes,
        long modeFrequency, BigDecimal availableCash, long supportTickets, long openSupportTickets,
        long ordersWithDisputes, long ordersWithOpenDisputes, Instant firstOrderAt, Instant lastOrderAt) {}
    public record Stock(String symbol, String name, long orders, long executedShares,
        long buyExecutedShares, long sellExecutedShares, long clients) {}
    public record Day(String date, long orders, long executedShares) {}
    public record Report(Instant retrievedAt, Instant lastSyncedAt, Summary summary,
        List<Stock> topStocks, List<Day> dailyActivity, AnalyticsInsightsService.Insights insights) {}

    private final JdbcTemplate olap;
    private final AnalyticsInsightsService insights;

    public AnalyticsService(@Qualifier("olapJdbcTemplate") JdbcTemplate olap, AnalyticsInsightsService insights) {
        this.olap = olap;
        this.insights = insights;
    }

    @Transactional(transactionManager = "olapTransactionManager", readOnly = true)
    public Report report() {
        return report(AnalyticsInsightsService.Period.ALL);
    }

    @Transactional(transactionManager = "olapTransactionManager", readOnly = true)
    public Report report(AnalyticsInsightsService.Period period) {
        // All queries see one published copy, never a mixture of two ETL runs.
        olap.execute("SELECT pg_advisory_xact_lock_shared(724182031)");
        Map<String, Object> row = olap.queryForMap("""
            SELECT COUNT(*) AS orders,
              COUNT(*) FILTER (WHERE quantity_executed > 0) AS executed_orders,
              COALESCE(SUM(execution_count), 0) AS fills,
              COALESCE(SUM(quantity_executed), 0) AS executed_shares,
              COUNT(*) FILTER (WHERE TRIM(side) = 'BUY') AS buy_orders,
              COUNT(*) FILTER (WHERE TRIM(side) = 'SELL') AS sell_orders,
              COUNT(DISTINCT userid) AS active_clients, COUNT(DISTINCT accountid) AS active_accounts,
              COUNT(*) FILTER (WHERE order_status = 'REJECTED') AS rejected_orders,
              COUNT(*) FILTER (WHERE order_status = 'CANCELLED') AS cancelled_orders,
              COUNT(*) FILTER (WHERE order_status = 'PENDING') AS pending_orders,
              AVG(quantity_requested) AS mean_size,
              PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY quantity_requested) AS median_size,
              COUNT(*) FILTER (WHERE disputeid IS NOT NULL) AS disputed_orders,
              COUNT(*) FILTER (WHERE dispute_status IN ('DISPUTED', 'UNDER-REVIEW')) AS open_disputes,
              MIN(date_request) AS first_order, MAX(date_request) AS last_order,
              MAX(last_synced_at) AS synced
            FROM public.trade_facts
            """);
        Map<String, Object> accounts = olap.queryForMap("""
            SELECT COUNT(*) AS total_accounts, COALESCE(SUM(availablefunds), 0) AS available_cash
            FROM public.accounts_info
            """);
        Map<String, Object> support = olap.queryForMap("""
            SELECT COUNT(*) AS tickets, COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS')) AS open_tickets
            FROM public.employees_info
            """);
        List<Map<String, Object>> modes = olap.queryForList("""
            WITH frequencies AS (
              SELECT quantity_requested AS size, COUNT(*) AS frequency FROM public.trade_facts
              WHERE quantity_requested IS NOT NULL GROUP BY quantity_requested
            )
            SELECT size, frequency FROM frequencies
            WHERE frequency = (SELECT MAX(frequency) FROM frequencies) AND frequency > 1 ORDER BY size
            """);
        long orders = number(row, "orders");
        BigDecimal rate = orders == 0 ? null : BigDecimal.valueOf(number(row, "executed_orders"))
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP);
        Summary summary = new Summary(orders, number(row, "executed_orders"), number(row, "fills"),
            number(row, "executed_shares"), number(row, "buy_orders"), number(row, "sell_orders"),
            number(row, "active_clients"), number(row, "active_accounts"), number(accounts, "total_accounts"),
            number(row, "rejected_orders"), number(row, "cancelled_orders"), number(row, "pending_orders"),
            rate, decimal(row.get("mean_size")), decimal(row.get("median_size")),
            modes.stream().map(mode -> ((Number) mode.get("size")).intValue()).toList(),
            modes.isEmpty() ? 0 : number(modes.get(0), "frequency"),
            decimal(accounts.get("available_cash")), number(support, "tickets"), number(support, "open_tickets"),
            number(row, "disputed_orders"), number(row, "open_disputes"),
            instant(row.get("first_order")), instant(row.get("last_order")));
        List<Stock> stocks = olap.query("""
            SELECT ticker, MAX(instrumentname) AS name, COUNT(*) AS orders,
              COALESCE(SUM(quantity_executed), 0) AS shares,
              COALESCE(SUM(quantity_executed) FILTER (WHERE TRIM(side) = 'BUY'), 0) AS buys,
              COALESCE(SUM(quantity_executed) FILTER (WHERE TRIM(side) = 'SELL'), 0) AS sells,
              COUNT(DISTINCT userid) AS clients
            FROM public.trade_facts GROUP BY ticker HAVING SUM(quantity_executed) > 0
            ORDER BY shares DESC, orders DESC, ticker ASC LIMIT 10
            """, (rs, i) -> new Stock(rs.getString("ticker"), rs.getString("name"), rs.getLong("orders"),
                rs.getLong("shares"), rs.getLong("buys"), rs.getLong("sells"), rs.getLong("clients")));
        List<Day> days = olap.query("""
            SELECT (date_request AT TIME ZONE 'UTC')::DATE AS day, COUNT(*) AS orders,
              COALESCE(SUM(quantity_executed), 0) AS shares
            FROM public.trade_facts WHERE date_request IS NOT NULL
            GROUP BY day ORDER BY day DESC LIMIT 30
            """, (rs, i) -> new Day(rs.getString("day"), rs.getLong("orders"), rs.getLong("shares")));
        Instant retrievedAt = Instant.now();
        return new Report(retrievedAt, instant(row.get("synced")), summary, stocks, days,
            insights.calculate(period, retrievedAt));
    }

    private static long number(Map<String, Object> values, String key) {
        return ((Number) values.get(key)).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static Instant instant(Object value) {
        return value == null ? null : ((Timestamp) value).toInstant();
    }
}
