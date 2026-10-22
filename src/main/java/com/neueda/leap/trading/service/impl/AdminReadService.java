package com.neueda.leap.trading.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminReadService {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate olap;

    public AdminReadService(@Qualifier("olapJdbcTemplate") JdbcTemplate olap) {
        this.olap = olap;
    }

    public Map<String, Object> orders(String status, String symbol, Integer accountId,
                                      LocalDate from, LocalDate to, int page, int size, String sort, String direction) {
        validateRange(from, to);
        if (page < 0) throw new IllegalArgumentException("Page must not be negative");
        int boundedSize = boundedSize(size);
        String orderBy = "requestid".equalsIgnoreCase(sort) ? "requestid" : "created_at_utc";
        String orderDirection = "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) { where.append(" AND order_status = ?"); args.add(status.trim().toUpperCase()); }
        if (symbol != null && !symbol.isBlank()) { where.append(" AND instrumentid IN (SELECT instrumentid FROM instruments_info WHERE ticker = ?)"); args.add(symbol.trim().toUpperCase()); }
        if (accountId != null) { where.append(" AND accountid = ?"); args.add(accountId); }
        if (from != null) { where.append(" AND created_at_utc >= ?"); args.add(from.atStartOfDay()); }
        if (to != null) { where.append(" AND created_at_utc < ?"); args.add(to.plusDays(1).atStartOfDay()); }

        Integer total = olap.queryForObject("SELECT COUNT(*) FROM order_facts" + where, Integer.class, args.toArray());
        args.add(boundedSize);
        args.add((long) page * boundedSize);
        List<Map<String, Object>> rows = olap.queryForList(
            "SELECT requestid, accountid, instrumentid, side, quantity_requested, bidprice, order_status, " +
            "created_at_utc, correlation_id FROM order_facts" + where +
            " ORDER BY " + orderBy + " " + orderDirection + ", requestid " + orderDirection + " LIMIT ? OFFSET ?",
            args.toArray());
        return page(rows, total == null ? 0 : total, page, boundedSize, from, to);
    }

    public Map<String, Object> order(Integer orderId) {
        List<Map<String, Object>> rows = olap.queryForList("SELECT * FROM order_facts WHERE requestid = ?", orderId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Order not found: " + orderId);
        return protect(rows.get(0));
    }

    public List<Map<String, Object>> events(Integer orderId) {
        return olap.queryForList("SELECT * FROM lifecycle_event_facts WHERE requestid = ? ORDER BY event_sequence, event_id", orderId)
            .stream().map(this::protect).toList();
    }

    public List<Map<String, Object>> fills(Integer orderId) {
        return olap.queryForList("SELECT * FROM fill_facts WHERE requestid = ? ORDER BY executed_at_utc, executeid", orderId)
            .stream().map(this::protect).toList();
    }

    public Map<String, Object> accountSummary(Integer accountId) {
        List<Map<String, Object>> accounts = olap.queryForList("SELECT * FROM accounts_info WHERE accountid = ?", accountId);
        if (accounts.isEmpty()) throw new IllegalArgumentException("Account not found: " + accountId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("account", protect(accounts.get(0)));
        response.put("holdings", olap.queryForList("SELECT * FROM holdings_info WHERE accountid = ? ORDER BY instrumentid", accountId));
        response.put("asOf", latestPublication());
        return response;
    }

    public Map<String, Object> activity(LocalDate from, LocalDate to, String currency) {
        validateRange(from, to);
        if (currency != null && !currency.isBlank()) throw new IllegalArgumentException("Currency-converted reporting is not supported");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instruments", olap.queryForList("SELECT f.instrumentid, i.ticker, COUNT(*) AS fill_count, SUM(f.executed_quantity) AS executed_quantity FROM fill_facts f LEFT JOIN instruments_info i ON i.instrumentid=f.instrumentid WHERE f.executed_at_utc >= ? AND f.executed_at_utc < ? GROUP BY f.instrumentid, i.ticker ORDER BY fill_count DESC, f.instrumentid", from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        result.put("orders", olap.queryForList("SELECT order_status, COUNT(*) AS order_count FROM order_facts WHERE created_at_utc >= ? AND created_at_utc < ? GROUP BY order_status ORDER BY order_status", from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        result.put("from", from.toString()); result.put("to", to.toString());
        result.put("asOf", latestPublication());
        result.put("valuation", "UNAVAILABLE: quote currency metadata is not available for all fills");
        return result;
    }

    public Map<String, Object> reportingStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        var latest = olap.queryForList("SELECT run_id, status, started_at FROM etl_refresh_runs ORDER BY started_at DESC LIMIT 1");
        var success = olap.queryForList("SELECT published_at, source_watermark FROM etl_refresh_runs WHERE status='SUCCEEDED' ORDER BY published_at DESC LIMIT 1");
        result.put("status", latest.isEmpty() ? "UNAVAILABLE" : latest.get(0).get("status"));
        if (!success.isEmpty()) {
            result.putAll(success.get(0));
            for (String key : List.of("source_watermark", "published_at")) {
                Object time = result.get(key);
                if (time instanceof java.sql.Timestamp t) result.put(key, t.toInstant().toString());
            }
        }
        Object value = success.isEmpty() ? null : success.get(0).get("source_watermark");
        java.time.Instant watermark = value instanceof java.sql.Timestamp t ? t.toInstant() : null;
        long age = watermark == null ? -1 : java.time.Duration.between(watermark, java.time.Instant.now()).getSeconds();
        result.put("ageSeconds", age);
        result.put("stale", age < 0 || age > 900);
        result.put("staleThresholdSeconds", 900);
        return result;
    }

    private Map<String, Object> page(List<Map<String, Object>> rows, int total, int page, int size, LocalDate from, LocalDate to) {
        Map<String, Object> result = new LinkedHashMap<>(Map.of("items", rows.stream().map(this::protect).toList(), "page", page, "size", size,
            "total", total, "from", from == null ? "" : from.toString(), "to", to == null ? "" : to.toString(),
            "source", "REPORTING"));
        result.put("asOf", latestPublication());
        return result;
    }

    private Map<String, Object> protect(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean limited = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "PERM_LIMITED_CLIENT_IDENTIFIERS".equals(authority.getAuthority()));
        if (limited) {
            Object userId = result.remove("userid");
            result.remove("username");
            if (userId != null) result.put("clientRef", "client-" + Integer.toHexString(userId.hashCode()));
        }
        return result;
    }

    private String latestPublication() {
        try {
            java.sql.Timestamp timestamp = olap.queryForObject("SELECT published_at FROM etl_refresh_runs WHERE status = 'SUCCEEDED' ORDER BY published_at DESC LIMIT 1", java.sql.Timestamp.class);
            return timestamp == null ? null : timestamp.toInstant().toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int boundedSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        return size;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Invalid date range");
        if (ChronoUnit.DAYS.between(from, to) + 1 > 366) throw new IllegalArgumentException("Date range cannot exceed 366 days");
    }
}