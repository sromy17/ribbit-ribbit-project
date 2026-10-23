DROP TABLE IF EXISTS trade_facts cascade;
DROP TABLE IF EXISTS accounts_info cascade;
DROP TABLE IF EXISTS employees_info cascade;
DROP TABLE IF EXISTS holdings_info cascade;
DROP TABLE IF EXISTS etl_refresh_runs cascade;
DROP TABLE IF EXISTS order_facts cascade;
DROP TABLE IF EXISTS fill_facts cascade;
DROP TABLE IF EXISTS lifecycle_event_facts cascade;
DROP TABLE IF EXISTS cash_movement_facts cascade;
DROP TABLE IF EXISTS holding_movement_facts cascade;
DROP TABLE IF EXISTS instruments_info cascade;
DROP TABLE IF EXISTS clients_info cascade;
DROP TABLE IF EXISTS client_segment_history cascade;

CREATE TABLE IF NOT EXISTS etl_refresh_runs
(
    run_id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    source_watermark TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    error_message VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS order_facts
(
    requestid INT PRIMARY KEY, accountid INT NOT NULL, userid INT NOT NULL,
    instrumentid INT, side CHAR(5), quantity_requested INT, bidprice NUMERIC,
    order_status VARCHAR(20), created_at_utc TIMESTAMPTZ, correlation_id VARCHAR(36),
    dataset_run_id VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS fill_facts
(
    executeid INT PRIMARY KEY, requestid INT NOT NULL, accountid INT NOT NULL,
    instrumentid INT, executed_quantity INT NOT NULL, execution_price NUMERIC NOT NULL,
    fee NUMERIC NOT NULL, executed_at_utc TIMESTAMPTZ,
    client_id INT, execution_currency CHAR(3), fee_currency CHAR(3), quantity_unit VARCHAR(40),
    contract_multiplier NUMERIC, product_convention VARCHAR(80), pricing_event_id BIGINT,
    dataset_run_id VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS lifecycle_event_facts
(
    event_id BIGINT PRIMARY KEY, requestid INT NOT NULL, accountid INT NOT NULL,
    instrumentid INT, event_type VARCHAR(60) NOT NULL, event_sequence BIGINT NOT NULL,
    occurred_at_utc TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(36) NOT NULL,
    quote_source VARCHAR(80), quote_at_utc TIMESTAMPTZ, quote_price NUMERIC,
    quote_received_at_utc TIMESTAMPTZ, quote_currency CHAR(3), quote_price_type VARCHAR(40),
    decision_price NUMERIC, quantity INT, fee NUMERIC, reason VARCHAR(500),
    dataset_run_id VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS cash_movement_facts
(
    movement_id BIGINT PRIMARY KEY, requestid INT NOT NULL, accountid INT NOT NULL,
    amount NUMERIC NOT NULL, currency CHAR(3), currency_source VARCHAR(80), movement_type VARCHAR(40) NOT NULL,
    occurred_at_utc TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(36) NOT NULL,
    dataset_run_id VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS holding_movement_facts
(
    movement_id BIGINT PRIMARY KEY, requestid INT NOT NULL, accountid INT NOT NULL,
    instrumentid INT NOT NULL, quantity_delta INT NOT NULL, quantity_after INT NOT NULL,
    occurred_at_utc TIMESTAMPTZ NOT NULL, correlation_id VARCHAR(36) NOT NULL,
    dataset_run_id VARCHAR(36) NOT NULL
);

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
    date_request DATE,
    executeid INT,
    execution_count INT,
    last_execution_price NUMERIC,
    first_execution_date DATE,
    last_execution_date DATE,
    quantity_executed INT,
    cancel_id INT,
    cancel_reason VARCHAR(50),
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
    currency CHAR(3),
    creationdate DATE,
    account_type VARCHAR(20)
    );

CREATE TABLE IF NOT EXISTS instruments_info
(
    instrumentid INT PRIMARY KEY,
    ticker VARCHAR(25) NOT NULL,
    instrumentname VARCHAR(25) NOT NULL,
    asset_class VARCHAR(30), market VARCHAR(40), venue VARCHAR(80),
    quote_currency CHAR(3), settlement_currency CHAR(3), quantity_unit VARCHAR(40),
    price_precision INT, quantity_precision INT, contract_multiplier NUMERIC,
    product_convention VARCHAR(80), metadata_source VARCHAR(200), market_data_symbol VARCHAR(80),
    dataset_run_id VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS clients_info
(
    client_id INT PRIMARY KEY,
    registered_at_utc TIMESTAMPTZ,
    dataset_run_id VARCHAR(36) NOT NULL
);

CREATE TABLE IF NOT EXISTS client_segment_history
(
    membership_id BIGINT PRIMARY KEY, client_id INT NOT NULL,
    segment_code VARCHAR(60) NOT NULL, source VARCHAR(200) NOT NULL,
    effective_from_utc TIMESTAMPTZ NOT NULL, effective_to_utc TIMESTAMPTZ,
    recorded_at_utc TIMESTAMPTZ NOT NULL, dataset_run_id VARCHAR(36) NOT NULL,
    UNIQUE (client_id, effective_from_utc),
    CHECK (effective_to_utc IS NULL OR effective_to_utc > effective_from_utc)
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
    as_of_date DATE,
    last_synced_at TIMESTAMPTZ,
    PRIMARY KEY (accountid, instrumentid)
    );
