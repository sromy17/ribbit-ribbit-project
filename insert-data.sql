-- Insert data into USERS table
INSERT INTO users (userid, username, salt, userHashedSaltedPassword) VALUES
(1, 'john_trader', 'salt123', 'hashed_password_1'),
(2, 'jane_investor', 'salt456', 'hashed_password_2'),
(3, 'bob_daytrader', 'salt789', 'hashed_password_3'),
(4, 'alice_portfolio', 'salt012', 'hashed_password_4'),
(5, 'charlie_hedge', 'salt345', 'hashed_password_5');

-- Insert data into EMPLOYEES table
INSERT INTO employees (employeeid, employeeusername, employeesalt, employeeHashedSaltedPassword, employeeRoles) VALUES
(1, 'admin_user', 'emp_salt1', 'emp_hashed_1', 'ADMIN'),
(2, 'support_staff', 'emp_salt2', 'emp_hashed_2', 'SUPPORT'),
(3, 'compliance_officer', 'emp_salt3', 'emp_hashed_3', 'COMPLIANCE'),
(4, 'dispute_resolver', 'emp_salt4', 'emp_hashed_4', 'DISPUTE_HANDLER');

-- Insert data into ACCOUNTS table
INSERT INTO accounts (accountid, userid, availablefunds, creationdate, account_type) VALUES
(101, 1, 50000.00, '2024-01-15', 'TRADING'),
(102, 2, 75000.00, '2024-02-20', 'INVESTMENT'),
(103, 3, 100000.00, '2024-03-10', 'DAYTRADING'),
(104, 4, 250000.00, '2024-01-05', 'PORTFOLIO'),
(105, 5, 500000.00, '2023-12-01', 'HEDGE');

-- Insert data into INSTRUMENTS table
INSERT INTO instruments (instrumentid, ticker, instrumentname) VALUES
(1001, 'AAPL', 'Apple Inc'),
(1002, 'MSFT', 'Microsoft Corporation'),
(1003, 'GOOGL', 'Alphabet Inc'),
(1004, 'TSLA', 'Tesla Inc'),
(1005, 'AMZN', 'Amazon.com Inc'),
(1006, 'META', 'Meta Platforms Inc'),
(1007, 'NVDA', 'NVIDIA Corporation');

-- Insert data into TRADE_REQUEST table
INSERT INTO trade_request (requestid, accountid, instrumentid, side, quantity, bidprice, date_request, status) VALUES
(2001, 101, 1001, 'BUY', 10, 150.50, '2024-09-20', 'EXECUTED'),
(2002, 102, 1002, 'BUY', 25, 380.00, '2024-09-21', 'EXECUTED'),
(2003, 103, 1003, 'SELL', 5, 140.75, '2024-09-21', 'EXECUTED'),
(2004, 104, 1004, 'BUY', 15, 245.30, '2024-09-22', 'PENDING'),
(2005, 105, 1005, 'SELL', 20, 180.00, '2024-09-22', 'PENDING'),
(2006, 101, 1006, 'BUY', 8, 480.50, '2024-09-23', 'EXECUTED'),
(2007, 102, 1007, 'BUY', 12, 875.25, '2024-09-23', 'CANCELLED');

-- Insert data into TRADES_EXECUTED table
INSERT INTO trades_executed (executeid, requestid, askprice, date_execution, quantity_executed) VALUES
(3001, 2001, 150.75, '2024-09-20', 10),
(3002, 2002, 380.50, '2024-09-21', 25),
(3003, 2003, 140.50, '2024-09-21', 5),
(3004, 2006, 481.00, '2024-09-23', 8);

-- Insert data into DISPUTED_ORDERS table
INSERT INTO disputed_orders (disputeid, requestid, status, employeeid, category, reason) VALUES
(4001, 2001, 'COMPLETED', 3, 'Accident', 'Price discrepancy resolved'),
(4002, 2002, 'UNDER-REVIEW', 2, 'Fraud', 'Suspicious activity detected'),
(4003, 2003, 'DISPUTED', NULL, 'Incorrect transfer', 'User reported wrong quantity');

-- Insert data into CANCELED_ORDER table
INSERT INTO canceled_order (cancel_id, request_id, reason, cancel_date) VALUES
(5001, 2007, 'ooops! Misclick!', '2024-09-23');

-- Insert data into REJECTED_ORDERS table (if exists)
INSERT INTO rejected_orders (reject_id, request_id, reason, reject_date) VALUES
(7001, 2004, 'Insufficient funds', '2024-09-22'),
(7002, 2005, 'Market closed', '2024-09-22');

-- Insert data into SUPPORT_TICKET table
INSERT INTO support_ticket (ticket_id, accountid, reason, status, assigned_to, created_at, closed_at) VALUES
(6001, 101, 'Account verification issue', 'OPEN', NULL, '2024-09-20 10:30:00', NULL),
(6002, 102, 'Withdrawal delayed', 'IN_PROGRESS', 2, '2024-09-21 14:15:00', NULL),
(6003, 103, 'Trade settlement discrepancy', 'RESOLVED', 3, '2024-09-21 09:45:00', '2024-09-22 16:20:00');
