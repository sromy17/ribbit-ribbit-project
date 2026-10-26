-- Preserve the declared UTC meaning of existing *_at_utc evidence columns.
-- PostgreSQL only; review/run through the normal migration process before ETL.
-- TIMESTAMPTZ sources are left unchanged. No missing times are synthesized.
DO $$
DECLARE evidence RECORD;
BEGIN
  FOR evidence IN
    SELECT table_name,column_name FROM information_schema.columns
    WHERE table_schema=current_schema() AND data_type='timestamp without time zone'
      AND ((table_name IN ('order_lifecycle_events','cash_movements','holding_movements','dispute_case_events') AND column_name='occurred_at_utc')
        OR (table_name='dispute_cases' AND column_name IN ('created_at_utc','updated_at_utc')))
  LOOP
    EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE TIMESTAMPTZ USING %I AT TIME ZONE ''UTC''',evidence.table_name,evidence.column_name,evidence.column_name);
  END LOOP;
END $$;
