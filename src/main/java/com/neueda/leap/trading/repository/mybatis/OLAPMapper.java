package com.neueda.leap.trading.repository.mybatis;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface OLAPMapper {
    
   // complete trade facts has data from request, exectute, reject , and canceled orders, oh and disputes!
    @Update("TRUNCATE TABLE trade_facts CASCADE")
    void truncateTradeFacts();
    
    @Insert({
        "INSERT INTO trade_facts (",
        "    requestid, accountid, userid, instrumentid, side, quantity,",
        "    bidprice, date_request, executeid, askprice, date_execution,",
        "    quantity_executed, cancel_id, cancel_reason, cancel_date,",
        "    reject_id, reject_reason, reject_date,",
        "    disputeid, dispute_status, dispute_category, dispute_reason,",
        "    dispute_employee_id, availablefunds, creationdate, account_type,",
        "    username, salt, userHashedSaltedPassword, ticker, instrumentname,",
        "    dispute_employeeusername, dispute_employeeRoles, trade_status",
        ")",
        "SELECT ",
        "    tr.requestid,",
        "    tr.accountid,",
        "    a.userid,",
        "    tr.instrumentid,",
        "    tr.side,",
        "    tr.quantity,",
        "    tr.bidprice,",
        "    tr.date_request,",
        "    CAST(te.executeid AS INT),",
        "    te.askprice,",
        "    te.date_execution,",
        "    te.quantity_executed,",
        "    CAST(co.cancel_id AS INT),",
        "    co.reason,",
        "    co.cancel_date,",
        "    CAST(ro.reject_id AS INT),",
        "    ro.reason,",
        "    ro.reject_date,",
        "    CAST(d.disputeid AS INT),",
        "    d.status,",
        "    d.category,",
        "    d.reason,",
        "    CAST(d.employeeid AS INT),",
        "    a.availablefunds,",
        "    a.creationdate,",
        "    a.account_type,",
        "    u.username,",
        "    u.salt,",
        "    u.userHashedSaltedPassword,",
        "    i.ticker,",
        "    i.instrumentname,",
        "    e.employeeusername,",
        "    e.employeeRoles,",
        "    CASE ",
        "        WHEN co.cancel_id IS NOT NULL THEN 'CANCELED'",
        "        WHEN d.disputeid IS NOT NULL THEN 'DISPUTED'",
        "        WHEN ro.reject_id IS NOT NULL THEN 'REJECTED'",
        "        WHEN te.executeid IS NOT NULL THEN 'EXECUTED'",
        "        ELSE 'REQUESTED'",
        "    END",
        "FROM trade_request tr",
        "LEFT JOIN accounts a ON tr.accountid = a.accountid",
        "LEFT JOIN users u ON a.userid = u.userid",
        "LEFT JOIN instruments i ON tr.instrumentid = i.instrumentid",
        "LEFT JOIN trades_executed te ON tr.requestid = te.requestid",
        "LEFT JOIN canceled_order co ON tr.requestid = co.request_id",
        "LEFT JOIN rejected_orders ro ON tr.requestid = ro.request_id",
        "LEFT JOIN disputed_orders d ON tr.requestid = d.requestid",
        "LEFT JOIN employees e ON d.employeeid = e.employeeid",
        "ORDER BY tr.requestid"
    })
    int loadTradeFacts();
    
    // pretty much consolidated account information and user information, one little neat table
    
    @Update("TRUNCATE TABLE accounts_info CASCADE")
    void truncateAccountsInfo();
    
    @Insert({
        "INSERT INTO accounts_info (",
        "    accountid, userid, username, availablefunds,",
        "    creationdate, account_type, salt, userHashedSaltedPassword",
        ")",
        "SELECT ",
        "    a.accountid,",
        "    a.userid,",
        "    u.username,",
        "    a.availablefunds,",
        "    a.creationdate,",
        "    a.account_type,",
        "    u.salt,",
        "    u.userHashedSaltedPassword",
        "FROM accounts a",
        "JOIN users u ON a.userid = u.userid",
        "ORDER BY a.accountid"
    })
    int loadAccountsInfo();
    
    //I hate my life 
    
    @Update("TRUNCATE TABLE holdings_info CASCADE")
    void truncateHoldingsInfo();
    
    @Insert({
        "INSERT INTO holdings_info (",
        "    holding_id, accountid, instrumentid, quantity, as_of_date",
        ")",
        "WITH executed_trades AS (",
        "    SELECT ",
        "        tr.accountid,",
        "        tr.instrumentid,",
        "        tr.side,",
        "        te.quantity_executed,",
        "        te.date_execution",
        "    FROM trade_request tr",
        "    JOIN trades_executed te ON tr.requestid = te.requestid",
        "    WHERE tr.status = 'EXECUTED'",
        "),",
        "holdings_calculated AS (",
        "    SELECT ",
        "        accountid,",
        "        instrumentid,",
        "        SUM(CASE ",
        "            WHEN side = 'BUY' THEN quantity_executed",
        "            WHEN side = 'SELL' THEN -quantity_executed",
        "            ELSE 0",
        "        END) as quantity,",
        "        MAX(date_execution) as as_of_date",
        "    FROM executed_trades",
        "    GROUP BY accountid, instrumentid",
        "    HAVING SUM(CASE ",
        "            WHEN side = 'BUY' THEN quantity_executed",
        "            WHEN side = 'SELL' THEN -quantity_executed",
        "            ELSE 0",
        "        END) > 0",
        ")",
        "SELECT ",
        "    ROW_NUMBER() OVER (ORDER BY accountid, instrumentid) as holding_id,",
        "    accountid,",
        "    instrumentid,",
        "    quantity,",
        "    as_of_date",
        "FROM holdings_calculated",
        "ORDER BY accountid, instrumentid"
    })
    int loadHoldingsInfo();
    
    // employees infos, its them plus all the support tickets they are assigned to
    
    @Update("TRUNCATE TABLE employees_info CASCADE")
    void truncateEmployeesInfo();
    
    @Insert({
        "INSERT INTO employees_info (",
        "    ticket_id, accountid, username, support_reason,",
        "    created_at, status, assigned_to, employeeusername, closed_at",
        ")",
        "SELECT ",
        "    st.ticket_id,",
        "    st.accountid,",
        "    u.username,",
        "    st.reason,",
        "    st.created_at,",
        "    st.status,",
        "    st.assigned_to,",
        "    e.employeeusername,",
        "    st.closed_at",
        "FROM support_ticket st",
        "LEFT JOIN accounts a ON st.accountid = a.accountid",
        "LEFT JOIN users u ON a.userid = u.userid",
        "LEFT JOIN employees e ON st.assigned_to = e.employeeid",
        "ORDER BY st.ticket_id"
    })
    int loadEmployeesInfo();
}