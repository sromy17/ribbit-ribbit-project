package com.neueda.leap.trading.api;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Bounded operational reads. Reporting aggregates remain on the reporting store. */
@RestController
@RequestMapping("/api/admin")
public class AdminInvestigationController {
    private final JdbcTemplate db;
    public AdminInvestigationController(@Qualifier("oltpJdbcTemplate") JdbcTemplate db) { this.db = db; }

    @GetMapping("/session")
    public Map<String, Object> session(Authentication actor) {
        return Map.of("permissions", actor.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList());
    }

    @GetMapping("/accounts/{id}/history")
    @PreAuthorize("hasAuthority('PERM_ACCOUNT_READ')")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> history(@PathVariable Integer id,
            @RequestParam(defaultValue = "orders") String kind,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        if (!List.of("orders", "trades", "holdings", "cash").contains(kind)) throw new IllegalArgumentException("Invalid history type");
        var accounts = db.queryForList("SELECT accountid, userid, account_type, availablefunds FROM accounts WHERE accountid = ?", id);
        if (accounts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        String query = switch (kind) {
            case "trades" -> "SELECT t.executeid, t.requestid, i.ticker, t.quantity_executed, t.askprice, t.fee, t.executed_at_utc FROM trades_executed t JOIN trade_request r ON r.requestid=t.requestid JOIN instruments i ON i.instrumentid=r.instrumentid WHERE r.accountid=?";
            case "holdings" -> "SELECT h.holdingid, i.ticker, h.quantity, h.average_cost, h.as_of_date FROM holdings h JOIN instruments i ON i.instrumentid=h.instrumentid WHERE h.accountid=?";
            case "cash" -> "SELECT movement_id, order_id, amount, currency, movement_type, occurred_at_utc FROM cash_movements WHERE account_id=?";
            default -> "SELECT r.requestid, i.ticker, r.side, r.quantity, r.bidprice, r.status, r.created_at_utc FROM trade_request r JOIN instruments i ON i.instrumentid=r.instrumentid WHERE r.accountid=?";
        };
        String sort = switch (kind) {
            case "trades" -> "executed_at_utc DESC, executeid DESC";
            case "holdings" -> "holdingid ASC";
            case "cash" -> "occurred_at_utc DESC, movement_id DESC";
            default -> "created_at_utc DESC, requestid DESC";
        };
        Long total = db.queryForObject("SELECT count(*) FROM (" + query + ") rows", Long.class, id);
        return Map.of("account", accounts.get(0), "items", db.queryForList(query + " ORDER BY " + sort + " LIMIT ? OFFSET ?", id, size, (long)page * size),
            "total", total, "page", page, "size", size, "asOf", Instant.now().toString(), "source", "OPERATIONAL");
    }

    @GetMapping("/investigations/{id}")
    @PreAuthorize("hasAuthority('PERM_ORDER_INVESTIGATION')")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> investigation(@PathVariable Integer id) {
        var orders = db.queryForList("SELECT r.*, i.ticker FROM trade_request r JOIN instruments i ON i.instrumentid=r.instrumentid WHERE r.requestid=?", id);
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        var events = db.queryForList("SELECT * FROM order_lifecycle_events WHERE order_id=? ORDER BY event_sequence, event_id", id);
        return Map.of("order", orders.get(0), "events", events,
            "fills", db.queryForList("SELECT executeid, askprice, quantity_executed, fee, executed_at_utc FROM trades_executed WHERE requestid=? ORDER BY executed_at_utc, executeid", id),
            "cash", db.queryForList("SELECT * FROM cash_movements WHERE order_id=? ORDER BY occurred_at_utc, movement_id", id),
            "holdings", db.queryForList("SELECT * FROM holding_movements WHERE order_id=? ORDER BY occurred_at_utc, movement_id", id),
            "asOf", Instant.now().toString(), "source", "OPERATIONAL", "evidenceAvailable", !events.isEmpty());
    }
}
