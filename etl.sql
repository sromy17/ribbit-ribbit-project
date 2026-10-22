-- OLTP -> OLAP load for the reporting schema.
-- Run this after basedSchema.sql and olap.sql have been applied.
-- Holdings are intentionally not loaded.

TRUNCATE TABLE trade_facts, accounts_info, employees_info, bank_movement_facts, bank_info;

INSERT INTO bank_info (
    bankid, accountid, userid, username, first_name, last_name,
    accountname, routing_number, account_number, balance
)
SELECT
    b.bankid, b.accountid, u.userid, u.username, u.first_name, u.last_name,
    b.accountname, b.routing_number, b.account_number, b.balance
FROM bank b
JOIN accounts a ON a.accountid = b.accountid
JOIN users u ON u.userid = a.userid;

INSERT INTO bank_movement_facts (
    movement_id, bankid, accountid, userid, username, first_name, last_name,
    amount, movement_type, movement_date
)
SELECT
    bm.movement_id, bm.bankid, b.accountid, u.userid, u.username,
    u.first_name, u.last_name, bm.amount, bm.movement_type, bm.movement_date
FROM bank_movement bm
JOIN bank b ON b.bankid = bm.bankid
JOIN accounts a ON a.accountid = b.accountid
JOIN users u ON u.userid = a.userid;

INSERT INTO accounts_info (
    accountid,
    userid,
    username,
    first_name,
    last_name,
    availablefunds,
    creationdate,
    account_type
)
SELECT
    a.accountid,
    u.userid,
    u.username,
    u.first_name,
    u.last_name,
    a.availablefunds,
    a.creationdate,
    a.account_type
FROM accounts a
JOIN users u ON u.userid = a.userid;

INSERT INTO trade_facts (
    requestid,
    accountid,
    userid,
    instrumentid,
    side,
    quantity_requested,
    bidprice,
    date_request,
    executeid,
    execution_count,
    last_execution_price,
    first_execution_date,
    last_execution_date,
    quantity_executed,
    cancel_id,
    cancel_reason,
    cancel_date,
    reject_id,
    reject_reason,
    reject_date,
    disputeid,
    dispute_status,
    dispute_category,
    dispute_reason,
    dispute_employee_id,
    availablefunds,
    creationdate,
    account_type,
    username,
    first_name,
    last_name,
    ticker,
    instrumentname,
    dispute_employeeusername,
    dispute_employeeRoles,
    order_status,
    lifecycle_status,
    last_synced_at
)
SELECT
    tr.requestid,
    tr.accountid,
    a.userid,
    tr.instrumentid,
    tr.side,
    tr.quantity,
    tr.bidprice,
    tr.date_request,
    MIN(te.executeid),
    COUNT(te.executeid)::int,
    MAX(te.askprice),
    MIN(te.date_execution),
    MAX(te.date_execution),
    COALESCE(SUM(te.quantity_executed), 0)::int,
    co.cancel_id,
    co.reason,
    co.cancel_date,
    ro.reject_id,
    ro.reason,
    ro.reject_date,
    d.disputeid,
    d.status,
    d.category,
    d.reason,
    d.employeeid,
    a.availablefunds,
    a.creationdate,
    a.account_type,
    u.username,
    u.first_name,
    u.last_name,
    i.ticker,
    i.instrumentname,
    e.employeeusername,
    e.employeeRoles,
    tr.status,
    CASE
        WHEN tr.status = 'EXECUTED' THEN 'COMPLETED'
        WHEN tr.status = 'CANCELLED' THEN 'CANCELLED'
        WHEN tr.status = 'REJECTED' THEN 'REJECTED'
        ELSE 'PENDING'
    END,
    CURRENT_TIMESTAMP
FROM trade_request tr
JOIN accounts a ON a.accountid = tr.accountid
JOIN users u ON u.userid = a.userid
JOIN instruments i ON i.instrumentid = tr.instrumentid
LEFT JOIN trades_executed te ON te.requestid = tr.requestid
LEFT JOIN canceled_order co ON co.request_id = tr.requestid
LEFT JOIN rejected_orders ro ON ro.request_id = tr.requestid
LEFT JOIN disputed_orders d ON d.requestid = tr.requestid
LEFT JOIN employees e ON e.employeeid = d.employeeid
GROUP BY
    tr.requestid, tr.accountid, a.userid, tr.instrumentid, tr.side,
    tr.quantity, tr.bidprice, tr.date_request, co.cancel_id, co.reason,
    co.cancel_date, ro.reject_id, ro.reason, ro.reject_date, d.disputeid,
    d.status, d.category, d.reason, d.employeeid, a.availablefunds,
    a.creationdate, a.account_type, u.username, u.first_name, u.last_name,
    i.ticker, i.instrumentname, e.employeeusername, e.employeeRoles,
    tr.status;

INSERT INTO employees_info (
    ticket_id,
    accountid,
    username,
    first_name,
    last_name,
    support_reason,
    created_at,
    status,
    assigned_to,
    employeeusername,
    employee_roles,
    closed_at
)
SELECT
    st.ticket_id,
    st.accountid,
    u.username,
    u.first_name,
    u.last_name,
    st.reason,
    st.created_at,
    st.status,
    st.assigned_to,
    e.employeeusername,
    e.employeeRoles,
    st.closed_at
FROM support_ticket st
JOIN accounts a ON a.accountid = st.accountid
JOIN users u ON u.userid = a.userid
LEFT JOIN employees e ON e.employeeid = st.assigned_to;
