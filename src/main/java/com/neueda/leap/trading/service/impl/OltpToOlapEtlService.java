package com.neueda.leap.trading.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OltpToOlapEtlService {
    private static final Logger log = LoggerFactory.getLogger(OltpToOlapEtlService.class);

    private final JdbcTemplate oltpJdbcTemplate;
    private final JdbcTemplate olapJdbcTemplate;
    private final TransactionTemplate olapTransactionTemplate;
    private final TransactionTemplate sourceTransactionTemplate;
    private final AtomicBoolean refreshRunning = new AtomicBoolean();

    public OltpToOlapEtlService(
        @Qualifier("oltpJdbcTemplate") JdbcTemplate oltpJdbcTemplate,
        @Qualifier("olapJdbcTemplate") JdbcTemplate olapJdbcTemplate,
        @Qualifier("olapTransactionManager") org.springframework.transaction.PlatformTransactionManager olapTransactionManager,
        @Qualifier("dataSource") javax.sql.DataSource sourceDataSource
    ) {
        this.oltpJdbcTemplate = oltpJdbcTemplate;
        this.olapJdbcTemplate = olapJdbcTemplate;
        this.olapTransactionTemplate = new TransactionTemplate(olapTransactionManager);
        this.sourceTransactionTemplate = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(sourceDataSource));
        this.sourceTransactionTemplate.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.sourceTransactionTemplate.setReadOnly(true);
    }

    public String runFullLoad() {
        if (!refreshRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("An ETL refresh is already running");
        }

        String runId = UUID.randomUUID().toString();
        boolean runStarted = false;
        try {
            ensureRefreshRunsTable();
            InstantHolder sourceWatermark = sourceWatermark();
            startRun(runId, sourceWatermark.value());
            runStarted = true;
            logConnection("OLTP", oltpJdbcTemplate);
            logConnection("OLAP", olapJdbcTemplate);
            sourceTransactionTemplate.executeWithoutResult(source -> olapTransactionTemplate.executeWithoutResult(status -> {
                Boolean locked = olapJdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(738291)", Boolean.class);
                if (!Boolean.TRUE.equals(locked)) throw new IllegalStateException("Another process is publishing reporting data");
                olapJdbcTemplate.update(
                    "TRUNCATE TABLE public.trade_facts, public.accounts_info, public.instruments_info, " +
                    "public.employees_info, public.holdings_info, public.order_facts, " +
                    "public.fill_facts, public.lifecycle_event_facts, public.cash_movement_facts, " +
                    "public.holding_movement_facts"
                );
                loadAccounts();
                loadInstruments(runId);
                loadTradeFacts();
                loadEmployees();
                loadHoldings();
                loadEvidence(runId);
                validatePublishedDataset();
                completeRun(runId);
            }));
            return runId;
        } catch (RuntimeException ex) {
            if (runStarted) {
                failRun(runId, ex);
            }
            throw ex;
        } finally {
            refreshRunning.set(false);
        }
    }

    @Scheduled(cron = "${etl.schedule.cron:-}")
    public void runScheduledLoad() {
        runFullLoad();
    }

    private void ensureRefreshRunsTable() {
        olapJdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS etl_refresh_runs (
                run_id VARCHAR(36) PRIMARY KEY,
                status VARCHAR(20) NOT NULL,
                source_watermark TIMESTAMPTZ NOT NULL,
                started_at TIMESTAMPTZ NOT NULL,
                published_at TIMESTAMPTZ,
                error_message VARCHAR(1000)
            )
            """);
    }

    private InstantHolder sourceWatermark() {
        java.sql.Timestamp timestamp = oltpJdbcTemplate.queryForObject(
            "SELECT CURRENT_TIMESTAMP", java.sql.Timestamp.class);
        return new InstantHolder(timestamp == null ? java.time.Instant.now() : timestamp.toInstant());
    }

    private void startRun(String runId, java.time.Instant watermark) {
        olapJdbcTemplate.update(
            "INSERT INTO etl_refresh_runs (run_id, status, source_watermark, started_at) VALUES (?, 'RUNNING', ?, CURRENT_TIMESTAMP)",
            runId, java.sql.Timestamp.from(watermark));
    }

    private void completeRun(String runId) {
        olapJdbcTemplate.update(
            "UPDATE etl_refresh_runs SET status = 'SUCCEEDED', published_at = CURRENT_TIMESTAMP WHERE run_id = ?",
            runId);
    }

    private void failRun(String runId, RuntimeException exception) {
        olapJdbcTemplate.update(
            "UPDATE etl_refresh_runs SET status = 'FAILED', error_message = ? WHERE run_id = ?",
            truncate(exception.getMessage()), runId);
    }

    private String truncate(String message) {
        if (message == null) {
            return "ETL refresh failed";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private void validatePublishedDataset() {
        Integer sourceAccounts = oltpJdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        Integer publishedAccounts = olapJdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts_info", Integer.class);
        if (!java.util.Objects.equals(sourceAccounts, publishedAccounts)) {
            throw new IllegalStateException("Account count reconciliation failed");
        }

        Integer duplicateFacts = olapJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM (SELECT requestid FROM trade_facts GROUP BY requestid HAVING COUNT(*) > 1) duplicates",
            Integer.class);
        if (duplicateFacts != null && duplicateFacts > 0) {
            throw new IllegalStateException("Trade fact uniqueness validation failed");
        }
    }

    private record InstantHolder(java.time.Instant value) {
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

    private void loadInstruments(String runId) {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList(
            "SELECT instrumentid, ticker, instrumentname FROM public.instruments");
        batchInsertEvidence(
            "INSERT INTO public.instruments_info (instrumentid, ticker, instrumentname, dataset_run_id) VALUES (?, ?, ?, ?)",
            rows, runId, "instrumentid", "ticker", "instrumentname");
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

    private void loadEvidence(String runId) {
        batchInsertEvidence("""
            INSERT INTO public.order_facts
                (requestid, accountid, userid, instrumentid, side, quantity_requested,
                 bidprice, order_status, created_at_utc, correlation_id, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
                       tr.quantity, tr.bidprice, tr.status, tr.created_at_utc, tr.correlation_id
                FROM public.trade_request tr JOIN public.accounts a ON a.accountid = tr.accountid
                """), runId,
            "requestid", "accountid", "userid", "instrumentid", "side", "quantity",
            "bidprice", "status", "created_at_utc", "correlation_id");

        batchInsertEvidence("""
            INSERT INTO public.fill_facts
                (executeid, requestid, accountid, instrumentid, executed_quantity,
                 execution_price, fee, executed_at_utc, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT te.executeid, te.requestid, tr.accountid, tr.instrumentid,
                       te.quantity_executed, te.askprice, te.fee, te.executed_at_utc
                FROM public.trades_executed te JOIN public.trade_request tr ON tr.requestid = te.requestid
                """), runId,
            "executeid", "requestid", "accountid", "instrumentid", "quantity_executed",
            "askprice", "fee", "executed_at_utc");

        batchInsertEvidence("""
            INSERT INTO public.lifecycle_event_facts
                (event_id, requestid, accountid, instrumentid, event_type, event_sequence,
                 occurred_at_utc, correlation_id, quote_source, quote_at_utc, quote_price,
                 decision_price, quantity, fee, reason, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT event_id, order_id, account_id, instrument_id, event_type, event_sequence,
                       occurred_at_utc, correlation_id, quote_source, quote_at_utc, quote_price,
                       decision_price, quantity, fee, reason
                FROM public.order_lifecycle_events
                """), runId,
            "event_id", "order_id", "account_id", "instrument_id", "event_type", "event_sequence",
            "occurred_at_utc", "correlation_id", "quote_source", "quote_at_utc", "quote_price",
            "decision_price", "quantity", "fee", "reason");

        batchInsertEvidence("""
            INSERT INTO public.cash_movement_facts
                (movement_id, requestid, accountid, amount, currency, movement_type,
                 occurred_at_utc, correlation_id, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT movement_id, order_id, account_id, amount, currency, movement_type,
                       occurred_at_utc, correlation_id FROM public.cash_movements
                """), runId,
            "movement_id", "order_id", "account_id", "amount", "currency", "movement_type",
            "occurred_at_utc", "correlation_id");

        batchInsertEvidence("""
            INSERT INTO public.holding_movement_facts
                (movement_id, requestid, accountid, instrumentid, quantity_delta,
                 quantity_after, occurred_at_utc, correlation_id, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT movement_id, order_id, account_id, instrument_id, quantity_delta,
                       quantity_after, occurred_at_utc, correlation_id FROM public.holding_movements
                """), runId,
            "movement_id", "order_id", "account_id", "instrument_id", "quantity_delta",
            "quantity_after", "occurred_at_utc", "correlation_id");
    }

    private void batchInsertEvidence(String sql, List<Map<String, Object>> rows, String runId, String... columns) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> arguments = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object[] values = new Object[columns.length + 1];
            for (int i = 0; i < columns.length; i++) {
                values[i] = row.get(columns[i]);
            }
            values[columns.length] = runId;
            arguments.add(values);
        }
        olapJdbcTemplate.batchUpdate(sql, arguments);
    }

    private void batchInsert(@NonNull String sql, List<Map<String, Object>> rows, String... columns) {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> arguments = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                values[i] = row.get(columns[i]);
            }
            arguments.add(values);
        }
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
