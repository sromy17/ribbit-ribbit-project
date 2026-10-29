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
    private final TransactionTemplate runMetadataTransactionTemplate;
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
        this.runMetadataTransactionTemplate = new TransactionTemplate(olapTransactionManager);
        this.runMetadataTransactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.sourceTransactionTemplate = new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(sourceDataSource));
        this.sourceTransactionTemplate.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.sourceTransactionTemplate.setReadOnly(true);
    }

    public String runFullLoad() {
        if (!refreshRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("An ETL refresh is already running");
        }

        String runId = UUID.randomUUID().toString();
        AtomicBoolean runStarted = new AtomicBoolean();
        try {
            ensureRefreshRunsTable();
            InstantHolder sourceWatermark = sourceWatermark();

            logConnection("OLTP", oltpJdbcTemplate);
            logConnection("OLAP", olapJdbcTemplate);
            sourceTransactionTemplate.executeWithoutResult(source -> olapTransactionTemplate.executeWithoutResult(status -> {
                Boolean locked = olapJdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(738291)", Boolean.class);
                if (!Boolean.TRUE.equals(locked)) throw new IllegalStateException("Another process is publishing reporting data");
                // The exclusive publisher lock proves that another RUNNING row has no active publisher.
                // Commit job state separately so a process crash leaves recoverable evidence.
                runMetadataTransactionTemplate.executeWithoutResult(metadata -> {
                    olapJdbcTemplate.update("UPDATE etl_refresh_runs SET status='ABANDONED', error_message='Publisher stopped before publication; recovered by next refresh' WHERE status='RUNNING'");
                    startRun(runId, sourceWatermark.value());
                });
                runStarted.set(true);
                // DELETE preserves MVCC snapshots for in-flight repeatable-read reports.
                // Reports are retained separately and are never refreshed or deleted here.
                for (String table : List.of("trade_facts", "accounts_info", "instruments_info",
                        "employees_info", "holdings_info", "order_facts", "fill_facts",
                        "lifecycle_event_facts", "cash_movement_facts", "holding_movement_facts",
                        "clients_info", "client_segment_history", "bank_movement_facts", "bank_info")) {
                    olapJdbcTemplate.update("DELETE FROM public." + table);
                }
                var snapshotTime = sourceWatermark().value();
                olapJdbcTemplate.update("UPDATE etl_refresh_runs SET source_watermark=? WHERE run_id=?",
                    java.sql.Timestamp.from(snapshotTime), runId);
                loadAccounts();
                loadWalletFacts();
                loadInstruments(runId);
                loadClients(runId);
                loadTradeFacts();
                loadEmployees();
                loadHoldings();
                loadEvidence(runId);
                validatePublishedDataset();
                completeRun(runId);
            }));
            return runId;
        } catch (RuntimeException ex) {
            if (runStarted.get()) {
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
        if (timestamp == null) throw new IllegalStateException("Source watermark is unavailable");
        return new InstantHolder(timestamp.toInstant());
    }

    private void startRun(String runId, java.time.Instant watermark) {
        olapJdbcTemplate.update(
            "INSERT INTO etl_refresh_runs (run_id, status, source_watermark, started_at) VALUES (?, 'RUNNING', ?, CURRENT_TIMESTAMP)",
            runId, java.sql.Timestamp.from(watermark));
    }

    private void completeRun(String runId) {
        olapJdbcTemplate.update(
            "UPDATE etl_refresh_runs SET status = 'SUCCEEDED', published_at = clock_timestamp() WHERE run_id = ?",
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
        for (String table : List.of("trade_request", "trades_executed", "order_lifecycle_events",
                "cash_movements", "holding_movements", "users", "instruments", "client_segment_history")) {
            String target = switch (table) {
                case "trade_request" -> "order_facts";
                case "trades_executed" -> "fill_facts";
                case "order_lifecycle_events" -> "lifecycle_event_facts";
                case "cash_movements" -> "cash_movement_facts";
                case "holding_movements" -> "holding_movement_facts";
                case "users" -> "clients_info";
                case "instruments" -> "instruments_info";
                default -> "client_segment_history";
            };
            Long sourceCount = oltpJdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            Long targetCount = olapJdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + target, Long.class);
            if (!java.util.Objects.equals(sourceCount, targetCount)) {
                throw new IllegalStateException("Fact/dimension reconciliation failed for " + target);
            }
        }
        for (String fact : List.of("fill_facts", "lifecycle_event_facts", "cash_movement_facts", "holding_movement_facts")) {
            Long orphaned=olapJdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + fact +
                " f LEFT JOIN order_facts o ON o.requestid=f.requestid WHERE o.requestid IS NULL OR o.accountid IS DISTINCT FROM f.accountid",Long.class);
            if(orphaned!=null && orphaned>0) throw new IllegalStateException("Unattributed or inconsistent order evidence in " + fact);
        }
        // Reconcile independently at instrument/currency/unit grain; never publish a mixed-unit total.
        var sourceAmounts=oltpJdbcTemplate.queryForList("""
            SELECT o.instrumentid,t.execution_currency,t.fee_currency,t.quantity_unit,
                SUM(t.quantity_executed)::numeric AS quantity,SUM(t.askprice*t.quantity_executed*t.contract_multiplier) AS notional,SUM(t.fee) AS fees
            FROM trades_executed t JOIN trade_request o ON o.requestid=t.requestid
            GROUP BY o.instrumentid,t.execution_currency,t.fee_currency,t.quantity_unit
            ORDER BY o.instrumentid,t.execution_currency NULLS FIRST,t.fee_currency NULLS FIRST,t.quantity_unit NULLS FIRST
            """);
        var targetAmounts=olapJdbcTemplate.queryForList("""
            SELECT instrumentid,execution_currency,fee_currency,quantity_unit,
                SUM(executed_quantity)::numeric AS quantity,SUM(execution_price*executed_quantity*contract_multiplier) AS notional,SUM(fee) AS fees
            FROM fill_facts GROUP BY instrumentid,execution_currency,fee_currency,quantity_unit
            ORDER BY instrumentid,execution_currency NULLS FIRST,fee_currency NULLS FIRST,quantity_unit NULLS FIRST
            """);
        if(!normalizedAmounts(sourceAmounts).equals(normalizedAmounts(targetAmounts)))
            throw new IllegalStateException("Fill quantity/notional/fee reconciliation failed");
        Long invalidSegments = olapJdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM client_segment_history a
            WHERE (a.effective_to_utc IS NOT NULL AND a.effective_to_utc <= a.effective_from_utc)
               OR NOT EXISTS (SELECT 1 FROM clients_info c WHERE c.client_id = a.client_id)
               OR EXISTS (
                SELECT 1 FROM client_segment_history b
                WHERE a.client_id = b.client_id AND a.membership_id <> b.membership_id
                  AND (b.effective_to_utc IS NULL OR a.effective_from_utc < b.effective_to_utc)
                  AND (a.effective_to_utc IS NULL OR b.effective_from_utc < a.effective_to_utc))
            """, Long.class);
        if (invalidSegments != null && invalidSegments > 0) {
            throw new IllegalStateException("Invalid or overlapping client segment history");
        }
    }

    private List<Map<String,Object>> normalizedAmounts(List<Map<String,Object>> rows) {
        return rows.stream().map(row -> {
            Map<String,Object> normalized=new java.util.LinkedHashMap<>();
            row.forEach((key,value)->normalized.put(key,value instanceof java.math.BigDecimal decimal ? decimal.stripTrailingZeros() : value));
            return normalized;
        }).toList();
    }

    private record InstantHolder(java.time.Instant value) {
    }

    private void loadWalletFacts() {
        var banks = oltpJdbcTemplate.queryForList("""
            SELECT b.bankid,b.accountid,a.userid,u.username,u.first_name,u.last_name,
                b.accountname,RIGHT(b.routing_number,3) AS routing_number,
                RIGHT(b.account_number,4) AS account_number,b.balance
            FROM bank b JOIN accounts a ON a.accountid=b.accountid JOIN users u ON u.userid=a.userid
            """);
        batchInsert("INSERT INTO bank_info(bankid,accountid,userid,username,first_name,last_name,accountname,routing_number,account_number,balance) VALUES(?,?,?,?,?,?,?,?,?,?)",
            banks,"bankid","accountid","userid","username","first_name","last_name","accountname","routing_number","account_number","balance");
        var movements = oltpJdbcTemplate.queryForList("""
            SELECT m.movement_id,m.bankid,b.accountid,a.userid,u.username,u.first_name,u.last_name,
                m.amount,m.movement_type,m.movement_date
            FROM bank_movement m JOIN bank b ON b.bankid=m.bankid
            JOIN accounts a ON a.accountid=b.accountid JOIN users u ON u.userid=a.userid
            """);
        batchInsert("INSERT INTO bank_movement_facts(movement_id,bankid,accountid,userid,username,first_name,last_name,amount,movement_type,movement_date) VALUES(?,?,?,?,?,?,?,?,?,?)",
            movements,"movement_id","bankid","accountid","userid","username","first_name","last_name","amount","movement_type","movement_date");
    }

    private void loadAccounts() {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList("""
            SELECT a.accountid, a.userid, u.username, a.availablefunds,
                   a.creationdate, a.account_type, a.currency
            FROM public.accounts a
            JOIN public.users u ON u.userid = a.userid
            """);

        batchInsert(
            """
            INSERT INTO public.accounts_info
                (accountid, userid, username, availablefunds, creationdate, account_type, currency)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            rows,
            "accountid", "userid", "username", "availablefunds",
            "creationdate", "account_type", "currency"
        );
    }

    private void loadInstruments(String runId) {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList(
            """
            SELECT instrumentid, ticker, instrumentname, asset_class, market, venue,
                   quote_currency, settlement_currency, quantity_unit, price_precision,
                   quantity_precision, contract_multiplier, product_convention, metadata_source,
                   market_data_symbol FROM public.instruments
            """);
        batchInsertEvidence(
            """
            INSERT INTO public.instruments_info (instrumentid, ticker, instrumentname, asset_class,
                market, venue, quote_currency, settlement_currency, quantity_unit, price_precision,
                quantity_precision, contract_multiplier, product_convention, metadata_source,
                market_data_symbol, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rows, runId, "instrumentid", "ticker", "instrumentname", "asset_class", "market", "venue",
            "quote_currency", "settlement_currency", "quantity_unit", "price_precision", "quantity_precision",
            "contract_multiplier", "product_convention", "metadata_source", "market_data_symbol");
    }

    private void loadClients(String runId) {
        batchInsertEvidence("""
            INSERT INTO public.clients_info (client_id, registered_at_utc, dataset_run_id)
            VALUES (?, ?, ?)
            """, oltpJdbcTemplate.queryForList("SELECT userid, registered_at_utc FROM public.users"),
            runId, "userid", "registered_at_utc");
        batchInsertEvidence("""
            INSERT INTO public.client_segment_history (membership_id, client_id, segment_code, source,
                effective_from_utc, effective_to_utc, recorded_at_utc, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT membership_id, client_id, segment_code, source, effective_from_utc,
                       effective_to_utc, recorded_at_utc FROM public.client_segment_history
                """), runId, "membership_id", "client_id", "segment_code", "source",
            "effective_from_utc", "effective_to_utc", "recorded_at_utc");
    }

    private void loadTradeFacts() {
        List<Map<String, Object>> rows = oltpJdbcTemplate.queryForList("""
            WITH fills AS (
                SELECT requestid, MAX(executeid) AS executeid, COUNT(*)::int AS execution_count,
                       (ARRAY_AGG(askprice ORDER BY executed_at_utc DESC NULLS LAST, executeid DESC))[1] AS last_execution_price,
                       MIN(date_execution) AS first_execution_date, MAX(date_execution) AS last_execution_date,
                       SUM(quantity_executed)::int AS quantity_executed
                FROM public.trades_executed GROUP BY requestid
            )
            SELECT tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
                   tr.quantity AS quantity_requested, tr.bidprice, tr.date_request,
                   te.executeid, COALESCE(te.execution_count, 0) AS execution_count,
                   te.last_execution_price, te.first_execution_date, te.last_execution_date,
                   COALESCE(te.quantity_executed, 0) AS quantity_executed,
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
            LEFT JOIN fills te ON te.requestid = tr.requestid
            LEFT JOIN LATERAL (SELECT * FROM public.canceled_order c WHERE c.request_id = tr.requestid ORDER BY cancel_id DESC LIMIT 1) co ON TRUE
            LEFT JOIN LATERAL (SELECT * FROM public.rejected_orders r WHERE r.request_id = tr.requestid ORDER BY reject_id DESC LIMIT 1) ro ON TRUE
            LEFT JOIN LATERAL (SELECT * FROM public.disputed_orders cases WHERE cases.requestid = tr.requestid ORDER BY disputeid DESC LIMIT 1) d ON TRUE
            LEFT JOIN public.employees e ON e.employeeid = d.employeeid
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
                 execution_price, fee, executed_at_utc, client_id, execution_currency, fee_currency,
                 quantity_unit, contract_multiplier, product_convention, pricing_event_id, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT te.executeid, te.requestid, tr.accountid, tr.instrumentid,
                       te.quantity_executed, te.askprice, te.fee, te.executed_at_utc,
                       te.client_id, te.execution_currency, te.fee_currency, te.quantity_unit,
                       te.contract_multiplier, te.product_convention, te.pricing_event_id
                FROM public.trades_executed te JOIN public.trade_request tr ON tr.requestid = te.requestid
                """), runId,
            "executeid", "requestid", "accountid", "instrumentid", "quantity_executed",
            "askprice", "fee", "executed_at_utc", "client_id", "execution_currency", "fee_currency",
            "quantity_unit", "contract_multiplier", "product_convention", "pricing_event_id");

        batchInsertEvidence("""
            INSERT INTO public.lifecycle_event_facts
                (event_id, requestid, accountid, instrumentid, event_type, event_sequence,
                 occurred_at_utc, correlation_id, quote_source, quote_at_utc, quote_price,
                 decision_price, quantity, fee, reason, quote_received_at_utc, quote_currency,
                 quote_price_type, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT event_id, order_id, account_id, instrument_id, event_type, event_sequence,
                       occurred_at_utc, correlation_id, quote_source, quote_at_utc, quote_price,
                       decision_price, quantity, fee, reason, quote_received_at_utc, quote_currency, quote_price_type
                FROM public.order_lifecycle_events
                """), runId,
            "event_id", "order_id", "account_id", "instrument_id", "event_type", "event_sequence",
            "occurred_at_utc", "correlation_id", "quote_source", "quote_at_utc", "quote_price",
            "decision_price", "quantity", "fee", "reason", "quote_received_at_utc", "quote_currency", "quote_price_type");

        batchInsertEvidence("""
            INSERT INTO public.cash_movement_facts
                (movement_id, requestid, accountid, amount, currency, currency_source, movement_type,
                 occurred_at_utc, correlation_id, dataset_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, oltpJdbcTemplate.queryForList("""
                SELECT movement_id, order_id, account_id, amount, currency, currency_source, movement_type,
                       occurred_at_utc, correlation_id FROM public.cash_movements
                """), runId,
            "movement_id", "order_id", "account_id", "amount", "currency", "currency_source", "movement_type",
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
