-- OLTP: durable, transactionally locked counters. Never reset these on ETL refresh.
-- Runtime initialization also creates these for existing and H2 development installs.
BEGIN;
CREATE TABLE IF NOT EXISTS financial_id_allocations (
    kind VARCHAR(20) PRIMARY KEY,
    last_id BIGINT NOT NULL
);
INSERT INTO financial_id_allocations(kind,last_id)
VALUES ('ORDER',0),('TRADE',0),('REJECTION',0) ON CONFLICT(kind) DO NOTHING;
-- Allocation floors each counter at the actual committed maximum under a row lock.
-- Backups/restores must retain both source records and this table.
COMMIT;
