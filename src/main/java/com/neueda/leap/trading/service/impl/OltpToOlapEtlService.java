package com.neueda.leap.trading.service.impl;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OltpToOlapEtlService {
    private static final Logger log = LoggerFactory.getLogger(OltpToOlapEtlService.class);

    private final JdbcTemplate oltpJdbcTemplate;
    private final JdbcTemplate olapJdbcTemplate;

    public OltpToOlapEtlService(
        @Qualifier("oltpJdbcTemplate") JdbcTemplate oltpJdbcTemplate,
        @Qualifier("olapJdbcTemplate") JdbcTemplate olapJdbcTemplate
    ) {
        this.oltpJdbcTemplate = oltpJdbcTemplate;
        this.olapJdbcTemplate = olapJdbcTemplate;
    }

    public void runFullLoad() {
        logConnection("OLTP", oltpJdbcTemplate);
        logConnection("OLAP", olapJdbcTemplate);
        olapJdbcTemplate.update(
            "TRUNCATE TABLE public.trade_facts, public.accounts_info, " +
            "public.employees_info, public.holdings_info"
        );
        loadAccounts();
        loadTradeFacts();
        loadEmployees();
        loadHoldings();
    }

    @Scheduled(cron = "${etl.schedule.cron:-}")
    public void runScheduledLoad() {
        runFullLoad();
    }

    private void loadAccounts() {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList("""
            SELECT a.accountid, a.userid, u.username, a.availablefunds,
                   a.creationdate, a.account_type
            FROM public.accounts a
            JOIN public.users u ON u.userid = a.userid
            """);

        batchInsert(
            """
            INSERT INTO public.accounts_info
                (accountid, userid, username, availablefunds, creationdate, account_type)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            rows,
            "accountid", "userid", "username", "availablefunds",
            "creationdate", "account_type"
        );
    }

    private void loadTradeFacts() {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList("""
            SELECT tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
                   tr.quantity AS quantity_requested, tr.bidprice, tr.date_request,
                   MAX(te.executeid) AS executeid, COUNT(te.executeid)::int AS execution_count,
                   MAX(te.askprice) AS last_execution_price,
                   MIN(te.date_execution) AS first_execution_date,
                   MAX(te.date_execution) AS last_execution_date,
                   COALESCE(SUM(te.quantity_executed), 0)::int AS quantity_executed,
                   co.cancel_id, co.reason AS cancel_reason, co.cancel_date,
                   ro.reject_id, ro.reason AS reject_reason, ro.reject_date,
                   d.disputeid, d.status AS dispute_status, d.category AS dispute_category,
                   d.reason AS dispute_reason, d.employeeid AS dispute_employee_id,
                   a.availablefunds, a.creationdate, a.account_type, u.username,
                   i.ticker, i.instrumentname,
                   e.employeeusername AS dispute_employeeusername,
                   e.employeeRoles AS dispute_employeeroles,
                   tr.status AS order_status, tr.status AS lifecycle_status
            FROM public.trade_request tr
            JOIN public.accounts a ON a.accountid = tr.accountid
            JOIN public.users u ON u.userid = a.userid
            JOIN public.instruments i ON i.instrumentid = tr.instrumentid
            LEFT JOIN public.trades_executed te ON te.requestid = tr.requestid
            LEFT JOIN public.canceled_order co ON co.request_id = tr.requestid
            LEFT JOIN public.rejected_orders ro ON ro.request_id = tr.requestid
            LEFT JOIN public.disputed_orders d ON d.requestid = tr.requestid
            LEFT JOIN public.employees e ON e.employeeid = d.employeeid
            GROUP BY tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
                     tr.quantity, tr.bidprice, tr.date_request, co.cancel_id,
                     co.reason, co.cancel_date, ro.reject_id, ro.reason, ro.reject_date,
                     d.disputeid, d.status, d.category, d.reason, d.employeeid,
                     a.availablefunds, a.creationdate, a.account_type, u.username,
                     i.ticker, i.instrumentname, e.employeeusername, e.employeeRoles,
                     tr.status
            """);

        batchInsert(
            """
            INSERT INTO public.trade_facts
                (requestid, accountid, userid, instrumentid, side, quantity_requested,
                 bidprice, date_request, executeid, execution_count, last_execution_price,
                 first_execution_date, last_execution_date, quantity_executed, cancel_id,
                 cancel_reason, cancel_date, reject_id, reject_reason, reject_date,
                 disputeid, dispute_status, dispute_category, dispute_reason,
                 dispute_employee_id, availablefunds, creationdate, account_type, username,
                 ticker, instrumentname, dispute_employeeusername, dispute_employeeRoles,
                 order_status, lifecycle_status, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            rows,
            "requestid", "accountid", "userid", "instrumentid", "side",
            "quantity_requested", "bidprice", "date_request", "executeid",
            "execution_count", "last_execution_price", "first_execution_date",
            "last_execution_date", "quantity_executed", "cancel_id", "cancel_reason",
            "cancel_date", "reject_id", "reject_reason", "reject_date", "disputeid",
            "dispute_status", "dispute_category", "dispute_reason", "dispute_employee_id",
            "availablefunds", "creationdate", "account_type", "username", "ticker",
            "instrumentname", "dispute_employeeusername", "dispute_employeeroles",
            "order_status", "lifecycle_status"
        );
    }

    private void loadEmployees() {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList("""
            SELECT st.ticket_id, st.accountid, u.username,
                   st.reason AS support_reason, st.created_at, st.status,
                   st.assigned_to, e.employeeusername, e.employeeRoles AS employee_roles,
                   st.closed_at
            FROM public.support_ticket st
            JOIN public.accounts a ON a.accountid = st.accountid
            JOIN public.users u ON u.userid = a.userid
            LEFT JOIN public.employees e ON e.employeeid = st.assigned_to
            """);

        batchInsert(
            """
            INSERT INTO public.employees_info
                (ticket_id, accountid, username, support_reason, created_at, status,
                 assigned_to, employeeusername, employee_roles, closed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rows,
            "ticket_id", "accountid", "username", "support_reason", "created_at",
            "status", "assigned_to", "employeeusername", "employee_roles", "closed_at"
        );
    }

    private void loadHoldings() {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList("""
            SELECT h.accountid, a.userid, u.username, h.instrumentid, i.ticker,
                   i.instrumentname, h.quantity, h.as_of_date
            FROM public.holdings h
            JOIN public.accounts a ON a.accountid = h.accountid
            JOIN public.users u ON u.userid = a.userid
            JOIN public.instruments i ON i.instrumentid = h.instrumentid
            """);

        batchInsert(
            """
            INSERT INTO public.holdings_info
                (accountid, userid, username, instrumentid, ticker, instrumentname,
                 quantity, as_of_date, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            rows,
            "accountid", "userid", "username", "instrumentid", "ticker",
            "instrumentname", "quantity", "as_of_date"
        );
    }

    private void batchInsert(String sql, List<Map<String, Object>> rows, String... columns) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> arguments = rows.stream()
            .map(row -> {
                Object[] values = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    values[i] = row.get(columns[i]);
                }
                return values;
            })
            .toList();
        olapJdbcTemplate.batchUpdate(sql, arguments);
    }

    private void logConnection(String name, JdbcTemplate jdbcTemplate) {
        Map<String, Object> connection = jdbcTemplate.queryForMap("""
            SELECT current_database() AS database_name,
                   current_schema() AS schema_name,
                   current_setting('search_path') AS search_path
            """);
        log.info(
            "{} connection: database={}, schema={}, search_path={}",
            name,
            connection.get("database_name"),
            connection.get("schema_name"),
            connection.get("search_path")
        );
    }
}
