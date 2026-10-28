/* SYNTHETIC DEMO DATA ONLY. Run in ribbit-oltp after schema + migrations. */
/* Customer usernames/password are publicly known sample credentials. */
/* One-time import: conflicting IDs/usernames fail and roll back; no existing data is overwritten. */
BEGIN;
SET LOCAL TIME ZONE 'UTC';
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM users WHERE userid BETWEEN 1 AND 5 OR username IN ('john_trader','jane_investor','bob_daytrader','alice_portfolio','charlie_hedge'))
 OR EXISTS(SELECT 1 FROM accounts WHERE accountid BETWEEN 101 AND 105)
 OR EXISTS(SELECT 1 FROM employees WHERE employeeid BETWEEN 1 AND 4)
 OR EXISTS(SELECT 1 FROM trade_request WHERE requestid BETWEEN 2001 AND 2007) THEN
  RAISE EXCEPTION 'Seed IDs or usernames already exist. No changes applied; do not overwrite existing accounts.';
 END IF;
END $$;
/* Seed users before dependent accounts and token records. */
INSERT INTO users (
    userid,
    username,
    salt,
    userHashedSaltedPassword,
    first_name,
    last_name
) VALUES
    (1, 'john_trader', 'bcrypt', '$2a$10$fFRDi5/CnTE0b67oOd8KheUPLhWJ1pDZmY5YJ9FGrgZ/Eq6j0Ucfm', 'John', 'Trader'),
    (2, 'jane_investor', 'bcrypt', '$2a$10$fFRDi5/CnTE0b67oOd8KheUPLhWJ1pDZmY5YJ9FGrgZ/Eq6j0Ucfm', 'Jane', 'Investor'),
    (3, 'bob_daytrader', 'bcrypt', '$2a$10$fFRDi5/CnTE0b67oOd8KheUPLhWJ1pDZmY5YJ9FGrgZ/Eq6j0Ucfm', 'Bob', 'Daytrader'),
    (4, 'alice_portfolio', 'bcrypt', '$2a$10$fFRDi5/CnTE0b67oOd8KheUPLhWJ1pDZmY5YJ9FGrgZ/Eq6j0Ucfm', 'Alice', 'Portfolio'),
    (5, 'charlie_hedge', 'bcrypt', '$2a$10$fFRDi5/CnTE0b67oOd8KheUPLhWJ1pDZmY5YJ9FGrgZ/Eq6j0Ucfm', 'Charlie', 'Hedge');

INSERT INTO employees (
    employeeid,
    employeeusername,
    employeesalt,
    employeeHashedSaltedPassword,
    employeeRoles
) VALUES
    (1, 'admin_user', 'emp_salt1', 'emp_hashed_1', 'ADMIN'),
    (2, 'support_staff', 'emp_salt2', 'emp_hashed_2', 'SUPPORT'),
    (3, 'compliance_officer', 'emp_salt3', 'emp_hashed_3', 'COMPLIANCE'),
    (4, 'dispute_resolver', 'emp_salt4', 'emp_hashed_4', 'DISPUTE_HANDLER');

INSERT INTO accounts (
    accountid,
    userid,
    availablefunds,
    creationdate,
    account_type
) VALUES
    (101, 1, 50000.00, '2024-01-15 09:00:00+00', 'TRADING'),
    (102, 2, 75000.00, '2024-02-20 09:00:00+00', 'INVESTMENT'),
    (103, 3, 100000.00, '2024-03-10 09:00:00+00', 'DAYTRADING'),
    (104, 4, 250000.00, '2024-01-05 09:00:00+00', 'PORTFOLIO'),
    (105, 5, 500000.00, '2023-12-01 09:00:00+00', 'HEDGE');

CREATE TEMP TABLE seed_instruments(old_id int PRIMARY KEY, ticker text, name text) ON COMMIT DROP;
INSERT INTO seed_instruments VALUES (1001, 'AAPL', 'Apple Inc'),
    (1002, 'MSFT', 'Microsoft Corporation'),
    (1003, 'GOOGL', 'Alphabet Inc'),
    (1004, 'TSLA', 'Tesla Inc'),
    (1005, 'AMZN', 'Amazon.com Inc'),
    (1006, 'META', 'Meta Platforms Inc'),
    (1007, 'NVDA', 'NVIDIA Corporation');
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM instruments i JOIN seed_instruments s ON upper(i.ticker)=s.ticker GROUP BY s.ticker HAVING count(*)>1) THEN
  RAISE EXCEPTION 'Ambiguous instrument tickers; provide explicit venue mapping before seeding';
 END IF;
