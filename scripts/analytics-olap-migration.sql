-- Apply to OLAP before deploying the updated ETL. Retains the previous dataset.
BEGIN;
ALTER TABLE accounts_info ADD COLUMN IF NOT EXISTS currency CHAR(3);
ALTER TABLE instruments_info
    ADD COLUMN IF NOT EXISTS dataset_run_id VARCHAR(36),
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
ALTER TABLE fill_facts
    ADD COLUMN IF NOT EXISTS client_id INT,
    ADD COLUMN IF NOT EXISTS execution_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS fee_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(40),
    ADD COLUMN IF NOT EXISTS contract_multiplier NUMERIC,
    ADD COLUMN IF NOT EXISTS product_convention VARCHAR(80),
    ADD COLUMN IF NOT EXISTS pricing_event_id BIGINT;
ALTER TABLE lifecycle_event_facts
    ADD COLUMN IF NOT EXISTS quote_received_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quote_currency CHAR(3),
    ADD COLUMN IF NOT EXISTS quote_price_type VARCHAR(40);
ALTER TABLE cash_movement_facts ALTER COLUMN currency DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS currency_source VARCHAR(80);
CREATE TABLE IF NOT EXISTS clients_info (
    client_id INT PRIMARY KEY, registered_at_utc TIMESTAMPTZ, dataset_run_id VARCHAR(36) NOT NULL
);
CREATE TABLE IF NOT EXISTS client_segment_history (
    membership_id BIGINT PRIMARY KEY, client_id INT NOT NULL,
    segment_code VARCHAR(60) NOT NULL, source VARCHAR(200) NOT NULL,
    effective_from_utc TIMESTAMPTZ NOT NULL, effective_to_utc TIMESTAMPTZ,
    recorded_at_utc TIMESTAMPTZ NOT NULL, dataset_run_id VARCHAR(36) NOT NULL,
    UNIQUE (client_id, effective_from_utc),
    CHECK (effective_to_utc IS NULL OR effective_to_utc > effective_from_utc)
);
COMMIT;
