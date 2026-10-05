DROP TABLE IF EXISTS users cascade;
DROP TABLE IF EXISTS accounts cascade;
DROP TABLE IF EXISTS instruments cascade;
DROP TABLE IF EXISTS employees cascade;
DROP TABLE IF EXISTS trade_request cascade;
DROP TABLE IF EXISTS trades_executed cascade;
DROP TABLE IF EXISTS disputed_orders cascade;
DROP TABLE IF EXISTS canceled_order cascade;
DROP TABLE IF EXISTS support_ticket cascade;


CREATE TABLE users
(
userid SERIAL PRIMARY KEY,
username varchar(50) UNIQUE NOT NULL,
salt varchar(25) NOT NULL,
userHashedSaltedPassword varchar(64) NOT NULL
);

CREATE TABLE accounts
(
accountid SERIAL PRIMARY KEY,
userid INT REFERENCES users(userid) NOT NULL,
availablefunds NUMERIC NOT NULL DEFAULT 0,
CHECK (availablefunds >= 0),
creationdate DATE NOT NULL,
account_type VARCHAR(20) NOT NULL,
FOREIGN KEY (userid) REFERENCES users(userid)
);

CREATE TABLE instruments
(
instrumentid SERIAL PRIMARY KEY,
ticker VARCHAR(25) NOT NULL UNIQUE,
instrumentname VARCHAR(25) NOT NULL
);

CREATE TABLE trade_request
(
requestid SERIAL PRIMARY KEY,
accountid INT NOT NULL REFERENCES accounts(accountid),
instrumentid INT NOT NULL REFERENCES instruments(instrumentid),
side CHAR(5) NOT NULL CHECK (side IN ('BUY', 'SELL')),
quantity INT NOT NULL CHECK (quantity > 0),
bidprice NUMERIC NOT NULL CHECK (bidprice >= 0),
date_request DATE NOT NULL,
status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED'))
);

CREATE TABLE trades_executed
(
executeid SERIAL PRIMARY KEY,
requestid INT NOT NULL REFERENCES trade_request(requestid),
askprice NUMERIC NOT NULL CHECK (askprice >= 0),
date_execution DATE NOT NULL,
quantity_executed INT NOT NULL CHECK (quantity_executed > 0)
);

CREATE TABLE employees
(
employeeid SERIAL PRIMARY KEY,
employeeusername VARCHAR(50) UNIQUE NOT NULL,
employeesalt VARCHAR(25) NOT NULL, 
employeeHashedSaltedPassword VARCHAR(64) NOT NULL,
employeeRoles VARCHAR(20)
);

CREATE TABLE disputed_orders
(
disputeid SERIAL PRIMARY KEY NOT NULL,
requestid INT REFERENCES trade_request(requestid) NOT NULL,
status VARCHAR(20) DEFAULT 'DISPUTED' CHECK (status IN ('DISPUTED', 'UNDER-REVIEW', 'COMPLETED')),
employeeid int DEFAULT NULL REFERENCES employees(employeeid),
category VARCHAR(20) CHECK (category IN ('Fraud', 'Accident', 'Incorrect transfer')),
reason VARCHAR(75)
);
CREATE TABLE canceled_order
(
cancel_id SERIAL PRIMARY KEY NOT NULL,
request_id INT REFERENCES trade_request(requestid) NOT NULL,
reason VARCHAR(20) CHECK (reason IN ('user_cancel', 'insufficent_funds')),
cancel_date DATE NOT NULL
);

CREATE TABLE support_ticket
(
ticket_id SERIAL PRIMARY KEY NOT NULL,
accountid INT NOT NULL REFERENCES accounts(accountid),
reason VARCHAR(75) NOT NULL,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
status VARCHAR(20) DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
assigned_to INT REFERENCES employees(employeeid),
closed_at TIMESTAMP DEFAULT NULL
);