END $$;
LOCK TABLE instruments IN SHARE ROW EXCLUSIVE MODE;
INSERT INTO instruments(instrumentid,ticker,instrumentname,status,asset_class,market,quote_currency,settlement_currency,quantity_unit,contract_multiplier,product_convention,metadata_source,market_data_symbol)
SELECT (SELECT coalesce(max(instrumentid),0) FROM instruments)+row_number() OVER (ORDER BY s.old_id),
 s.ticker,s.name,'ACTIVE','EQUITY','US','USD','USD','SHARE',1,'PRICE_PER_UNIT','SYNTHETIC_DEMO',s.ticker
FROM seed_instruments s WHERE NOT EXISTS (SELECT 1 FROM instruments i WHERE upper(i.ticker)=s.ticker);
CREATE TEMP VIEW seed_instrument_ids AS SELECT s.old_id,i.instrumentid FROM seed_instruments s JOIN instruments i ON upper(i.ticker)=s.ticker;

CREATE TEMP TABLE seed_orders (
 requestid int, accountid int, instrumentid int, side text, quantity int,
 bidprice numeric, date_request timestamptz, status text
) ON COMMIT DROP;
INSERT INTO seed_orders (
    requestid,
    accountid,
    instrumentid,
    side,
    quantity,
    bidprice,
    date_request,
    status
) VALUES
    (2001, 101, 1001, 'BUY', 10, 150.50, '2024-09-20 10:00:00+00', 'EXECUTED'),
    (2002, 102, 1002, 'BUY', 25, 380.00, '2024-09-21 10:00:00+00', 'EXECUTED'),
    (2003, 103, 1003, 'SELL', 5, 140.75, '2024-09-21 11:00:00+00', 'EXECUTED'),
    (2004, 104, 1004, 'BUY', 15, 245.30, '2024-09-22 10:00:00+00', 'REJECTED'),
    (2005, 105, 1005, 'SELL', 20, 180.00, '2024-09-22 11:00:00+00', 'REJECTED'),
    (2006, 101, 1006, 'BUY', 8, 480.50, '2024-09-23 10:00:00+00', 'EXECUTED'),
    (2007, 102, 1007, 'BUY', 12, 875.25, '2024-09-23 11:00:00+00', 'CANCELLED');
INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,created_at_utc,correlation_id,status)
SELECT o.requestid,o.accountid,i.instrumentid,o.side,o.quantity,o.bidprice,
 o.date_request::date,o.date_request,'seed-'||o.requestid,o.status
FROM seed_orders o JOIN seed_instrument_ids i ON i.old_id=o.instrumentid;


CREATE TEMP TABLE seed_fills(executeid int,requestid int,askprice numeric,date_execution timestamptz,quantity_executed int) ON COMMIT DROP;
INSERT INTO seed_fills (
    executeid,
    requestid,
    askprice,
    date_execution,
    quantity_executed
) VALUES
    (3001, 2001, 150.75, '2024-09-20 10:05:00+00', 10),
    (3002, 2002, 380.50, '2024-09-21 10:05:00+00', 25),
    (3003, 2003, 140.50, '2024-09-21 11:05:00+00', 5),
    (3004, 2006, 481.00, '2024-09-23 10:05:00+00', 8);
INSERT INTO trades_executed(executeid,requestid,askprice,date_execution,executed_at_utc,client_id,execution_currency,fee_currency,quantity_unit,contract_multiplier,product_convention,fee,quantity_executed)
SELECT f.executeid,f.requestid,f.askprice,f.date_execution::date,f.date_execution,a.userid,
 'USD',NULL,'SHARE',1,'PRICE_PER_UNIT',NULL,f.quantity_executed
FROM seed_fills f JOIN trade_request o ON o.requestid=f.requestid JOIN accounts a ON a.accountid=o.accountid;


INSERT INTO disputed_orders (
    disputeid,
    requestid,
    status,
    employeeid,
    category,
    reason
) VALUES
    (4001, 2001, 'COMPLETED', 3, 'Accident', 'Price discrepancy resolved'),
    (4002, 2002, 'UNDER-REVIEW', 2, 'Fraud', 'Suspicious activity detected'),
    (4003, 2003, 'DISPUTED', NULL, 'Incorrect transfer', 'User reported wrong quantity');

