package com.neueda.leap.trading.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {
    public record Ticket(String kind, int ticketId, int accountId, int userId, String username,
        String firstName, String lastName, String accountType, BigDecimal availableFunds,
        Integer orderId, String symbol, String side, Integer quantity, String orderStatus,
        String category, String reason, String status, Integer employeeId, String employeeUsername,
        Instant createdAt, Instant closedAt) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public TicketService(@Qualifier("oltpJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(
            Objects.requireNonNull(jdbc.getDataSource())));
    }

    public void requireOwner(Jwt jwt, int accountId) {
        Object uid = jwt.getClaim("uid");
        if ("employee".equals(jwt.getClaimAsString("principal_type")) || !(uid instanceof Number id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Investor account required");
        }
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE accountid = ? AND userid = ?",
            Integer.class, accountId, id.intValue());
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
    }

    public List<Ticket> list(String kind, Integer accountId) {
        boolean dispute = "dispute".equals(kind);
        String sql = dispute ? """
            SELECT d.disputeid AS ticket_id, a.accountid, a.userid, u.username, u.first_name, u.last_name,
                   a.account_type, a.availablefunds, tr.requestid AS order_id, i.ticker AS symbol,
                   tr.side, tr.quantity, tr.status AS order_status, d.category, d.reason, d.status,
                   d.employeeid, e.employeeusername, NULL AS created_at, NULL AS closed_at
            FROM disputed_orders d
            JOIN trade_request tr ON tr.requestid = d.requestid
            JOIN accounts a ON a.accountid = tr.accountid
            JOIN users u ON u.userid = a.userid
            JOIN instruments i ON i.instrumentid = tr.instrumentid
            LEFT JOIN employees e ON e.employeeid = d.employeeid
            """ : """
            SELECT s.ticket_id, a.accountid, a.userid, u.username, u.first_name, u.last_name,
                   a.account_type, a.availablefunds, NULL::INTEGER AS order_id, NULL AS symbol,
                   NULL AS side, NULL::INTEGER AS quantity, NULL AS order_status, NULL AS category,
                   s.reason, s.status, s.assigned_to AS employeeid, e.employeeusername,
                   s.created_at, s.closed_at
            FROM support_ticket s
            JOIN accounts a ON a.accountid = s.accountid
            JOIN users u ON u.userid = a.userid
            LEFT JOIN employees e ON e.employeeid = s.assigned_to
            """;
        String filtered = sql + (accountId == null ? "" : " WHERE a.accountid = ?") + " ORDER BY ticket_id DESC";
        return jdbc.query(filtered, (rs, row) -> new Ticket(kind, rs.getInt("ticket_id"),
            rs.getInt("accountid"), rs.getInt("userid"), rs.getString("username"),
            rs.getString("first_name"), rs.getString("last_name"), rs.getString("account_type"),
            rs.getBigDecimal("availablefunds"), rs.getObject("order_id", Integer.class),
            rs.getString("symbol"), rs.getString("side"), rs.getObject("quantity", Integer.class),
            rs.getString("order_status"), rs.getString("category"), rs.getString("reason"),
            rs.getString("status"), rs.getObject("employeeid", Integer.class),
            rs.getString("employeeusername"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant()),
            accountId == null ? new Object[0] : new Object[] {accountId});
    }

    public int createDispute(Jwt jwt, int accountId, int orderId, String category, String reason) {
        requireOwner(jwt, accountId);
        return Objects.requireNonNull(transaction.execute(status -> {
            var orders = jdbc.queryForList(
                "SELECT requestid FROM trade_request WHERE accountid = ? AND requestid = ? FOR UPDATE",
                accountId, orderId);
            if (orders.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for this account");
            }
            Integer open = jdbc.queryForObject(
                "SELECT COUNT(*) FROM disputed_orders WHERE requestid = ? AND status IN ('DISPUTED', 'UNDER-REVIEW')",
                Integer.class, orderId);
            if (open != null && open > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This order already has an open dispute");
            }
            int id = nextId("disputed_orders", "disputeid");
            jdbc.update("""
                INSERT INTO disputed_orders (disputeid, requestid, category, reason, status)
                VALUES (?, ?, ?, ?, 'DISPUTED')
                """, id, orderId, category, reason.trim());
            return id;
        }));
    }

    public int createSupport(Jwt jwt, int accountId, String reason) {
        requireOwner(jwt, accountId);
        return Objects.requireNonNull(transaction.execute(status -> {
            int id = nextId("support_ticket", "ticket_id");
            jdbc.update("INSERT INTO support_ticket (ticket_id, accountid, reason, status) VALUES (?, ?, ?, 'OPEN')",
                id, accountId, reason.trim());
            return id;
        }));
    }

    private int nextId(String table, String column) {
        // Serialize allocation with other ticket requests while preserving existing seed IDs.
        jdbc.execute("LOCK TABLE " + table + " IN SHARE ROW EXCLUSIVE MODE");
        Long id = jdbc.queryForObject("SELECT COALESCE(MAX(" + column + ")::BIGINT, 0) + 1 FROM " + table,
            Long.class);
        if (id != null && id <= Integer.MAX_VALUE) {
            return id.intValue();
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to allocate ticket ID");
    }

    public void update(String kind, int ticketId, String nextStatus, String expectedStatus, Jwt jwt) {
        boolean dispute = "dispute".equals(kind);
        List<String> statuses = dispute ? List.of("DISPUTED", "UNDER-REVIEW", "COMPLETED")
            : List.of("OPEN", "IN_PROGRESS", "RESOLVED");
        if (!statuses.contains(nextStatus) || !statuses.contains(expectedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ticket status");
        }
        String terminal = dispute ? "COMPLETED" : "RESOLVED";
        String progress = dispute ? "UNDER-REVIEW" : "IN_PROGRESS";
        if (terminal.equals(expectedStatus) || nextStatus.equals(expectedStatus)
            || !(nextStatus.equals(progress) || nextStatus.equals(terminal))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid ticket status transition");
        }
        Object eid = jwt.getClaim("eid");
        if (!(eid instanceof Number employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee identity required");
        }
        String sql = dispute ? """
            UPDATE disputed_orders SET status = ?, employeeid = ?
            WHERE disputeid = ? AND status = ?
            """ : """
            UPDATE support_ticket SET status = ?, assigned_to = ?,
              closed_at = CASE WHEN ? = 'RESOLVED' THEN CURRENT_TIMESTAMP ELSE NULL END
            WHERE ticket_id = ? AND status = ?
            """;
        int changed = dispute
            ? jdbc.update(sql, nextStatus, employeeId.intValue(), ticketId, expectedStatus)
            : jdbc.update(sql, nextStatus, employeeId.intValue(), nextStatus, ticketId, expectedStatus);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket changed or no longer exists. Refresh and retry.");
        }
    }
}
