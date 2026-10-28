package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsInsightsService {
    public enum Period { ALL, LAST_30_DAYS, LAST_90_DAYS }
    public record Funding(BigDecimal deposits, BigDecimal withdrawals, BigDecimal netDeposits,
        long depositCount, long depositingClients, BigDecimal meanDeposit, BigDecimal medianDeposit) {}
    public record Bucket(String label, long deposits) {}
    public record Outcome(String label, long orders, BigDecimal percentage) {}
    public record Execution(long requestedShares, long executedShares, BigDecimal shareFillRate,
        List<Outcome> outcomes) {}
    public record Participation(long newAccounts, long eligibleAccounts, long tradingAccounts,
        BigDecimal tradingAccountRate) {}
    public record Week(String week, BigDecimal deposits, BigDecimal withdrawals, BigDecimal netDeposits,
        long depositingClients, BigDecimal meanDeposit, BigDecimal medianDeposit,
        long newAccounts, long eligibleAccounts, long tradingAccounts, BigDecimal tradingAccountRate) {}
    public record StockWeek(String week, String symbol, long orders, long clients) {}
    public record Insights(Period period, String fromDate, String toDate, Funding funding,
        List<Bucket> depositDistribution, List<AnalyticsService.Stock> topStocks,
        List<StockWeek> stockActivity, Execution execution, Participation participation, List<Week> weeks) {}

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsInsightsService(@Qualifier("olapJdbcTemplate") JdbcTemplate olap) {
        this.jdbc = new NamedParameterJdbcTemplate(olap);
    }

    // Called inside AnalyticsService's reporting transaction and shared publisher lock.
    public Insights calculate(Period period, Instant retrievedAt) {
        LocalDate today = retrievedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate start = switch (period) {
            case LAST_30_DAYS -> today.minusDays(29);
            case LAST_90_DAYS -> today.minusDays(89);
            case ALL -> jdbc.queryForObject("""
                SELECT MIN(day) FROM (
                  SELECT MIN((movement_date AT TIME ZONE 'UTC')::date) AS day FROM public.bank_movement_facts
                  UNION ALL SELECT MIN((date_request AT TIME ZONE 'UTC')::date) FROM public.trade_facts
                  UNION ALL SELECT MIN((creationdate AT TIME ZONE 'UTC')::date) FROM public.accounts_info
                ) dates
                """, Map.of(), (rs, i) -> rs.getObject(1, LocalDate.class));
        };
        if (start == null || start.isAfter(today)) start = today;
        var params = Map.<String, Object>of(
            "start", Timestamp.from(start.atStartOfDay().toInstant(ZoneOffset.UTC)),
            "end", Timestamp.from(today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        var funding = jdbc.queryForMap("""
            SELECT COALESCE(SUM(amount) FILTER (WHERE movement_type='DEPOSIT'),0) AS deposits,
              COALESCE(SUM(amount) FILTER (WHERE movement_type='WITHDRAWAL'),0) AS withdrawals,
              COUNT(*) FILTER (WHERE movement_type='DEPOSIT') AS deposit_count,
              COUNT(DISTINCT userid) FILTER (WHERE movement_type='DEPOSIT') AS clients,
              AVG(amount) FILTER (WHERE movement_type='DEPOSIT') AS mean,
              PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amount)
                FILTER (WHERE movement_type='DEPOSIT') AS median
            FROM public.bank_movement_facts WHERE movement_date >= :start AND movement_date < :end
            """, params);
        BigDecimal deposits = decimal(funding.get("deposits"));
        BigDecimal withdrawals = decimal(funding.get("withdrawals"));
        var distribution = jdbc.query("""
            WITH buckets(id, label) AS (VALUES (0,'Under $100'),(1,'$100 to under $1,000'),
              (2,'$1,000 to under $10,000'),(3,'$10,000 to under $100,000'),(4,'$100,000 and over')),
            deposits AS (
              SELECT CASE WHEN amount<100 THEN 0 WHEN amount<1000 THEN 1 WHEN amount<10000 THEN 2
                WHEN amount<100000 THEN 3 ELSE 4 END AS id
              FROM public.bank_movement_facts
              WHERE movement_type='DEPOSIT' AND movement_date >= :start AND movement_date < :end
            )
            SELECT b.label, COUNT(d.id) AS deposits FROM buckets b LEFT JOIN deposits d USING(id)
            GROUP BY b.id,b.label ORDER BY b.id
            """, params, (rs, i) -> new Bucket(rs.getString("label"), rs.getLong("deposits")));
        var stocks = jdbc.query("""
            SELECT ticker, MAX(instrumentname) AS name, COUNT(*) AS orders,
              COALESCE(SUM(quantity_executed),0) AS shares,
              COALESCE(SUM(quantity_executed) FILTER (WHERE TRIM(side)='BUY'),0) AS buys,
              COALESCE(SUM(quantity_executed) FILTER (WHERE TRIM(side)='SELL'),0) AS sells,
              COUNT(DISTINCT userid) AS clients
            FROM public.trade_facts WHERE date_request >= :start AND date_request < :end
            GROUP BY ticker HAVING SUM(quantity_executed)>0 ORDER BY shares DESC,orders DESC,ticker LIMIT 10
            """, params, (rs, i) -> new AnalyticsService.Stock(rs.getString("ticker"), rs.getString("name"),
                rs.getLong("orders"), rs.getLong("shares"), rs.getLong("buys"), rs.getLong("sells"), rs.getLong("clients")));
        var stockActivity = jdbc.query("""
            SELECT DATE_TRUNC('week',date_request AT TIME ZONE 'UTC')::date AS week,ticker,
              COUNT(*) AS orders,COUNT(DISTINCT userid) AS clients
            FROM public.trade_facts WHERE date_request >= :start AND date_request < :end
            GROUP BY week,ticker ORDER BY week,ticker
            """, params, (rs, i) -> new StockWeek(rs.getString("week"), rs.getString("ticker"),
                rs.getLong("orders"), rs.getLong("clients")))
            .stream().filter(row -> stocks.stream().anyMatch(stock -> stock.symbol().equals(row.symbol()))).toList();
        var totals = jdbc.queryForMap("""
            SELECT COALESCE(SUM(quantity_requested::bigint),0) AS requested,
              COALESCE(SUM(quantity_executed::bigint),0) AS executed
            FROM public.trade_facts WHERE date_request >= :start AND date_request < :end
            """, params);
        var outcomeCounts = jdbc.queryForList("""
            SELECT CASE WHEN order_status='REJECTED' THEN 'Rejected'
              WHEN order_status='CANCELLED' THEN 'Cancelled'
              WHEN quantity_requested>0 AND quantity_executed>=quantity_requested THEN 'Fully filled'
              WHEN quantity_executed>0 THEN 'Partially filled' ELSE 'Unfilled' END AS outcome, COUNT(*) AS orders
            FROM public.trade_facts WHERE date_request >= :start AND date_request < :end GROUP BY outcome
            """, params);
        long orderCount = outcomeCounts.stream().mapToLong(row -> count(row, "orders")).sum();
        var outcomes = List.of("Fully filled", "Partially filled", "Unfilled", "Rejected", "Cancelled").stream()
            .map(label -> {
                long n = outcomeCounts.stream().filter(row -> label.equals(row.get("outcome")))
                    .mapToLong(row -> count(row, "orders")).sum();
                return new Outcome(label, n, percent(n, orderCount));
            }).toList();
        var participation = jdbc.queryForMap("""
            SELECT COUNT(*) FILTER (WHERE creationdate >= :start) AS new_accounts,
              COUNT(*) AS eligible_accounts,
              COUNT(*) FILTER (WHERE EXISTS (
                SELECT 1 FROM public.trade_facts t WHERE t.accountid=a.accountid
                AND t.date_request >= :start AND t.date_request < :end)) AS trading_accounts
            FROM public.accounts_info a WHERE creationdate < :end
            """, params);
        var weeklyFunding = jdbc.queryForList("""
            SELECT DATE_TRUNC('week',movement_date AT TIME ZONE 'UTC')::date AS week,
              COALESCE(SUM(amount) FILTER (WHERE movement_type='DEPOSIT'),0) AS deposits,
              COALESCE(SUM(amount) FILTER (WHERE movement_type='WITHDRAWAL'),0) AS withdrawals,
              COUNT(DISTINCT userid) FILTER (WHERE movement_type='DEPOSIT') AS clients,
              AVG(amount) FILTER (WHERE movement_type='DEPOSIT') AS mean,
              PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amount)
                FILTER (WHERE movement_type='DEPOSIT') AS median
            FROM public.bank_movement_facts WHERE movement_date >= :start AND movement_date < :end GROUP BY week
            """, params);
        var weeklyTrading = jdbc.queryForList("""
            SELECT DATE_TRUNC('week',date_request AT TIME ZONE 'UTC')::date AS week,
              COUNT(DISTINCT accountid) AS accounts FROM public.trade_facts
            WHERE date_request >= :start AND date_request < :end GROUP BY week
            """, params);
        List<LocalDate> creationDates = jdbc.query("""
            SELECT (creationdate AT TIME ZONE 'UTC')::date AS day
            FROM public.accounts_info WHERE creationdate < :end
            """, params, (rs, i) -> rs.getObject("day", LocalDate.class));
        var moneyByWeek = indexWeeks(weeklyFunding);
        var tradingByWeek = indexWeeks(weeklyTrading);
        List<Week> weeks = new ArrayList<>();
        for (LocalDate week = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            !week.isAfter(today); week = week.plusWeeks(1)) {
            LocalDate from = week.isBefore(start) ? start : week;
            LocalDate until = week.plusWeeks(1).isAfter(today.plusDays(1)) ? today.plusDays(1) : week.plusWeeks(1);
            var money = moneyByWeek.get(week.toString());
            var trading = tradingByWeek.get(week.toString());
            long eligible = creationDates.stream().filter(day -> day.isBefore(until)).count();
            long created = creationDates.stream().filter(day -> !day.isBefore(from) && day.isBefore(until)).count();
            long active = trading == null ? 0 : count(trading, "accounts");
            BigDecimal paidIn = money == null ? BigDecimal.ZERO : decimal(money.get("deposits"));
            BigDecimal paidOut = money == null ? BigDecimal.ZERO : decimal(money.get("withdrawals"));
            weeks.add(new Week(week.toString(), paidIn, paidOut, paidIn.subtract(paidOut),
                money == null ? 0 : count(money, "clients"), money == null ? null : decimal(money.get("mean")),
                money == null ? null : decimal(money.get("median")), created, eligible, active, percent(active, eligible)));
        }
        return new Insights(period, start.toString(), today.toString(),
            new Funding(deposits, withdrawals, deposits.subtract(withdrawals), count(funding, "deposit_count"),
                count(funding, "clients"), decimal(funding.get("mean")), decimal(funding.get("median"))),
            distribution, stocks, stockActivity,
            new Execution(count(totals, "requested"), count(totals, "executed"),
                percent(count(totals, "executed"), count(totals, "requested")), outcomes),
            new Participation(count(participation, "new_accounts"), count(participation, "eligible_accounts"),
                count(participation, "trading_accounts"),
                percent(count(participation, "trading_accounts"), count(participation, "eligible_accounts"))), weeks);
    }

    private static Map<String, Map<String, Object>> indexWeeks(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        rows.forEach(row -> result.put(row.get("week").toString(), row));
        return result;
    }

    private static long count(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? null : new BigDecimal(value.toString());
    }

    private static BigDecimal percent(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