INSERT INTO canceled_order (
    cancel_id,
    request_id,
    reason,
    cancel_date
) VALUES
    (5001, 2007, 'User canceled order', '2024-09-23 12:00:00+00');

INSERT INTO rejected_orders (
    reject_id,
    request_id,
    reason,
    reject_date
) VALUES
    (7001, 2004, 'Insufficient funds', '2024-09-22 10:05:00+00'),
    (7002, 2005, 'Market closed', '2024-09-22 11:05:00+00');

INSERT INTO support_ticket (
    ticket_id,
    accountid,
    reason,
    status,
    assigned_to,
    created_at,
    closed_at
) VALUES
    (6001, 101, 'Account verification issue', 'OPEN', NULL, '2024-09-20 10:30:00+00', NULL),
    (6002, 102, 'Withdrawal delayed', 'IN_PROGRESS', 2, '2024-09-21 14:15:00+00', NULL),
    (6003, 103, 'Trade settlement discrepancy', 'RESOLVED', 3, '2024-09-21 09:45:00+00', '2024-09-22 16:20:00+00');





INSERT INTO bank (
    bankid,
    accountid,
    accountName,
    routing_number,
    account_number,
    balance
) VALUES
    (10001, 101, 'John Checking', '021000021', '1234567890', 25000.00),
    (10002, 102, 'Jane Brokerage Bank', '021000022', '2234567890', 40000.00),
    (10003, 103, 'Bob Trading Bank', '021000023', '3234567890', 60000.00);

INSERT INTO bank_movement (
    movement_id,
    bankid,
    amount,
    movement_type,
    movement_date
) VALUES
    (11001, 10001, 25000.00, 'DEPOSIT', '2024-09-20 09:30:00+00'),
    (11002, 10002, 40000.00, 'DEPOSIT', '2024-09-21 09:30:00+00'),
    (11003, 10003, 10000.00, 'DEPOSIT', '2024-09-22 09:30:00+00'),
    (11004, 10003, 2500.00, 'WITHDRAWAL', '2024-09-23 15:00:00+00');
/* Explicit synthetic account currencies; historic registration times and fees remain unknown. */
UPDATE accounts SET currency='USD' WHERE accountid BETWEEN 101 AND 105;
INSERT INTO roles(rolename) SELECT 'USER' WHERE NOT EXISTS(SELECT 1 FROM roles WHERE rolename='USER');
INSERT INTO user_roles(userid,roleid) SELECT u.userid,r.roleid FROM users u CROSS JOIN roles r WHERE u.userid BETWEEN 1 AND 5 AND r.rolename='USER';

/* Current dispute UI uses dispute_cases; old disputed_orders retained for legacy reports. */
INSERT INTO dispute_cases(order_id,account_id,customer_user_id,transaction_type,transaction_reference,
 title,reason,fee_related,status,resolution_reason,idempotency_key,payload_hash,created_at_utc,updated_at_utc,version)
SELECT o.requestid,o.accountid,a.userid,'TRADE',o.requestid::text,
 'Imported demo dispute '||d.disputeid,d.reason,FALSE,
 CASE d.status WHEN 'COMPLETED' THEN 'RESOLVED' WHEN 'UNDER-REVIEW' THEN 'IN_PROGRESS' ELSE 'OPEN' END,
 CASE WHEN d.status='COMPLETED' THEN d.reason ELSE NULL END,
 'seed-dispute-'||d.disputeid,repeat(md5('synthetic-import-'||d.disputeid),2),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
FROM disputed_orders d JOIN trade_request o ON o.requestid=d.requestid JOIN accounts a ON a.accountid=o.accountid
WHERE d.disputeid BETWEEN 4001 AND 4003;

/* Do not invent holdings, opening positions, cash ledgers or quote evidence from */
/* this incomplete historical sample. In particular, the GOOGL SELL has no opening */
/* position in the supplied data. Account cash is a supplied snapshot, not a */
/* reconstruction from these trades. Staff employee rows are legacy references, */
/* not usable staff logins. No fake/expired session tokens are inserted. */
COMMIT;
SELECT 'Demo data loaded; customer password: mission123' AS result;
