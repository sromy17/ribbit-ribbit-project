package com.neueda.leap.trading.service.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OltpToOlapEtlService {
    private final JdbcTemplate jdbcTemplate;

    public OltpToOlapEtlService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void runFullLoad() {
        jdbcTemplate.update("TRUNCATE TABLE trade_facts, accounts_info, employees_info, holdings_info");
        loadAccounts();
        loadTradeFacts();
        loadEmployees();
        loadHoldings();
    }

    @Scheduled(cron = "${etl.schedule.cron:-}")
    @Transactional
    public void runScheduledLoad() {
        runFullLoad();
    }

    private void loadAccounts() {
        jdbcTemplate.update("""
            INSERT INTO accounts_info
                (accountid, userid, username, availablefunds, creationdate, account_type)
            SELECT a.accountid, a.userid, u.username, a.availablefunds, a.creationdate, a.account_type
            FROM accounts a
            JOIN users u ON u.userid = a.userid
            """);
    }

    private void loadTradeFacts() {
        jdbcTemplate.update("""
            INSERT INTO trade_facts
                (requestid, accountid, userid, instrumentid, side, quantity_requested, bidprice,
                 date_request, executeid, execution_count, last_execution_price, first_execution_date,
                 last_execution_date, quantity_executed, cancel_id, cancel_reason, cancel_date,
                 reject_id, reject_reason, reject_date, disputeid, dispute_status, dispute_category,
                 dispute_reason, dispute_employee_id, availablefunds, creationdate, account_type,
                 username, ticker, instrumentname, dispute_employeeusername, dispute_employeeRoles,
                 order_status, lifecycle_status, last_synced_at)
            SELECT tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side, tr.quantity,
                   tr.bidprice, tr.date_request, MAX(te.executeid), COUNT(te.executeid)::int,
                   MAX(te.askprice), MIN(te.date_execution), MAX(te.date_execution),
                   COALESCE(SUM(te.quantity_executed), 0)::int, co.cancel_id, co.reason, co.cancel_date,
                   ro.reject_id, ro.reason, ro.reject_date, d.disputeid, d.status, d.category,
                   d.reason, d.employeeid, a.availablefunds, a.creationdate, a.account_type,
                   u.username, i.ticker, i.instrumentname, e.employeeusername, e.employeeRoles,
                   tr.status, tr.status, CURRENT_TIMESTAMP
            FROM trade_request tr
            JOIN accounts a ON a.accountid = tr.accountid
            JOIN users u ON u.userid = a.userid
            JOIN instruments i ON i.instrumentid = tr.instrumentid
            LEFT JOIN trades_executed te ON te.requestid = tr.requestid
            LEFT JOIN canceled_order co ON co.request_id = tr.requestid
            LEFT JOIN rejected_orders ro ON ro.request_id = tr.requestid
            LEFT JOIN disputed_orders d ON d.requestid = tr.requestid
            LEFT JOIN employees e ON e.employeeid = d.employeeid
            GROUP BY tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side, tr.quantity,
                     tr.bidprice, tr.date_request, co.cancel_id, co.reason, co.cancel_date,
                     ro.reject_id, ro.reason, ro.reject_date, d.disputeid, d.status, d.category,
                     d.reason, d.employeeid, a.availablefunds, a.creationdate, a.account_type,
                     u.username, i.ticker, i.instrumentname, e.employeeusername, e.employeeRoles,
                     tr.status
            """);
    }

    private void loadEmployees() {
        jdbcTemplate.update("""
            INSERT INTO employees_info
                (ticket_id, accountid, username, support_reason, created_at, status, assigned_to,
                 employeeusername, employee_roles, closed_at)
            SELECT st.ticket_id, st.accountid, u.username, st.reason, st.created_at, st.status,
                   st.assigned_to, e.employeeusername, e.employeeRoles, st.closed_at
            FROM support_ticket st
            JOIN accounts a ON a.accountid = st.accountid
            JOIN users u ON u.userid = a.userid
            LEFT JOIN employees e ON e.employeeid = st.assigned_to
            """);
    }

    private void loadHoldings() {
        jdbcTemplate.update("""
            INSERT INTO holdings_info
                (accountid, userid, username, instrumentid, ticker, instrumentname, quantity,
                 as_of_date, last_synced_at)
            SELECT h.accountid, a.userid, u.username, h.instrumentid, i.ticker, i.instrumentname,
                   h.quantity, h.as_of_date, CURRENT_TIMESTAMP
            FROM holdings h
            JOIN accounts a ON a.accountid = h.accountid
            JOIN users u ON u.userid = a.userid
            JOIN instruments i ON i.instrumentid = h.instrumentid
            """);
    }
}