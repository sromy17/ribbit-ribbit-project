# Analytics delivery and verification

## Run and access

Apply the existing OLTP/OLAP evidence migrations, then run
`scripts/analytics-reporting-migration.sql` on **OLAP only**. It creates the retained
report store and query indexes. It is safe to replay. Never run `olap.sql` against
an existing database: that file is a destructive test/development initializer.
Run an ETL refresh before querying analytics. Start the normal backend and frontend
using the README instructions, sign in with a server-provisioned **ANALYST** user,
and open `/analyst-dashboard`. PLATFORM_ADMINISTRATOR can also use analytics APIs.
The frontend proxy preserves `/api/analytics` rather than stripping `/api`.

Every analytics endpoint requires the server-derived `PERM_REPORTING` permission.
No username, account ID, client ID or order ID is exposed in analytic results.
Customer and Operations roles alone cannot access these aggregate/report routes.
David continues to use bounded operational investigation routes. No analytics
handler has an OLTP datasource or a live-query fallback.

## Query contract

`GET /api/analytics/{overview|activity|instruments|client-segments}` accepts:

- Required inclusive calendar dates `from`, `to`; at most 366 days.
- `timezone` (IANA zone, default UTC), converted to half-open UTC instants, including DST.
- Optional positive `instrumentId`, `side` (BUY/SELL), exact `segment` code.
- `page` (0–10000), `size` (1–100, default 25), `sort`
  (fills/instrument/period/segment), `direction` (asc/desc).

Grouping is fixed by route. Sorting uses static SQL columns; filters are bound
parameters. Activity groups by local calendar date. Instrument quantities group
by stable instrument ID and recorded quantity unit. Ranking is by fill count,
never by a sum of incompatible units. Unknown segment history is `UNKNOWN`.
Distinct client/order counts are recomputed per scope; grouped counts must not
be added to obtain a period distinct count. Fees, turnover and cash are distinct
native-currency measures. Missing currency/multiplier/convention never implies USD
or a multiplier of one. No consolidated FX valuation is implemented.

Responses include dataset ID, metric version, selected filters, local and UTC
boundaries, source cutoff, publication time, coverage, completeness and warnings.
The cutoff identifies the committed extraction snapshot, not an upstream delivery
SLA. Future-dated evidence is excluded at the cutoff. Missing-time coverage is
for the otherwise selected dataset and cannot be assigned to the selected month.
A current/partial month is incomplete when its end exceeds the cutoff.

`GET /api/analytics/data-status` returns availability, latest refresh status and
last-successful publication metadata. A failed refresh retains last-good data.
A missing dataset or unavailable reporting store returns 503 for queries, with no
trading-store fallback. SQL statements have 5-second limits, locks 3 seconds,
and read transactions 15 seconds. The stale threshold is provisionally 900 seconds.
These are configured implementation limits, **not approved production targets**.

## Issued reports and correction policy

`POST /api/analytics/reports` accepts `from`, `to`, `timezone`, the same optional
filters, and `idempotencyKey`. A report must cover exactly one calendar month.
It freezes overview, complete activity/instrument/segment tables, coverage,
warnings, dataset/cutoff, filters and metric version in OLAP JSONB. Issuance is
bounded to 1000 groups per table and a 30-second transaction; oversized selections
fail rather than silently truncating. Saved report retrieval does not require the
source dataset to remain current. SQL triggers reject UPDATE/DELETE on reports;
ETL never refreshes this store. There is no report export endpoint in this scope.

`GET /api/analytics/reports/{reportId}` returns the saved payload with its version
and issuance timestamp under the same reporting permission. Report IDs can be
shared among authorized reporting staff; they are not public URLs. Issuer identity
is retained internally for attribution.

Proposed late-arrival policy: preserve the original report and issue an explicit
amendment using `amendsReportId` and a nonblank `amendmentReason`. Amendments retain
the original month/timezone/filters and recalculate against the latest published
dataset. Each version can have one immediate successor; further corrections amend
that successor. The prior version is never overwritten. A reused request key with
different parameters conflicts; ordinary retry returns the original saved version.
Concurrent conflicting issuance returns 409 and can be retried. Policy approval,
metric sign-off, segment source/values and legal retention remain stakeholder work.
The user authorized implementation of the proposed calculations/screens; the UI
continues to label their definitions PROPOSED.

## Reader/publication consistency

Analytics reads and issuance use OLAP repeatable-read transactions. ETL replaces
rows with transactional DELETE/INSERT rather than TRUNCATE, preserving MVCC
snapshots for readers. ETL writers retain their advisory exclusion lock. The UI
rejects responses with differing dataset IDs across its parallel queries, requiring
a refresh rather than combining versions. Saved reports use one transaction and
one dataset, regardless of later ETL refreshes.

Order submission now commits accepted/rejected intent separately from execution.
This fixes the prior self-invocation transaction boundary, where independently
committed execution-attempt evidence could wait on uncommitted lifecycle rows.
Execution failure leaves accepted intent available for investigation/retry.

## Verification and practical limits

Run `mvn test`, `cd frontend && npm test -- --watch=false`, and
`bash scripts/verify-admin-postgres.sh`. The latter owns disposable PostgreSQL 17
containers; it does not use the application's database. It covers migrations,
known fixtures, currencies/units, partial fills, cross-account clients, UTC versus
New York month boundaries, cohort rejections arriving in a later month,
unknown coverage, protected HTTP APIs, immutable reports, amendments, failed
publication, mixed trading/reporting/ingestion, operational lifecycle inspection,
reporting failure isolation, restart and financial replay.

`target/analytics-verification/mixed-load.txt` records actual order end-to-end p50
and p95, mixed-workload throughput, sampled OLTP lock waits, source snapshot age and newest-fill-to-final-publication lag.
Lock activity is sampled every 10 ms; a zero sample count does not measure cumulative wait duration. The fixture has 10000 historical orders and 20 sequential HTTP orders while 20
analytics reads and three refreshes run concurrently. Quotes are deterministic,
local fixtures; broker/network latency and Kafka delivery are not benchmarked.
The existing restart suite separately verifies persisted fills, holdings, disputes
and replay protection. This local run is a regression workload, not evidence of
production capacity. Production sign-off requires representative client/instrument
cardinality, concurrent submitters, target hardware, realistic broker/quote/Kafka
behaviour and agreed latency/throughput/isolation limits. The trading model still
uses integer quantities; this change does not add fractional execution support.

See [recorded local verification](analytics-local-verification.md) for the measured run.
