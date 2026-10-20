DROP TABLE IF EXISTS users cascade;
DROP TABLE IF EXISTS audit_records cascade;
DROP TABLE IF EXISTS sessions cascade;
DROP TABLE IF EXISTS user_roles cascade;
DROP TABLE IF EXISTS roles cascade;
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
    username varchar(50) UNIQUE NOT NULL,
    salt varchar(25) NOT NULL,
    userHashedSaltedPassword varchar(64) NOT NULL
);

CREATE TABLE roles
(
	roleid SERIAL PRIMARY KEY,
	rolename VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE user_roles
(
	roleid INT NOT NULL REFERENCES roles(roleid),
	userid INT NOT NULL REFERENCES users(userid),
	PRIMARY KEY (roleid, userid)
);

CREATE TABLE sessions
(
	sessionid SERIAL PRIMARY KEY,
	userid INT NOT NULL REFERENCES users(userid),
	accesstoken VARCHAR(256) UNIQUE NOT NULL,
	expiresat TIMESTAMP NOT NULL
);

CREATE TABLE audit_records
(
	auditid BIGSERIAL PRIMARY KEY,
	actor_user_id INT REFERENCES users(userid),
	actor_username VARCHAR(50) NOT NULL,
	action VARCHAR(80) NOT NULL,
	target_type VARCHAR(80) NOT NULL,
	target_id VARCHAR(100),
	outcome VARCHAR(30) NOT NULL,
	occurred_at TIMESTAMP NOT NULL
);

CREATE TABLE accounts
(
    accountid INT PRIMARY KEY,
    userid INT REFERENCES users(userid) NOT NULL,
    availablefunds NUMERIC NOT NULL DEFAULT 0,
    CHECK (availablefunds >= 0),
    creationdate DATE NOT NULL,
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
    date_request DATE NOT NULL,
    created_at_utc TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id VARCHAR(36) NOT NULL DEFAULT '',
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED', 'REJECTED'))
);

CREATE TABLE trades_executed
(
    executeid INT PRIMARY KEY,
    requestid INT NOT NULL REFERENCES trade_request(requestid),
    askprice NUMERIC NOT NULL CHECK (askprice >= 0),
    date_execution DATE NOT NULL,
    executed_at_utc TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fee NUMERIC NOT NULL DEFAULT 0,
    quantity_executed INT NOT NULL CHECK (quantity_executed > 0)
);

CREATE TABLE order_lifecycle_events
(
    event_id BIGSERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES trade_request(requestid),
    account_id INT NOT NULL REFERENCES accounts(accountid),
    instrument_id INT REFERENCES instruments(instrumentid),
    event_type VARCHAR(60) NOT NULL,
    event_sequence BIGINT NOT NULL,
    event_version INT NOT NULL DEFAULT 1,
    occurred_at_utc TIMESTAMP NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    quote_source VARCHAR(80), quote_at_utc TIMESTAMP, quote_price NUMERIC,
    decision_price NUMERIC, quantity INT, fee NUMERIC, reason VARCHAR(500),
    UNIQUE (order_id, event_sequence)
);

CREATE TABLE cash_movements
(
    movement_id BIGSERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES trade_request(requestid),
    account_id INT NOT NULL REFERENCES accounts(accountid),
    amount NUMERIC NOT NULL, currency CHAR(3) NOT NULL,
    movement_type VARCHAR(40) NOT NULL, occurred_at_utc TIMESTAMP NOT NULL,
    correlation_id VARCHAR(36) NOT NULL
);

CREATE TABLE holding_movements
(
    movement_id BIGSERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES trade_request(requestid),
    account_id INT NOT NULL REFERENCES accounts(accountid),
    instrument_id INT NOT NULL REFERENCES instruments(instrumentid),
    quantity_delta INT NOT NULL, quantity_after INT NOT NULL,
    occurred_at_utc TIMESTAMP NOT NULL, correlation_id VARCHAR(36) NOT NULL
);

CREATE TABLE employees
(
    employeeid INT PRIMARY KEY,
    employeeusername VARCHAR(50) UNIQUE NOT NULL,
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
    cancel_date DATE NOT NULL
);

CREATE TABLE support_ticket
(
    ticket_id INT PRIMARY KEY NOT NULL,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    reason VARCHAR(75) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    assigned_to INT REFERENCES employees(employeeid),
    closed_at TIMESTAMP DEFAULT NULL
);

-- literally 1984!!!!!! The system dont want me to trade!!!!!
CREATE TABLE rejected_orders
(
    reject_id INT PRIMARY KEY NOT NULL,
    request_id INT REFERENCES trade_request(requestid) NOT NULL,
    reason VARCHAR(50),
    reject_date DATE NOT NULL
);

-- ho wmuch one user owns of one particular stock
CREATE TABLE holdings
(
    holdingid INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    instrumentid INT NOT NULL REFERENCES instruments(instrumentid),
    quantity INT NOT NULL CHECK (quantity >= 0),
    average_cost NUMERIC NOT NULL DEFAULT 0,
    as_of_date DATE NOT NULL,
    UNIQUE (accountid, instrumentid)
);
-- the clock dont stop, the clock dont stop, the clock dont stop, the clock dont stop

CREATE TABLE user_sessions
(
	userid INT REFERENCES users(userid) PRIMARY KEY,
	refreshToken INT,	
	dateIssued DATE
);
