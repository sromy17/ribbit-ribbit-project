DROP TABLE IF EXISTS users cascade;
DROP TABLE IF EXISTS accounts cascade;
DROP TABLE IF EXISTS instruments cascade;
DROP TABLE IF EXISTS employees cascade;
DROP TABLE IF EXISTS trade_request cascade;
DROP TABLE IF EXISTS trades_executed cascade;
DROP TABLE IF EXISTS disputed_orders cascade;
DROP TABLE IF EXISTS canceled_order cascade;
DROP TABLE IF EXISTS support_ticket cascade;
DROP TABLE IF EXISTS rejected_orders cascade;
DROP TABLE IF EXISTS holdings cascade;


CREATE TABLE users
(
    userid INT PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    salt varchar(25) NOT NULL,
    userHashedSaltedPassword varchar(64) NOT NULL
);

CREATE TABLE accounts
(
    accountid INT PRIMARY KEY,
    userid INT REFERENCES users(userid) NOT NULL,
    account_name VARCHAR(50) NOT NULL,
    availablefunds NUMERIC NOT NULL DEFAULT 0,
    CHECK (availablefunds >= 0),
    creationdate TIMESTAMPTZ NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE instruments
(
    instrumentid INT PRIMARY KEY,
    ticker VARCHAR(25) NOT NULL UNIQUE,
    instrumentname VARCHAR(25) NOT NULL
);

CREATE TABLE trade_request
(
    requestid INT PRIMARY KEY,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    instrumentid INT NOT NULL REFERENCES instruments(instrumentid),
    side CHAR(5) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INT NOT NULL CHECK (quantity > 0),
    bidprice NUMERIC NOT NULL CHECK (bidprice >= 0),
    date_request TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED', 'REJECTED'))
);

CREATE TABLE trades_executed
(
    executeid INT PRIMARY KEY,
    requestid INT NOT NULL REFERENCES trade_request(requestid),
    askprice NUMERIC NOT NULL CHECK (askprice >= 0),
    date_execution TIMESTAMPTZ NOT NULL,
    quantity_executed INT NOT NULL CHECK (quantity_executed > 0)
);

CREATE TABLE employees
(
    employeeid INT PRIMARY KEY,
    employee_email VARCHAR(50) UNIQUE NOT NULL,
    employeesalt VARCHAR(25) NOT NULL,
    employeeHashedSaltedPassword VARCHAR(64) NOT NULL,
    employeeRoles VARCHAR(20)
);

CREATE TABLE disputed_orders
(
    disputeid INT PRIMARY KEY NOT NULL,
    requestid INT REFERENCES trade_request(requestid) NOT NULL,
    status VARCHAR(20) DEFAULT 'DISPUTED' CHECK (status IN ('DISPUTED', 'UNDER-REVIEW', 'COMPLETED')),
    employeeid int DEFAULT NULL REFERENCES employees(employeeid),
    category VARCHAR(20) CHECK (category IN ('Fraud', 'Accident', 'Incorrect transfer')),
    reason VARCHAR(75)
);

-- table for orders canceled by the user(lmao)
CREATE TABLE canceled_order
(
    cancel_id INT PRIMARY KEY NOT NULL,
    request_id INT REFERENCES trade_request(requestid) NOT NULL,
    reason VARCHAR(50),
    cancel_date TIMESTAMPTZ NOT NULL
);

CREATE TABLE support_ticket
(
    ticket_id INT PRIMARY KEY NOT NULL,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    reason VARCHAR(75) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    assigned_to INT REFERENCES employees(employeeid),
    closed_at TIMESTAMPTZ DEFAULT NULL
);

-- literally 1984!!!!!! The system dont want me to trade!!!!!
CREATE TABLE rejected_orders
(
    reject_id INT PRIMARY KEY NOT NULL,
    request_id INT REFERENCES trade_request(requestid) NOT NULL,
    reason VARCHAR(50),
    reject_date TIMESTAMPTZ NOT NULL
);

-- ho wmuch one user owns of one particular stock
CREATE TABLE holdings
(
    holdingid INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    instrumentid INT NOT NULL REFERENCES instruments(instrumentid),
    quantity INT NOT NULL CHECK (quantity >= 0),
    as_of_date TIMESTAMPTZ NOT NULL,
    UNIQUE (accountid, instrumentid)
);
-- the clock dont stop, the clock dont stop, the clock dont stop, the clock dont stop


CREATE TABLE banned_users
(
    banid INT PRIMARY KEY,
    userid INT NOT NULL REFERENCES users(userid),
    reason VARCHAR(100) NOT NULL,
    ban_date TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_sessions
(
    session_id INT PRIMARY KEY NOT NULL,
    userid INT NOT NULL REFERENCES users(userid),
    refreshTokenHash varchar(64),
    dateIssued TIMESTAMPTZ NOT NULL,
    dateExpired TIMESTAMPTZ NOT NULL,
    isRevoked BOOLEAN NOT NULL
);

CREATE TABLE account_bank_details
(
    bank_detail_id INT PRIMARY KEY NOT NULL,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    bank_name VARCHAR(100) NOT NULL,
    routing_number VARCHAR(20) NOT NULL
);