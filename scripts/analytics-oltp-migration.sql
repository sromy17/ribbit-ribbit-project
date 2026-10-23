-- Apply to OLTP before deploying the analytics evidence changes. No historical backfill.
BEGIN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS registered_at_utc TIMESTAMPTZ;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS currency CHAR(3);
ALTER TABLE instruments
    ADD COLUMN IF NOT EXISTS asset_class VARCHAR(30),
    ADD COLUMN IF NOT EXISTS market VARCHAR(40),
    ADD COLUMN IF NOT EXISTS venue VARCHAR(80),
    ADD COLUMN IF NOT EXISTS quote_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS settlement_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(40),
    ADD COLUMN IF NOT EXISTS price_precision INT,
    ADD COLUMN IF NOT EXISTS quantity_precision INT,
    ADD COLUMN IF NOT EXISTS contract_multiplier NUMERIC,
    ADD COLUMN IF NOT EXISTS product_convention VARCHAR(80),
    ADD COLUMN IF NOT EXISTS metadata_source VARCHAR(200),
    ADD COLUMN IF NOT EXISTS market_data_symbol VARCHAR(80);

-- Remove only single-column ticker uniqueness, including Hibernate-generated names.
DO $$
DECLARE item RECORD;
BEGIN
    FOR item IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.conrelid = 'instruments'::regclass AND c.contype = 'u'
          AND cardinality(c.conkey) = 1 AND a.attname = 'ticker'
    LOOP
        EXECUTE format('ALTER TABLE instruments DROP CONSTRAINT %I', item.conname);
    END LOOP;
END $$;

ALTER TABLE trade_request ALTER COLUMN created_at_utc DROP NOT NULL,
    ALTER COLUMN created_at_utc DROP DEFAULT;
ALTER TABLE trades_executed
    ALTER COLUMN executed_at_utc DROP NOT NULL,
    ALTER COLUMN executed_at_utc DROP DEFAULT,
    ADD COLUMN IF NOT EXISTS client_id INT,
    ADD COLUMN IF NOT EXISTS execution_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS fee_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(40),
    ADD COLUMN IF NOT EXISTS contract_multiplier NUMERIC,
    ADD COLUMN IF NOT EXISTS product_convention VARCHAR(80),
    ADD COLUMN IF NOT EXISTS pricing_event_id BIGINT;
ALTER TABLE order_lifecycle_events
    ADD COLUMN IF NOT EXISTS quote_received_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quote_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS quote_price_type VARCHAR(40);
ALTER TABLE cash_movements ALTER COLUMN currency DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS currency_source VARCHAR(80);

CREATE TABLE IF NOT EXISTS client_segment_history (
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
COMMIT;
