-- ============================================================
-- OLAP DATA WAREHOUSE SCHEMA
-- For analytics and business intelligence
-- Data flows here via ETL from OLTP
-- ============================================================

-- OLAP: Denormalized trade facts table for analytics
CREATE TABLE trade_facts
(
    requestid INT PRIMARY KEY,
    accountid INT,
    userid INT,
    instrumentid INT,
    side CHAR(5),
    quantity INT,
    bidprice NUMERIC,
    date_request DATE,
    executeid INT,
    askprice NUMERIC,
    date_execution DATE,
    quantity_executed INT,
    cancel_id INT,
    cancel_reason VARCHAR(20),
    cancel_date DATE,
    reject_id INT,
    reject_reason VARCHAR(50),
    reject_date DATE,
    disputeid INT,
    dispute_status VARCHAR(20),
    dispute_category VARCHAR(20),
    dispute_reason VARCHAR(75),
    dispute_employee_id INT,
    availablefunds NUMERIC,
    creationdate DATE,
    account_type VARCHAR(20),
    username VARCHAR(50),
    salt VARCHAR(25),
    userHashedSaltedPassword VARCHAR(64),
    ticker VARCHAR(25),
    instrumentname VARCHAR(25),
    dispute_employeeusername VARCHAR(50),
    dispute_employeeRoles VARCHAR(20),
    trade_status VARCHAR(20)
);

-- OLAP: Consolidated account information
CREATE TABLE accounts_info
(
    accountid INT PRIMARY KEY,
    userid INT,
    username VARCHAR(50),
    availablefunds NUMERIC,
    creationdate DATE,
    account_type VARCHAR(20),
    salt VARCHAR(25),
    userHashedSaltedPassword VARCHAR(64)
);

-- OLAP: Holdings information (denormalized from trades)
CREATE TABLE holdings_info
(
    holding_id INT PRIMARY KEY,
    accountid INT,
    instrumentid INT,
    quantity INT,
    as_of_date DATE
);

-- OLAP: Employees and support tickets
CREATE TABLE employees_info
(
    ticket_id INT PRIMARY KEY,
    accountid INT,
    username VARCHAR(50),
    support_reason VARCHAR(75),
    created_at TIMESTAMP,
    status VARCHAR(20),
    assigned_to INT,
    employeeusername VARCHAR(50),
    closed_at TIMESTAMP
);
