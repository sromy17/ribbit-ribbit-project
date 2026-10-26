DROP TABLE IF EXISTS users cascade;
DROP TABLE IF EXISTS client_segment_history cascade;
DROP TABLE IF EXISTS audit_records cascade;
DROP TABLE IF EXISTS dispute_case_events cascade;
DROP TABLE IF EXISTS dispute_cases cascade;
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
drop table if exists bank cascade;
drop table if exists bank_movement cascade;
drop table if exists refresh_tokens cascade;
drop table if exists access_tokens cascade;

CREATE TABLE users
(
    userid INT PRIMARY KEY,
    username varchar(50) UNIQUE NOT NULL,
    registered_at_utc TIMESTAMPTZ,
    salt varchar(25) NOT NULL,
    userHashedSaltedPassword varchar(64) NOT NULL,
    first_name varchar(50) NOT NULL,
    last_name varchar(50) NOT NULL
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
	accesstoken VARCHAR(4096) UNIQUE NOT NULL,
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
    currency CHAR(3),
    CHECK (availablefunds >= 0),
    creationdate TIMESTAMPTZ NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE instruments
(
    instrumentid INT PRIMARY KEY,
    ticker VARCHAR(25) NOT NULL,
    instrumentname VARCHAR(25) NOT NULL,
    asset_class VARCHAR(30), market VARCHAR(40), venue VARCHAR(80),
    quote_currency CHAR(3), settlement_currency CHAR(3), quantity_unit VARCHAR(40),
    price_precision INT CHECK (price_precision >= 0),
    quantity_precision INT CHECK (quantity_precision >= 0),
    contract_multiplier NUMERIC CHECK (contract_multiplier > 0),
    product_convention VARCHAR(80), metadata_source VARCHAR(200),
    market_data_symbol VARCHAR(80)
);

CREATE TABLE client_segment_history
(
    membership_id BIGSERIAL PRIMARY KEY,
    client_id INT NOT NULL REFERENCES users(userid),
    segment_code VARCHAR(60) NOT NULL,
    source VARCHAR(200) NOT NULL,
    effective_from_utc TIMESTAMPTZ NOT NULL,
    effective_to_utc TIMESTAMPTZ,
    recorded_at_utc TIMESTAMPTZ NOT NULL,
    UNIQUE (client_id, effective_from_utc),
    CHECK (effective_to_utc IS NULL OR effective_to_utc > effective_from_utc)
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
    created_at_utc TIMESTAMPTZ,
    correlation_id VARCHAR(36) NOT NULL DEFAULT '',
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED', 'REJECTED'))
);

CREATE TABLE trades_executed
(
    executeid INT PRIMARY KEY,
    requestid INT NOT NULL REFERENCES trade_request(requestid),
    askprice NUMERIC NOT NULL CHECK (askprice >= 0),
    date_execution DATE NOT NULL,
    executed_at_utc TIMESTAMPTZ,
    client_id INT REFERENCES users(userid),
    execution_currency CHAR(3), fee_currency CHAR(3), quantity_unit VARCHAR(40),
    contract_multiplier NUMERIC CHECK (contract_multiplier > 0),
    product_convention VARCHAR(80), pricing_event_id BIGINT,
    fee NUMERIC NOT NULL DEFAULT 0,
    quantity_executed INT NOT NULL CHECK (quantity_executed > 0)
);

CREATE TABLE dispute_cases
(
    case_id BIGSERIAL PRIMARY KEY,
    order_id INT REFERENCES trade_request(requestid),
    account_id INT NOT NULL REFERENCES accounts(accountid),
    customer_user_id INT NOT NULL REFERENCES users(userid),
    transaction_type VARCHAR(20) NOT NULL,
    transaction_reference VARCHAR(100) NOT NULL,
    cash_movement_id BIGINT,
    title VARCHAR(120) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    fee_related BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    assigned_staff_user_id INT REFERENCES users(userid),
    resolution_reason VARCHAR(1000),
    idempotency_key VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at_utc TIMESTAMPTZ NOT NULL,
    updated_at_utc TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (customer_user_id, idempotency_key)
);

CREATE TABLE dispute_case_events
(
    event_id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL REFERENCES dispute_cases(case_id),
    event_type VARCHAR(40) NOT NULL,
    actor_user_id INT NOT NULL REFERENCES users(userid),
    note VARCHAR(2000),
    occurred_at_utc TIMESTAMPTZ NOT NULL,
    case_version BIGINT NOT NULL
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
    occurred_at_utc TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    quote_source VARCHAR(80), quote_at_utc TIMESTAMPTZ, quote_price NUMERIC,
    quote_received_at_utc TIMESTAMPTZ, quote_currency CHAR(3), quote_price_type VARCHAR(40),
    decision_price NUMERIC, quantity INT, fee NUMERIC, reason VARCHAR(500),
    UNIQUE (order_id, event_sequence)
);

CREATE TABLE cash_movements
(
    movement_id BIGSERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES trade_request(requestid),
    account_id INT NOT NULL REFERENCES accounts(accountid),
    amount NUMERIC NOT NULL, currency CHAR(3), currency_source VARCHAR(80),
    movement_type VARCHAR(40) NOT NULL, occurred_at_utc TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(36) NOT NULL
);

CREATE TABLE holding_movements
(
    movement_id BIGSERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES trade_request(requestid),
    account_id INT NOT NULL REFERENCES accounts(accountid),
    instrument_id INT NOT NULL REFERENCES instruments(instrumentid),
    quantity_delta INT NOT NULL, quantity_after INT NOT NULL,
    occurred_at_utc TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(36) NOT NULL
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
    average_cost NUMERIC NOT NULL DEFAULT 0,
    as_of_date DATE NOT NULL,
    UNIQUE (accountid, instrumentid)
);
-- the clock dont stop, the clock dont stop, the clock dont stop, the clock dont stop

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
