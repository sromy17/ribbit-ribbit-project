-- SYNTHETIC DEMO ONLY. The --demo launcher applies this only to its fresh disposable database.
BEGIN;
UPDATE accounts SET currency='GBP' WHERE accountid=103;
UPDATE accounts SET currency='USD' WHERE accountid IN (104,105);
INSERT INTO accounts(accountid,userid,availablefunds,creationdate,account_type,currency)
VALUES(106,3,899.5,'2026-08-01T09:00:00Z','TRADING','GBP');
INSERT INTO instruments(instrumentid,ticker,instrumentname,asset_class,market,venue,quote_currency,settlement_currency,quantity_unit,price_precision,quantity_precision,contract_multiplier,product_convention,metadata_source)
VALUES
(8001,'DEMO-A','Demo Alpha','EQUITY','UK','DEMO','GBP','GBP','SHARE',2,0,1,'PRICE_PER_UNIT','SYNTHETIC_DEMO'),
(8002,'DEMO-EURUSD','Demo EUR USD','FX','GLOBAL','DEMO','USD','USD','EUR',4,0,1,'PRICE_PER_UNIT','SYNTHETIC_DEMO'),
(8003,'DEMO-BTC','Demo Bitcoin','CRYPTO','GLOBAL','DEMO','USD','USD','BTC',2,0,1,'PRICE_PER_UNIT','SYNTHETIC_DEMO'),
(8004,'DEMO-B','Demo Beta','EQUITY','UK','DEMO','GBP','GBP','SHARE',2,0,1,'PRICE_PER_UNIT','SYNTHETIC_DEMO');
INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,created_at_utc,correlation_id,status)
VALUES
(800101,103,8001,'BUY',10,102,'2026-09-10','2026-09-10T10:00:00Z','demo-800101','EXECUTED'),
(800102,106,8004,'BUY',2,50,'2026-09-11','2026-09-11T10:00:00Z','demo-800102','EXECUTED'),
(800103,104,8002,'BUY',1000,1.10,'2026-09-12','2026-09-12T10:00:00Z','demo-800103','EXECUTED'),
(800104,105,8003,'BUY',1,60000,'2026-09-14','2026-09-14T10:00:00Z','demo-800104','EXECUTED'),
(800105,103,8001,'BUY',100000,100,'2026-09-15','2026-09-15T10:00:00Z','demo-800105','REJECTED'),
(800106,103,8001,'BUY',1,100,'2026-09-16','2026-09-16T10:00:00Z','demo-800106','PENDING'),
(800107,103,8001,'BUY',4,202,'2026-08-31','2026-08-31T23:58:00Z','demo-800107','EXECUTED');
INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,event_sequence,occurred_at_utc,correlation_id)
SELECT requestid,accountid,instrumentid,'ORDER_SUBMITTED',1,created_at_utc,correlation_id FROM trade_request WHERE requestid BETWEEN 800101 AND 800107;
INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,event_sequence,occurred_at_utc,correlation_id,reason)
SELECT requestid,accountid,instrumentid,CASE WHEN requestid=800105 THEN 'VALIDATION_REJECTED' ELSE 'ORDER_ACCEPTED' END,2,created_at_utc+INTERVAL '1 second',correlation_id,
CASE WHEN requestid=800105 THEN 'Insufficient funds (synthetic demo)' ELSE NULL END
FROM trade_request WHERE requestid BETWEEN 800101 AND 800107;
INSERT INTO rejected_orders(reject_id,request_id,reason,reject_date) VALUES(800105,800105,'Insufficient funds (synthetic demo)','2026-09-15T10:00:01Z');
INSERT INTO trades_executed(executeid,requestid,askprice,date_execution,executed_at_utc,client_id,execution_currency,fee_currency,quantity_unit,contract_multiplier,product_convention,fee,quantity_executed)
VALUES
(810101,800101,100,'2026-09-10','2026-09-10T10:00:02Z',3,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT',1,4),
(810102,800101,102,'2026-09-10','2026-09-10T10:00:03Z',3,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT',1,6),
(810103,800102,50,'2026-09-11','2026-09-11T10:00:02Z',3,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT',0.5,2),
(810104,800103,1.10,'2026-09-12','2026-09-12T10:00:02Z',4,'USD','USD','EUR',1,'PRICE_PER_UNIT',1,1000),
(810105,800104,60000,'2026-09-14','2026-09-14T10:00:02Z',5,'USD','USD','BTC',1,'PRICE_PER_UNIT',5,1),
(810106,800107,200,'2026-08-31','2026-08-31T23:59:00Z',3,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT',0.4,2),
(810107,800107,202,'2026-09-01','2026-09-01T00:01:00Z',3,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT',0.4,2);
INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,event_sequence,occurred_at_utc,correlation_id,quote_source,quote_at_utc,quote_price,quote_received_at_utc,quote_currency,quote_price_type,decision_price,quantity,fee)
SELECT t.requestid,o.accountid,o.instrumentid,'FILL_COMMITTED',2+row_number() OVER(PARTITION BY t.requestid ORDER BY t.executeid),t.executed_at_utc,o.correlation_id,'SYNTHETIC_DEMO',t.executed_at_utc-INTERVAL '1 second',t.askprice,t.executed_at_utc-INTERVAL '1 second',t.execution_currency,'ASK',t.askprice,t.quantity_executed,t.fee
FROM trades_executed t JOIN trade_request o USING(requestid) WHERE t.executeid BETWEEN 810101 AND 810107;
INSERT INTO cash_movements(order_id,account_id,amount,currency,currency_source,movement_type,occurred_at_utc,correlation_id)
SELECT t.requestid,o.accountid,-(t.askprice*t.quantity_executed+t.fee),t.execution_currency,'SYNTHETIC_DEMO','TRADE_SETTLEMENT',t.executed_at_utc,o.correlation_id FROM trades_executed t JOIN trade_request o USING(requestid) WHERE t.executeid BETWEEN 810101 AND 810107;
INSERT INTO holding_movements(order_id,account_id,instrument_id,quantity_delta,quantity_after,occurred_at_utc,correlation_id)
SELECT t.requestid,o.accountid,o.instrumentid,t.quantity_executed,sum(t.quantity_executed) OVER(PARTITION BY o.accountid,o.instrumentid ORDER BY t.executed_at_utc,t.executeid),t.executed_at_utc,o.correlation_id FROM trades_executed t JOIN trade_request o USING(requestid) WHERE t.executeid BETWEEN 810101 AND 810107;
INSERT INTO holdings(accountid,instrumentid,quantity,average_cost,as_of_date) VALUES
(103,8001,14,1816.0/14,'2026-09-16'),(106,8004,2,50,'2026-09-16'),(104,8002,1000,1.10,'2026-09-16'),(105,8003,1,60000,'2026-09-16');
UPDATE accounts SET availablefunds=availablefunds-1818.8 WHERE accountid=103;
UPDATE accounts SET availablefunds=availablefunds-1101 WHERE accountid=104;
UPDATE accounts SET availablefunds=availablefunds-60005 WHERE accountid=105;
INSERT INTO client_segment_history(client_id,segment_code,source,effective_from_utc,recorded_at_utc)
VALUES(3,'DEMO_RETAIL','SYNTHETIC_DEMO','2026-08-01T00:00:00Z','2026-08-01T00:00:00Z'),(4,'DEMO_PRO','SYNTHETIC_DEMO','2026-08-01T00:00:00Z','2026-08-01T00:00:00Z'),(5,'DEMO_PRO','SYNTHETIC_DEMO','2026-08-01T00:00:00Z','2026-08-01T00:00:00Z');
INSERT INTO dispute_cases(order_id,account_id,customer_user_id,transaction_type,transaction_reference,title,reason,fee_related,status,idempotency_key,payload_hash,created_at_utc,updated_at_utc)
VALUES(800101,103,3,'TRADE','800101','Demo: explain the execution fee','I see two fills and want to understand the total fee. Synthetic demonstration case.',TRUE,'OPEN','demo-fee-question',repeat('0',64),'2026-09-17T09:00:00Z','2026-09-17T09:00:00Z');
INSERT INTO dispute_case_events(case_id,event_type,actor_user_id,note,occurred_at_utc,case_version)
SELECT case_id,'SUBMITTED',3,'Synthetic customer fee question','2026-09-17T09:00:00Z',0 FROM dispute_cases WHERE idempotency_key='demo-fee-question';
COMMIT;
