-- Additive upgrade for the existing local application. Preserve unknown historical data.
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_name='holdings' AND column_name='averagecost')
 AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_name='holdings' AND column_name='average_cost') THEN
  ALTER TABLE holdings RENAME COLUMN averagecost TO average_cost;
 END IF;
END $$;
ALTER TABLE users ADD COLUMN IF NOT EXISTS registered_at_utc TIMESTAMPTZ;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS currency CHAR(3);
ALTER TABLE trade_request ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ;
ALTER TABLE trade_request ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(36);
UPDATE trade_request SET correlation_id='legacy-'||requestid WHERE correlation_id IS NULL;
ALTER TABLE trade_request ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE trades_executed ADD COLUMN IF NOT EXISTS fee NUMERIC(30,10);
-- Historical fees were not recorded by the old schema; NULL means unknown, never zero.
ALTER TABLE trades_executed ALTER COLUMN fee DROP NOT NULL;
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS asset_class VARCHAR(40);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS market VARCHAR(80);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS quantity_unit VARCHAR(40);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS quote_currency CHAR(3);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS settlement_currency CHAR(3);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS contract_multiplier NUMERIC(30,10);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS product_convention VARCHAR(80);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS metadata_source VARCHAR(160);
ALTER TABLE instruments ALTER COLUMN instrumentname TYPE VARCHAR(255);
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
