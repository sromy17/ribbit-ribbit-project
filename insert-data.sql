-- Seed users before dependent accounts and token records.
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
    (1, 'admin_user', 'bcrypt', '$2a$10$EAco3KtOvxLGwcz0G0aeauOx7KiBu1tKkSxOtyteuR8DeVrAhyJn6', 'ADMIN'),
    (2, 'support_staff', 'emp_salt2', 'emp_hashed_2', 'SUPPORT'),
    (3, 'compliance_officer', 'emp_salt3', 'emp_hashed_3', 'COMPLIANCE'),
    (4, 'dispute_resolver', 'emp_salt4', 'emp_hashed_4', 'DISPUTE_HANDLER'),
    (5, 'analyst_user', 'bcrypt', '$2a$10$5aBDMtqcyF2rqep.RtCnI.vProRwMEidkf//iG.Qb8l0Vo/AOIT.a', 'ANALYST');

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

INSERT INTO instruments (instrumentid, ticker, instrumentname) VALUES
    (1001, 'AAPL', 'Apple Inc'),
    (1002, 'MSFT', 'Microsoft Corporation'),
    (1003, 'GOOGL', 'Alphabet Inc'),
    (1004, 'TSLA', 'Tesla Inc'),
    (1005, 'AMZN', 'Amazon.com Inc'),
    (1006, 'META', 'Meta Platforms Inc'),
    (1007, 'NVDA', 'NVIDIA Corporation');

INSERT INTO trade_request (
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

INSERT INTO trades_executed (
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

INSERT INTO refresh_tokens (
    refreshTokenId,
    userid,
    refreshTokenHash,
    dateIssued,
    dateExpires,
    isRevoked
) VALUES
    (8001, 1, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '2024-09-20 09:00:00+00', '2025-09-20 09:00:00+00', FALSE),
    (8002, 2, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', '2024-09-21 09:00:00+00', '2025-09-21 09:00:00+00', FALSE),
    (8003, 3, 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', '2024-09-22 09:00:00+00', '2025-09-22 09:00:00+00', FALSE);

INSERT INTO access_tokens (
    accessTokenId,
    refreshTokenId,
    accessTokenHash,
    dateIssued,
    dateExpires,
    isRevoked
) VALUES
    (9001, 8001, '1111111111111111111111111111111111111111111111111111111111111111', '2024-09-23 09:00:00+00', '2024-09-23 10:00:00+00', FALSE),
    (9002, 8002, '2222222222222222222222222222222222222222222222222222222222222222', '2024-09-23 09:00:00+00', '2024-09-23 10:00:00+00', FALSE),
    (9003, 8003, '3333333333333333333333333333333333333333333333333333333333333333', '2024-09-23 09:00:00+00', '2024-09-23 10:00:00+00', FALSE);

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