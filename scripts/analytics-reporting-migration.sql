-- Reporting database only. Safe to replay; ETL must never truncate this store.
BEGIN;
CREATE TABLE IF NOT EXISTS analytics_reports (
    report_id VARCHAR(36) PRIMARY KEY,
    issued_by VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_signature TEXT NOT NULL,
    version INT NOT NULL CHECK(version > 0),
    amends_report_id VARCHAR(36) UNIQUE REFERENCES analytics_reports(report_id),
    amendment_reason VARCHAR(500),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    snapshot JSONB NOT NULL,
    UNIQUE(issued_by,idempotency_key),
    CHECK ((amends_report_id IS NULL AND version=1) OR
           (amends_report_id IS NOT NULL AND version>1 AND length(trim(amendment_reason))>0))
);
CREATE OR REPLACE FUNCTION prevent_analytics_report_mutation() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''Issued analytics reports are immutable; create an amendment''; END';
DROP TRIGGER IF EXISTS analytics_reports_immutable ON analytics_reports;
CREATE TRIGGER analytics_reports_immutable BEFORE UPDATE OR DELETE ON analytics_reports
    FOR EACH ROW EXECUTE FUNCTION prevent_analytics_report_mutation();
CREATE INDEX IF NOT EXISTS idx_fill_period ON fill_facts(executed_at_utc,instrumentid);
CREATE INDEX IF NOT EXISTS idx_lifecycle_period ON lifecycle_event_facts(event_type,occurred_at_utc,requestid);
CREATE INDEX IF NOT EXISTS idx_lifecycle_order ON lifecycle_event_facts(requestid,event_type);
CREATE INDEX IF NOT EXISTS idx_cash_period ON cash_movement_facts(occurred_at_utc,requestid);
CREATE INDEX IF NOT EXISTS idx_segment_effective ON client_segment_history(client_id,effective_from_utc,effective_to_utc);
COMMIT;
