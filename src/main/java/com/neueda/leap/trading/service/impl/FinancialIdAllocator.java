package com.neueda.leap.trading.service.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** Row-locked counters prevent MAX(id)+1 races across application instances and accounts. */
@Component
public class FinancialIdAllocator {
    private final JdbcTemplate db;
    public FinancialIdAllocator(@Qualifier("oltpJdbcTemplate") JdbcTemplate db) { this.db=db; }
    @PostConstruct
    void initialize() {
        db.execute("CREATE TABLE IF NOT EXISTS financial_id_allocations (kind VARCHAR(20) PRIMARY KEY, last_id BIGINT NOT NULL)");
        for(String kind:java.util.List.of("ORDER","TRADE","REJECTION")) {
            try { db.update("INSERT INTO financial_id_allocations(kind,last_id) SELECT ?,0 WHERE NOT EXISTS (SELECT 1 FROM financial_id_allocations WHERE kind=?)",kind,kind); }
            catch(DuplicateKeyException concurrentStartup) { /* Another instance initialized this durable counter. */ }
        }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public int next(String kind) {
        String source=switch(kind) {
            case "ORDER" -> "SELECT COALESCE(MAX(requestid),0) FROM trade_request";
            case "TRADE" -> "SELECT COALESCE(MAX(executeid),0) FROM trades_executed";
            case "REJECTION" -> "SELECT COALESCE(MAX(reject_id),0) FROM rejected_orders";
            default -> throw new IllegalArgumentException("Unknown financial ID kind");
        };
        Long allocated=db.queryForObject("SELECT last_id FROM financial_id_allocations WHERE kind=? FOR UPDATE",Long.class,kind);
        Long actual=db.queryForObject(source,Long.class);
        int next=Math.toIntExact(Math.max(java.util.Objects.requireNonNull(allocated),java.util.Objects.requireNonNull(actual))+1);
        db.update("UPDATE financial_id_allocations SET last_id=? WHERE kind=?",next,kind);
        return next;
    }
}
