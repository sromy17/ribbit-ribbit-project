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
drop table if exists bank cascade;
drop table if exists bank_movement cascade;
drop table if exists refresh_tokens cascade;
drop table if exists access_tokens cascade;
DROP TABLE IF EXISTS watchlist cascade;
DROP TABLE IF EXISTS access_tokens_employee cascade;
DROP TABLE IF EXISTS refresh_tokens_employee cascade;

CREATE TABLE users
(
    userid INT PRIMARY KEY,
    username varchar(50) UNIQUE NOT NULL,
    salt varchar(25) NOT NULL,
    userHashedSaltedPassword varchar(64) NOT NULL,
    first_name varchar(50) NOT NULL,
    last_name varchar(50) NOT NULL
);

CREATE TABLE accounts
(
    accountid INT PRIMARY KEY,
    userid INT REFERENCES users(userid) NOT NULL,
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
    employeeusername VARCHAR(50) UNIQUE NOT NULL,
    employeesalt VARCHAR(25) NOT NULL,
    employeeHashedSaltedPassword VARCHAR(64) NOT NULL,
    employeeRoles VARCHAR(20) NOT NULL
        CHECK (employeeRoles IN ('ADMIN', 'ANALYST', 'SUPPORT', 'COMPLIANCE', 'DISPUTE_HANDLER'))
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

--
CREATE TABLE refresh_tokens
(
    refreshTokenId INT PRIMARY KEY,
    userid INT REFERENCES users(userid) NOT NULL,
    refreshTokenHash VARCHAR(64) NOT NULL UNIQUE,
    dateIssued TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dateExpires TIMESTAMPTZ NOT NULL,
    isRevoked BOOLEAN NOT NULL DEFAULT FALSE
);

--"Thats why im moving to asia (fuck america)" - Sukie
CREATE TABLE access_tokens
(
    accessTokenId INT PRIMARY KEY,
    refreshTokenId INT NOT NULL REFERENCES refresh_tokens(refreshTokenId),
    accessTokenHash VARCHAR(64) NOT NULL UNIQUE,
    dateIssued TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dateExpires TIMESTAMPTZ NOT NULL,
    isRevoked BOOLEAN NOT NULL DEFAULT FALSE
);
-- the clock dont stop, the clock dont stop, the clock dont stop, the clock dont stop
CREATE TABLE bank
(
    bankid INT PRIMARY KEY,
    accountid INT NOT NULL REFERENCES accounts(accountid),
    accountName varchar(50) NOT NULL,
    routing_number varchar(9) NOT NULL,
    account_number varchar(17) NOT NULL,
    balance NUMERIC NOT NULL CHECK (balance >= 0),
    CONSTRAINT uq_bank_account_details UNIQUE (accountid, routing_number, account_number)
);
--evil money you can sell your soul for a little change - "Acid Souljah"
CREATE table bank_movement
(
    movement_id INT PRIMARY KEY,
    bankid INT NOT NULL REFERENCES bank(bankid),
    amount NUMERIC NOT NULL CHECK (amount >= 0),
    movement_type VARCHAR(10) NOT NULL CHECK (movement_type IN ('DEPOSIT', 'WITHDRAWAL')),
    movement_date TIMESTAMPTZ NOT NULL
);

CREATE TABLE watchlist
(
	watchlist_id INT PRIMARY KEY,
	accountid INT NOT NULL REFERENCES accounts(accountid),
	instrumentid INT NOT NULL REFERENCES instruments(instrumentid)
);

CREATE TABLE refresh_tokens_employee
(
    refreshTokenId BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employeeid INT REFERENCES employees(employeeid) NOT NULL,
    refreshTokenHash VARCHAR(64) NOT NULL UNIQUE,
    dateIssued TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dateExpires TIMESTAMPTZ NOT NULL,
    isRevoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE access_tokens_employee
(
    accessTokenId BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    refreshTokenId BIGINT NOT NULL REFERENCES refresh_tokens_employee(refreshTokenId),
    accessTokenHash VARCHAR(64) NOT NULL UNIQUE,
    dateIssued TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dateExpires TIMESTAMPTZ NOT NULL,
    isRevoked BOOLEAN NOT NULL DEFAULT FALSE
);

-- and i hate to see a good thing just go to waste, but honey thats, just a little weak, a little weak for my taste. its 