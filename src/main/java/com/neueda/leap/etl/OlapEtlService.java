package com.neueda.leap.etl;

import java.sql.Timestamp;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.lang.NonNull;

@Service
public class OlapEtlService {
    private static final Logger log = LoggerFactory.getLogger(OlapEtlService.class);

    private final JdbcTemplate oltp;
    private final JdbcTemplate olap;
    private final boolean enabled;
    private final TransactionTemplate sourceSnapshot;

    public OlapEtlService(
        @Qualifier("oltpJdbcTemplate") JdbcTemplate oltp,
        @Qualifier("olapJdbcTemplate") JdbcTemplate olap,
        @Value("${olap.enabled:false}") boolean enabled
    ) {
        this.oltp = oltp;
        this.olap = olap;
        this.enabled = enabled;
        this.sourceSnapshot = new TransactionTemplate(new DataSourceTransactionManager(
            Objects.requireNonNull(oltp.getDataSource())));
        this.sourceSnapshot.setReadOnly(true);
        this.sourceSnapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(transactionManager = "olapTransactionManager")
    public void runOnStartup() {
        if (enabled) {
            executeEtl();
        }
    }

    @Scheduled(fixedDelayString = "${olap.etl.fixed-delay-ms:300000}")
    @Transactional(transactionManager = "olapTransactionManager")
    public void runScheduled() {
        if (enabled) {
            executeEtl();
        }
    }

    @Transactional(transactionManager = "olapTransactionManager")
    public synchronized EtlResult runNow() {
        return executeEtl();
    }

    private EtlResult executeEtl() {
        try {
            // Shared with analyst reads; serialize scheduled/manual refreshes until commit.
            olap.execute("SELECT pg_advisory_xact_lock(724182031)");
            clearReportingData();
            sourceSnapshot.executeWithoutResult(status -> {
                loadAccounts();
                loadBanks();
                loadBankMovements();
                loadTradeFacts();
                loadEmployees();
            });
            log.info("OLTP to OLAP data refresh completed successfully");
            return new EtlResult(true, "OLTP to OLAP data refresh completed successfully");
        } catch (RuntimeException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("OLTP to OLAP data refresh failed", exception);
            String message = rootMessage(exception);
            return new EtlResult(false, message);
        }
    }

    private static String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof SQLException sqlException
            && sqlException.getMessage() != null) {
            return sqlException.getMessage();
        }
        return current.getMessage() == null
            ? current.getClass().getSimpleName()
            : current.getMessage();
    }

    private void clearReportingData() {
        List<String> tables = List.of(
            "trade_facts",
            "accounts_info",
            "employees_info",
            "bank_movement_facts",
            "bank_info"
        );
        List<String> missing = tables.stream()
            .filter(table -> olap.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, table) == 0)
            .toList();
        if (!missing.isEmpty()) {
            Map<String, Object> connection = olap.queryForMap(
                "SELECT current_database() AS database_name, "
                    + "current_user AS user_name, current_schema() AS schema_name"
            );
            throw new IllegalStateException(
                "Missing OLAP tables in public schema: " + String.join(", ", missing)
                    + ". Connected to database " + connection.get("database_name")
                    + " as user " + connection.get("user_name")
                    + " with current schema " + connection.get("schema_name")
            );
        }
        for (String table : tables) {
            olap.execute("TRUNCATE TABLE public." + table);
        }
    }

    private void loadAccounts() {
        List<Map<String, Object>> rows = oltp.queryForList("""
            SELECT a.accountid, u.userid, u.username, u.first_name, u.last_name,
                   a.availablefunds, a.creationdate, a.account_type
            FROM accounts a
            JOIN users u ON u.userid = a.userid
            """);
        olap.batchUpdate("""
            INSERT INTO public.accounts_info
                (accountid, userid, username, first_name, last_name,
                 availablefunds, creationdate, account_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, setter(rows, (statement, row) -> {
                statement.setObject(1, row.get("accountid"));
                statement.setObject(2, row.get("userid"));
                statement.setObject(3, row.get("username"));
                statement.setObject(4, row.get("first_name"));
                statement.setObject(5, row.get("last_name"));
                statement.setObject(6, row.get("availablefunds"));
                statement.setObject(7, row.get("creationdate"));
                statement.setObject(8, row.get("account_type"));
            }));
    }

    private void loadBanks() {
        List<Map<String, Object>> rows = oltp.queryForList("""
            SELECT b.bankid, b.accountid, u.userid, u.username, u.first_name,
                   u.last_name, b.accountname, b.routing_number,
                   b.account_number, b.balance
            FROM bank b
            JOIN accounts a ON a.accountid = b.accountid
            JOIN users u ON u.userid = a.userid
            """);
        olap.batchUpdate("""
            INSERT INTO public.bank_info
                (bankid, accountid, userid, username, first_name, last_name,
                 accountname, routing_number, account_number, balance)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, setter(rows, (statement, row) -> {
                statement.setObject(1, row.get("bankid"));
                statement.setObject(2, row.get("accountid"));
                statement.setObject(3, row.get("userid"));
                statement.setObject(4, row.get("username"));
                statement.setObject(5, row.get("first_name"));
                statement.setObject(6, row.get("last_name"));
                statement.setObject(7, row.get("accountname"));
                statement.setObject(8, row.get("routing_number"));
                statement.setObject(9, row.get("account_number"));
                statement.setObject(10, row.get("balance"));
            }));
    }

    private void loadBankMovements() {
        List<Map<String, Object>> rows = oltp.queryForList("""
            SELECT bm.movement_id, bm.bankid, b.accountid, u.userid, u.username,
                   u.first_name, u.last_name, bm.amount, bm.movement_type,
                   bm.movement_date
            FROM bank_movement bm
            JOIN bank b ON b.bankid = bm.bankid
            JOIN accounts a ON a.accountid = b.accountid
            JOIN users u ON u.userid = a.userid
            """);
        olap.batchUpdate("""
            INSERT INTO public.bank_movement_facts
                (movement_id, bankid, accountid, userid, username, first_name,
                 last_name, amount, movement_type, movement_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, setter(rows, (statement, row) -> {
                statement.setObject(1, row.get("movement_id"));
                statement.setObject(2, row.get("bankid"));
                statement.setObject(3, row.get("accountid"));
                statement.setObject(4, row.get("userid"));
                statement.setObject(5, row.get("username"));
                statement.setObject(6, row.get("first_name"));
                statement.setObject(7, row.get("last_name"));
                statement.setObject(8, row.get("amount"));
                statement.setObject(9, row.get("movement_type"));
                statement.setObject(10, row.get("movement_date"));
            }));
    }

    private void loadTradeFacts() {
        List<Map<String, Object>> rows = oltp.queryForList("""
            SELECT tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
                   tr.quantity AS quantity_requested, tr.bidprice, tr.date_request,
                   MIN(te.executeid) AS executeid, COUNT(te.executeid) AS execution_count,
                   MAX(te.askprice) AS last_execution_price,
                   MIN(te.date_execution) AS first_execution_date,
                   MAX(te.date_execution) AS last_execution_date,
                   COALESCE(SUM(te.quantity_executed), 0) AS quantity_executed,
                   co.cancel_id, co.reason AS cancel_reason, co.cancel_date,
                   ro.reject_id, ro.reason AS reject_reason, ro.reject_date,
                   d.disputeid, d.status AS dispute_status, d.category AS dispute_category,
                   d.reason AS dispute_reason, d.employeeid AS dispute_employee_id,
                   a.availablefunds, a.creationdate, a.account_type, u.username,
                   u.first_name, u.last_name, i.ticker, i.instrumentname,
                   e.employeeusername AS dispute_employeeusername,
                   e.employeeRoles AS dispute_employee_roles, tr.status AS order_status
            FROM trade_request tr
            JOIN accounts a ON a.accountid = tr.accountid
            JOIN users u ON u.userid = a.userid
            JOIN instruments i ON i.instrumentid = tr.instrumentid
            LEFT JOIN trades_executed te ON te.requestid = tr.requestid
            LEFT JOIN LATERAL (
                SELECT * FROM canceled_order WHERE request_id = tr.requestid ORDER BY cancel_id DESC LIMIT 1
            ) co ON TRUE
            LEFT JOIN LATERAL (
                SELECT * FROM rejected_orders WHERE request_id = tr.requestid ORDER BY reject_id DESC LIMIT 1
            ) ro ON TRUE
            LEFT JOIN LATERAL (
                SELECT * FROM disputed_orders WHERE requestid = tr.requestid
                ORDER BY CASE WHEN status IN ('DISPUTED', 'UNDER-REVIEW') THEN 0 ELSE 1 END,
                         disputeid DESC LIMIT 1
            ) d ON TRUE
            LEFT JOIN employees e ON e.employeeid = d.employeeid
            GROUP BY tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
                     tr.quantity, tr.bidprice, tr.date_request, co.cancel_id,
                     co.reason, co.cancel_date, ro.reject_id, ro.reason, ro.reject_date,
                     d.disputeid, d.status, d.category, d.reason, d.employeeid,
                     a.availablefunds, a.creationdate, a.account_type, u.username,
                     u.first_name, u.last_name, i.ticker, i.instrumentname,
                     e.employeeusername, e.employeeRoles, tr.status
            """);
        olap.batchUpdate("""
            INSERT INTO public.trade_facts
                (requestid, accountid, userid, instrumentid, side, quantity_requested,
                 bidprice, date_request, executeid, execution_count, last_execution_price,
                 first_execution_date, last_execution_date, quantity_executed, cancel_id,
                 cancel_reason, cancel_date, reject_id, reject_reason, reject_date,
                 disputeid, dispute_status, dispute_category, dispute_reason,
                 dispute_employee_id, availablefunds, creationdate, account_type, username,
                 first_name, last_name, ticker, instrumentname, dispute_employeeusername,
                 dispute_employeeRoles, order_status, lifecycle_status, last_synced_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, setter(rows, (statement, row) -> {
                String status = String.valueOf(row.get("order_status"));
                String lifecycle = switch (status) {
                    case "EXECUTED" -> "COMPLETED";
                    case "CANCELLED" -> "CANCELLED";
                    case "REJECTED" -> "REJECTED";
                    default -> "PENDING";
                };
                Object[] values = {
                    row.get("requestid"), row.get("accountid"), row.get("userid"),
                    row.get("instrumentid"), row.get("side"), row.get("quantity_requested"),
                    row.get("bidprice"), row.get("date_request"), row.get("executeid"),
                    row.get("execution_count"), row.get("last_execution_price"),
                    row.get("first_execution_date"), row.get("last_execution_date"),
                    row.get("quantity_executed"), row.get("cancel_id"), row.get("cancel_reason"),
                    row.get("cancel_date"), row.get("reject_id"), row.get("reject_reason"),
                    row.get("reject_date"), row.get("disputeid"), row.get("dispute_status"),
                    row.get("dispute_category"), row.get("dispute_reason"),
                    row.get("dispute_employee_id"), row.get("availablefunds"),
                    row.get("creationdate"), row.get("account_type"), row.get("username"),
                    row.get("first_name"), row.get("last_name"), row.get("ticker"),
                    row.get("instrumentname"), row.get("dispute_employeeusername"),
                    row.get("dispute_employee_roles"), status, lifecycle,
                    Timestamp.from(java.time.Instant.now())
                };
                for (int i = 0; i < values.length; i++) {
                    statement.setObject(i + 1, values[i]);
                }
            }));
    }

    private void loadEmployees() {
        List<Map<String, Object>> rows = oltp.queryForList("""
            SELECT st.ticket_id, st.accountid, u.username, u.first_name, u.last_name,
                   st.reason AS support_reason, st.created_at, st.status, st.assigned_to,
                   e.employeeusername, e.employeeRoles AS employee_roles, st.closed_at
            FROM support_ticket st
            JOIN accounts a ON a.accountid = st.accountid
            JOIN users u ON u.userid = a.userid
            LEFT JOIN employees e ON e.employeeid = st.assigned_to
            """);
        olap.batchUpdate("""
            INSERT INTO public.employees_info
                (ticket_id, accountid, username, first_name, last_name, support_reason,
                 created_at, status, assigned_to, employeeusername, employee_roles, closed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, setter(rows, (statement, row) -> {
                statement.setObject(1, row.get("ticket_id"));
                statement.setObject(2, row.get("accountid"));
                statement.setObject(3, row.get("username"));
                statement.setObject(4, row.get("first_name"));
                statement.setObject(5, row.get("last_name"));
                statement.setObject(6, row.get("support_reason"));
                statement.setObject(7, row.get("created_at"));
                statement.setObject(8, row.get("status"));
                statement.setObject(9, row.get("assigned_to"));
                statement.setObject(10, row.get("employeeusername"));
                statement.setObject(11, row.get("employee_roles"));
                statement.setObject(12, row.get("closed_at"));
            }));
    }

    private static @NonNull BatchPreparedStatementSetter setter(
        List<Map<String, Object>> rows,
        StatementBinder binder
    ) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(@NonNull java.sql.PreparedStatement statement, int index)
                throws java.sql.SQLException {
                binder.bind(statement, rows.get(index));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        };
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(java.sql.PreparedStatement statement, Map<String, Object> row)
            throws java.sql.SQLException;
    }

    public record EtlResult(boolean successful, String message) {
    }
}
