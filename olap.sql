DROP TABLE IF EXISTS trade_facts cascade;
DROP TABLE IF EXISTS accounts_info cascade;
DROP TABLE IF EXISTS employees_info cascade;
DROP TABLE IF EXISTS holdings_info cascade;

-- cash money ap!!!!
CREATE TABLE if NOT EXISTS trade_facts
(
    requestid INT PRIMARY KEY,
    accountid INT,
    userid INT,
    instrumentid INT,
    side CHAR(5),
    quantity_requested INT,
    bidprice NUMERIC,
    date_request TIMESTAMPTZ,
    executeid INT,
    execution_count INT,
    last_execution_price NUMERIC,
    first_execution_date TIMESTAMPTZ,
    last_execution_date TIMESTAMPTZ,
    quantity_executed INT,
    cancel_id INT,
    cancel_reason VARCHAR(50),
    cancel_date TIMESTAMPTZ,
    reject_id INT,
    reject_reason VARCHAR(50),
    reject_date TIMESTAMPTZ,
    disputeid INT,
    dispute_status VARCHAR(20),
    dispute_category VARCHAR(20),
    dispute_reason VARCHAR(75),
    dispute_employee_id INT,
    availablefunds NUMERIC,
    creationdate TIMESTAMPTZ,
    account_type VARCHAR(20),
    username VARCHAR(50),
    ticker VARCHAR(25),
    instrumentname VARCHAR(25),
    dispute_employeeusername VARCHAR(50),
    dispute_employeeRoles VARCHAR(20),
    order_status VARCHAR(20),
    lifecycle_status VARCHAR(20),
    last_synced_at TIMESTAMPTZ
    );
--Wyclef jean was a good guy
CREATE TABLE if NOT EXISTS accounts_info
(
    accountid INT PRIMARY KEY,
    userid INT,
    username VARCHAR(50),
    availablefunds NUMERIC,
    creationdate TIMESTAMPTZ,
    account_type VARCHAR(20)
    );
-- rain drop
CREATE TABLE if NOT EXISTS employees_info
(
    ticket_id INT PRIMARY KEY,
    accountid INT,
    username VARCHAR(50),
    support_reason VARCHAR(75),
    created_at TIMESTAMPTZ,
    status VARCHAR(20),
    assigned_to INT,
    employeeusername VARCHAR(50),
    employee_roles VARCHAR(20),
    closed_at TIMESTAMPTZ
    );

-- TYBG
CREATE TABLE if NOT EXISTS holdings_info
(
    accountid INT NOT NULL,
    userid INT,
    username VARCHAR(50),
    instrumentid INT,
    ticker VARCHAR(25),
    instrumentname VARCHAR(25),
    quantity INT,
    as_of_date TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    PRIMARY KEY (accountid, instrumentid)
    );
