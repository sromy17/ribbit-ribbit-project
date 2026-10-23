# Analytics platform: requirements, personas, and delivery plan

Updated: 2026-10-23. Status: proposed design and implementation backlog, not a claim
of implemented functionality or stakeholder approval.

Primary source: `../LEAP-Project-Business-Requirements-Specification.pdf`,
LEAP-BRS-2026-014, version 0.9, draft issued 20 August 2026. All eight pages were
read; persona/requirement/service-expectation pages 5–7 were also rendered and
visually reviewed. Repository observations describe a targeted read of current
source, not a complete compliance audit or a new test run.

## 1. Scope and evidence labels

- **BRS**: explicit outcome/priority in the PDF.
- **Persona-derived**: proposed acceptance behaviour needed to serve a named
  persona; it is not a new numbered BRS requirement.
- **Recommended**: an implementation choice or enhancement requiring review.
- **Previously confirmed**: user decisions in this chat; these remain applicable.

The BRS requires internal historical analysis separated from live trading (BR-16,
Must) and expects a small set of business insights (BR-17, Should). It also
requires a permanent attributable audit trail and complete trade reconstruction
(BR-14/15, Must). A heatmap is one optional presentation, not the definition of
analytics and not a substitute for these outcomes.

Use distinct operations and commercial-analysis experiences. They may share the
same Angular application, Spring application, and design system. The BRS does not
mandate microservices, a specific database, a BI vendor, CDC, Kafka, or a chart
library. Select and justify architecture under sections 10–12.

Keep advised dealing, real banking/payment integration, native mobile apps, and
production identity verification outside this phase (§4.2). Desktop-browser
usability is the initial target. Analytics by itself fulfils required reporting;
it is not automatically the extra capability required by BR-18.

## 2. Requirements traceability

| Source | Priority | Analytics/operations implication | Acceptance evidence |
| --- | --- | --- | --- |
| BR-16, §3, §4.1 | Must | Analyse activity by instrument, period, client segment; isolate queries from live trading | All three dimensions work on historical fixtures; representative reporting/ingestion load meets an agreed trading-impact limit |
| BR-17 | Should | Surface volumes, most active instruments, client activity trends | Small dashboard with named metrics, definitions, periods and actual data |
| BR-14 | Must | Preserve accepted intent, pricing decisions, cash/holding effects, client attribution and time | Evidence survives restart and failed deployment; reporting refresh cannot erase it |
| BR-15 | Must | Reconstruct a trade without engineering logs | David explains an order from submission through recorded outcome using the operations workspace |
| BR-06/08/09 | Must | Distinguish intent, acceptance, execution pricing, fills and atomic effects | Accepted-but-unfilled orders are represented correctly; fills reconcile with cash/holdings; actual execution quote evidence exists |
| BR-10/11, §9.1/9.2 | Must for BR-10/11; platform expectations for §9 | Protect Joanna's current holdings/cash/history and responsive trading | Reporting outage/load does not turn delayed OLAP values into her current position or block order processing |
| BR-12 | Must for execution pricing | Reporting metadata must represent supported instruments rather than assume US equities only | UK/US/Indian equity, FX and crypto fixtures retain correct instrument IDs, markets, units and currencies |
| BR-02, §9.3 | Must for BR-02; platform expectation for §9.3 | Client ownership and least-privilege internal access | Clients cannot call reporting/investigation APIs; analysts cannot retrieve unnecessary identifiable client/financial details |
| §9.4 | Platform expectation | Retained, unalterable evidence available for inspection | Controlled append-only access, archive/recovery controls, approved retention/hold policy |
| §6 Priya, §11 | Persona and success criteria | Reliable monthly reporting independent of trading | Priya can generate and explain a historical monthly report without engineering assistance |
| BR-18 | Should | Propose a justified additional capability | Dispute case workflow can be the proposed extension; Sponsor acceptance remains separate |

There are no numeric reporting latency, refresh, concurrency, export-size or legal
retention periods specified in the PDF. Existing proposals (five-minute refresh,
15-minute stale threshold, two/five-second p95 targets) are not BRS mandates or
production approvals.

## 3. Personas and their respective workspaces

### David: head of trading operations

His central question is: what exactly happened to this particular order, when,
and what financial effects were recorded? He does not use the client trading UI.

Proposed operations experience:

1. Search by order/fill/account/user reference, with bounded filters for date,
   instrument and state. Do not force a known historical order through an
   interactive reporting date-range restriction.
2. Open an evidence timeline: submitted intent; validation/acceptance; execution
   attempts; actual quote source/time/value; decision; fills or rejection;
   cash/holding changes; linked dispute references where authorised.
3. Show quantities, fees, rejection/failure reasons, actor/client attribution,
   correlation ID, time precision, and source. An aggregate order row is not a
   substitute for individual fills or lifecycle events.
4. Clearly separate historical effects from current account snapshots. Today's
   cash balance cannot establish the balance immediately before an old trade.
5. Label delayed, missing or inconsistent evidence and escalate it. A nonempty
   event list alone does not prove the full lifecycle is complete.
6. Use narrow operational reads or a complete evidence projection for recent
   investigations; heavy cross-client analysis belongs on reporting infrastructure.

Acceptance: given an executed, rejected, delayed and (if supported) partially
filled order, David explains the recorded lifecycle and financial outcome from
the UI without asking engineering to search logs. Missing proof remains visible.

Previously confirmed: only ADMIN works/claims/resolves/reassigns tickets. David
with OPERATIONS permissions may investigate/read cases; his persona does not
silently grant ADMIN permissions. Ticket mutation requires an explicit ADMIN role.

### Priya: commercial analyst

Her central question is: what trading and client activity occurred this month,
how does it compare with previous periods, and can I trust the numbers I present?

Proposed analytics experience:

1. Monthly overview with month/custom period, reporting timezone, complete vs
   partial-period label, submission/execution counts and unique active clients.
2. Trading activity over time with day/week/month grain, instrument/market/asset
   class/side filters, and comparable prior-period results.
3. Instrument analysis with stable instrument identity, execution count, executed
   quantity in its own unit, and gross notional only where currency/product
   conventions support it. Rank by an explicitly labelled measure.
4. Client activity and segment analysis: distinct clients across accounts,
   participation, repeat activity if defined, and the approved client segment.
   Account type is an additional dimension, not an invented client segment.
5. A monthly report view with selected filters, definitions, dataset version,
   source coverage, publication/as-of times and completeness status. Preserve
   a reproducible version for presentations; corrections create new versions.
6. Data-quality/freshness panel. Stale, unavailable, incomplete and zero activity
   are distinct states. A report should not look complete when data is missing.
7. Recommended convenience: downloadable tables/report pack with approved
   permission, bounded asynchronous jobs and the same definitions as the UI.
   Export/PDF delivery is useful for the persona but not explicitly prescribed.

Acceptance: Priya produces a month/instrument/segment report whose totals reconcile,
reopens the same report version later, and gets the same results. Her queries and
exports meet the agreed isolation/performance criteria while clients trade.

The BRS does not ask Priya to manage tickets, change accounts, place client orders,
or interpret internal buy/sell flow as an investment forecast. A platform trading
heatmap is internal activity, not an exchange-wide market heatmap or price return.

### Joanna: self-directed investor

Keep her sign-in, order placement/status, own holdings/cash and chronological
history simple. Her under-a-minute trading aim is persona guidance, not a reporting
SLA. BR-07 expects status changes without manual refresh (Should); BR-10 requires
current balances/holdings correct after execution. Neither depends on OLAP refresh.

No global client rankings, staff evidence, segment dashboards or ETL controls in
her experience. If analytics is unavailable, order processing and correct current
account reads still work. Do not insert analytics-driven confirmation steps into
her order journey.

### Other stakeholders from §5

- Risk/Compliance: read attributable historical evidence, verify immutability and
  retention, investigate anomalies; full audit access is separately permissioned.
- Finance: reconcile gross executed notional, fees and actual cash movements by
  currency. Trading turnover is not profit, and gross flows are not net cash.
- Sponsor: review evidence that §8/§9 outcomes are met, understand architecture in
  business terms, and approve scope/BR-18 at checkpoints.
- Trading Desk: approve acceptance/execution rules and the operational meaning of
  status/rejection categories. A rules-management UI is not required by this BRS.

## 4. What exists and what remains

| Current source | Observed foundation | Analytics gaps/risks |
| --- | --- | --- |
| AnalystDashboardComponent and ApiService | Heatmap and daily top-symbol/trend UI | Calls /api/analyst/trends/symbols and /api/analyst/trends/market; no backend handlers for those routes were found in inspected API source. No monthly/segment/freshness workflow |
| AdminReadService/AdminReadController | Basic order-state and instrument-fill aggregates plus reporting status | No complete commercial metric contract, historical segment analysis, monthly report versioning, or period comparison |
| olap.sql | Separate order/fill/event/cash/holding facts; refresh run IDs | Missing approved client dimension/segment history and broad instrument/currency metadata; no retained published monthly datasets |
| OltpToOlapEtlService | Repeatable-read extraction, transactional publication, advisory lock and failure status | TRUNCATE publication blocks OLAP readers; current full-table extraction can burden OLTP; watermark captured before extraction is not a completeness proof; replaced datasets cannot reproduce old reports by run ID alone |
| AdminInvestigationController | Operational timeline, fills and cash/holding effects; paginated current account reads | Complete evidence still depends on correctness of original source recording and coverage checks |
| OrderEvidenceService | Lifecycle/financial evidence recording now preserves supplied quote source/time/currency, receipt time and price type; cash currency comes from account metadata or remains unknown | Legacy generic quote sources and hardcoded USD labels remain unverified; missing upstream fields are not fabricated |
| Instrument/User domain and normalized OLAP | Stable IDs; instrument convention metadata, nullable registration time, client dimension and effective-dated segment storage; fills snapshot monetary/unit metadata | Requires curated metadata and approved segment source/values. New registration paths record time; legacy registration stays unknown. Segment reporting remains gated |
| SecurityConfig | OPERATIONS, COMPLIANCE, ANALYST and admin capabilities | Analyst identifiers must be properly protected; a predictable hash of numeric user IDs is not anonymisation |
| application.properties | Configurable ETL cron | Disabled by default; deployment must actually configure the agreed schedule |

The sample-data design preview is not evidence that real analytics APIs exist or
that BR-16 is satisfied. Existing local 10,001-order/two-fill measurements do not
establish representative analytics load or protection of the live trading path.

## 5. Recommended metric contract (approval required)

Keep prior confirmed semantics: submission can progress to acceptance or rejection;
acceptance is not a fill. Use recorded events, not guesses from current status.
The authoritative review-ready dictionary is
[reporting-metric-contract.md](reporting-metric-contract.md); it specifies grain,
event timestamp, units, filters, denominator and unknown-data behaviour for every
metric. Priya, Operations and Finance approval is still pending; these definitions
are proposed, not definitions supplied by the PDF. Do not implement new charts
before that approval.

| Metric | Grain/time/definition | Rules |
| --- | --- | --- |
| Submitted orders | Distinct order IDs by submission event time | Includes later acceptance/rejection; separate invalid API attempts never persisted as orders |
| Accepted orders | Distinct order IDs by acceptance event time | Durable intent, including orders still awaiting execution |
| Fill count | Distinct fill IDs by execution time | Deduplicate replayed deliveries |
| Executed orders | Distinct order IDs having a fill in the period | Multiple fills count once; distinct counts are not additive over periods |
| Executed quantity | Sum actual fill quantities, per instrument/unit | Never sum shares, FX base units and crypto units into one total |
| Gross executed notional | Sum price × fill quantity × required product multiplier, per quote currency | Buy/sell gross activity does not net away; preserve native currency and units; no notional when metadata is insufficient |
| Net cash movement | Sum signed ledger movements, per currency | Distinguish trading settlement, fee, adjustment and modelled cash movements; avoid counting an included fee twice |
| Fees | Actual committed execution fees, per currency | Report separately from gross notional; fees are not profit |
| Active clients | Distinct clients with a committed fill in the period | Count once across accounts; submitting clients are a separate metric |
| Rejection rate | For orders submitted in the period: rejected by report as-of / all submitted in that cohort | Display pending count and as-of; zero denominator unavailable; show pre-acceptance/execution rejection separately |
| Most active instruments | Rank by agreed measure (start with fill count), stable ID tie-break | Label ranking; converted notional ranking needs an approved FX convention |
| Prior-period change | Comparable equal calendar periods in selected timezone | Zero baseline unavailable; partial month vs full month must not be misleading |

Segmentation proposal already recorded in admin decisions (not yet approved):
registered-client tenure NEW (<30 elapsed days), ESTABLISHED (>=30 days), UNKNOWN
where registration time is unavailable. Do not infer registration time from first
account creation. A commercial classification can replace this only with an
approved source, values and effective-dated history. Always keep UNKNOWN in totals.

**User-approved 2026-10-23:** use event-time segment membership for historical
reporting; segment source/values and tenure bands remain pending. Distinct
clients who change segment during a period can legitimately occur in multiple
segment buckets, so segment-level distinct counts may exceed the overall unique
client count. A single monthly reference point has not been approved; introducing
one would require a labelled new contract version, not silent reclassification.

## 6. Step-by-step implementation

### Step 1: agree persona journeys and the metric dictionary

Produce a matrix linking each screen/metric to BR-14/15/16/17 and its user. Confirm
metric event time, reporting timezone, segment source/history, supported markets,
quantity/currency conventions, period closure, correction/version policy, freshness
and limits. Preserve prior user decisions about admin-only ticket work. Review
with Operations, Priya/commercial stakeholders, Finance and Compliance.

Exit: stakeholders can explain exactly what each number means and what David,
Priya and Joanna can access. Unapproved recommendations remain visibly provisional.

### Step 2: fix source evidence and metadata before aggregation

Persist real quote source, upstream quote timestamp, receipt timestamp, selected
bid/ask/price and decision, fee, client/account/instrument attribution and event
sequence. Store timestamps with timezone and honest precision. Preserve accepted
intent independently of execution, and fills/cash/holdings atomically.

Add instrument asset class, market/venue, quote/settlement currency, quantity unit,
precision and product conventions. Use IDs for joins; a ticker is only a label.
Add trustworthy client registration/segment history if the chosen segmentation
needs it. Fix the hardcoded USD path. Legacy unknowns remain unknown.

Exit: one real committed trade can be explained and reconciled without inferring
quote provenance or currency from a screen label.

### Step 3: model facts at explicit grains

Keep one row per order, per fill, per lifecycle event, per cash movement and per
holding movement. Add instrument dimension/history, a minimised client dimension,
segment effective dates, calendar/market conventions and refresh/report metadata.
Add dated account snapshots only if historical balance/valuation reports require
them; current holdings do not provide historical holdings.

Carry event/source IDs, schema version, event time, ingestion time and dataset
version. Define keys/constraints/indexes and decimal precision; FX/crypto quantity
requirements must not silently inherit integer-only equity fields.

Exit: joining one order with three fills and two cases cannot multiply volume.

### Step 4: strengthen ingestion and workload isolation

Start from the existing ETL rather than rebuilding everything. Bound extraction
work; use a replica or change capture when needed to meet isolation targets. Read
committed source state. With asynchronous delivery, use durable publication (for
example an outbox) and idempotent ingestion; no dual-write assumption that a broker
and database always succeed together.

Load/validate a new dataset in staging or versioned tables. Publish one pointer
atomically after reconciliation. Readers pin a dataset version so rows and as-of
metadata agree. Failed loads preserve the previous complete version. Retain the
versions required for monthly report reproducibility. Handle overlap, retry,
process crash, stale RUNNING status and recovery before enabling scheduling.

Separate reporting credentials, connection pools, query limits and appropriate
CPU/memory/I/O capacity. A second database on the same saturated server is not by
itself proof of BR-16 isolation. Bound ETL load too, not just dashboard queries.

Exit: reporting failure cannot roll back or block a client's committed trade;
representative reporting/ingestion load meets an agreed trading-impact limit.

### Step 5: implement and reconcile analytics calculations

Build tested query services using OLAP only for broad historical analysis. Add
period aggregation, per-instrument ranking, client activity, segment breakdown,
native-currency amounts where supported, and prior-period comparison. Define
rounding, distinct-count semantics, unknown-data treatment and total reconciliation.

Build a fixed fixture containing multiple clients/accounts/currencies/instruments,
orders spanning months and multiple fills. Establish expected results independently
of the implementation query. Test correction and late-arrival behaviour.

Exit: totals and breakdowns agree with the documented metric contract and source
ledger; no money total combines currencies without an approved conversion policy.

### Step 6: expose protected, bounded, versioned APIs

Proposed route family (not BRS-prescribed):

- GET /api/analytics/overview
- GET /api/analytics/activity
- GET /api/analytics/instruments
- GET /api/analytics/client-segments
- GET /api/analytics/data-status
- POST /api/analytics/reports (create a versioned report snapshot)
- GET /api/analytics/reports/{reportId}
- Optional POST/GET /api/analytics/exports for bounded asynchronous exports

Reuse the existing operations/evidence API family for David. Decide whether to
replace the frontend's missing analyst routes with these or implement compatible
handlers. Update proxy mappings too; the generic /api development proxy currently
strips that prefix, while /api/admin has a preserving mapping.

Responses include dataset ID, metric version, period boundaries/timezone, grain,
filters, source coverage/watermark, publication time, completeness/warnings and
pagination. Allowlist dimensions/sort fields; parameterise values. Apply role and
field restrictions, pagination, timeouts, cancellation, concurrency/rate limits
and consistent errors. Never fall back to heavy OLTP queries when OLAP fails.

Exit: an analyst can query aggregate reports but cannot retrieve raw investigation
records, mutate cases or alter financial data. Client tokens cannot access either.

### Step 7: build Priya's analytics workspace

Use the shared Tailwind components. Add common period/grain/instrument/market/
segment filters with clear defaults, an overview, activity line chart, instrument
ranking, segment table/chart, monthly report view and freshness panel. Accessible
underlying tables must show the same values as charts. Optional heatmap follows,
with labelled units and internal-flow semantics.

Persist/share filter state only through authorised URLs or saved report settings.
Cancel superseded requests; bind all panels to the same dataset. Distinguish
loading, empty, error, unavailable, stale, partial and final states. Do not replace
a failing endpoint with demo data in the production app.

Exit: Priya answers the month/instrument/segment questions from actual APIs with
no engineering help, and understands completeness and comparison limits.

### Step 8: align David's operations workspace and Joanna's boundary

Keep David's search/timeline/current account evidence accessible through OPERATIONS
capabilities; share narrow evidence services rather than giving him a client login.
Add actionable delayed/rejected/evidence-incomplete views if justified. Operational
exception aggregates can use OLAP with lag labels, while recent exact lookups use
an authoritative read path. Do not let Priya's broad filters trigger these reads.

Joanna continues using her own current account/trading services. Her holdings/cash
must reflect the last committed fill regardless of reporting state. Maintain
least-privilege navigation and backend authorisation for every principal.

Exit: personas perform their own jobs without gaining another persona's authority.

### Step 9: make monthly reporting reproducible

Retain report ID, filters, metric definitions/version, dataset/source cutoff,
calendar/timezone, output totals, completeness, generated-by/at and report status.
Recommended workflow: provisional -> finalised, with amendments as new versions.
This reporting workflow does not imply the BRS requires an approval product/UI.

Agree late-fill/correction cutoff rules with Finance/Priya. Preserve the original
issued report while making an amendment traceable. A dataset ID pointing at tables
that were subsequently truncated cannot reproduce a report. Exports, if supplied,
use that same version, permissions and totals; protect downloads and recheck access.

Exit: reopening a submitted monthly report returns the original figures; a later
correction has an explicit new version and explanation.

### Step 10: verify failures, privacy and recovery

Use PostgreSQL integration tests for ingestion replay, concurrent refreshes, read
consistency, publication failure, report snapshot durability and restart recovery.
Inject a failure between fill/cash/holding writes to verify no partial commit.
Retain existing case concurrency/revocation tests for the operations workflow.

Run query/ETL/export load at representative volume concurrently with real order
submission/execution. Measure trading throughput/p95/p99, lock waits, queue depth,
report latency, ingestion lag and recovery; compare with a trading-only baseline.
A small history-read benchmark is not that test. Test role removal during queries
and downloads, leakage through filters/counts and cross-client lookup boundaries.

Exit: documented evidence covers BR-09/14/15/16 and service expectations, with
known limits and stakeholder-agreed thresholds.

### Step 11: operationalise and review at checkpoints

Configure the agreed refresh schedule, monitoring/alerts, source-coverage checks,
quarantine/replay tooling, backup/restore, safe migrations and rollback. Old data
must survive deployment failures; bootstrap DROP/TRUNCATE scripts are not upgrades.
Define handling for suspected bad data, late publication, metric changes and
month-end amendments. Approve retention/holds and report access/export policy.

Demonstrate: Joanna trades while Priya runs a monthly report; David independently
explains a disputed trade; analytics fails and trading continues; recovery neither
loses nor duplicates records. Sponsor reviews the business-level architecture
explanation and the separately proposed BR-18 capability.

## 7. Edge-case and failure checklist

This is a release-oriented baseline. Final supported trading rules, data providers,
deployment and policies determine additional cases; no finite list can guarantee
all possible failures.

| Area | Cases | Expected behaviour/test |
| --- | --- | --- |
| Lifecycle | Submission rejected before acceptance; accepted but delayed; multiple attempts; execution rejection | Counts reflect distinct events and the correct cohort; David sees recorded reasons and order of events |
| Partial fills | Several fills/prices; fills cross midnight/month; partial then cancelled | Only executed quantities/notional counted; remaining quantity/state shown; order distinct counts not naively summed |
| Replay | Duplicate event/fill; replay after timeout/restart; new schema version | Stable source IDs and transactional deduplication prevent duplicate effects; incompatible records quarantined visibly |
| Event ordering | Fill arrives before order projection; equal timestamps; clock skew | Sequence/correlation and recovery handle ordering; do not invent precision or silently discard an unmatched fill |
| Crash | Source committed but publish failed; crash after staging before publication; stale RUNNING lease | Durable replay and atomic publication; last-good report remains usable; recoverable job state |
| Concurrent refresh | Two processes publish; source changes during load; long active readers | Lock/version checks; consistent source boundary; pinned report dataset; bounded waiting and retry |
| Metadata race | Data query completes against version A, as-of read returns version B; pagination across refresh | Pin dataset/version for all panels/pages and metadata; expire unavailable versions explicitly |
| Historical months | Late trade, correction, migration, old status changes | Event-time inclusion and versioned amendments; original issued report stays reproducible |
| Time | UTC vs market-local day, DST short/long days, leap day, month/year boundaries | Calendar-aware half-open intervals; display timezone; test each relevant boundary |
| Trading calendars | UK/US/India holidays/closures; FX sessions; crypto weekends | Distinguish expected no activity from missing coverage; do not apply one equity calendar to all assets |
| Instruments | Same ticker in different markets; rename/delist; missing asset class; FX base/quote reversal | Stable IDs and historical metadata; Unknown bucket; no guessed currency/unit; preserve original evidence |
| Precision | Fractional crypto/FX; huge amounts; fee minimums; rounding | Decimal arithmetic and instrument rules; aggregate raw values before display rounding; overflow validation |
| Currency | Mixed USD/GBP/INR; missing/stale conversion; fee currency differs | Native-currency totals; no silent consolidated total; record conversion source/time if conversion is later added |
| Meaning of volume | Requested vs executed; buy/sell netting; units across assets | Explicit measure and unit; gross turnover separate from net flow; no sum of incompatible quantities |
| Client identity | Multiple accounts; renamed/closed customer; registration unknown; merged identity if supported | Distinct client counting by approved stable ID; preserve history; no guessed registration date |
| Segment history | Client changes segment mid-month; overlapping effective dates; missing value | Defined event-time or period-reference rule; validate intervals; Unknown included; explain nonadditive unique-client buckets |
| Join multiplication | Multiple fills, events and dispute cases for one order | Aggregate facts at intended grain before joining; prove totals against independent fixture |
| Empty vs bad data | Zero trades, zero baseline, disconnected feed, failed query, missing quote | Separate zero/unavailable/error/incomplete; no divide-by-zero or fake zeros; show missing-data coverage |
| Comparisons | Month-to-date vs full prior month; different calendar lengths; changing segment definitions | Comparable periods and explicit labels/metric version; avoid misleading percentage change |
| Ranking | Tied instruments; one exceptionally large trade; popularity score undefined | Deterministic ordering, labelled rank metric, and no fabricated score/market-sentiment claim |
| Privacy | Analyst requests user-level records; predictable pseudonyms; tiny segment reveals a person | Backend field/row controls; approved opaque references; consider small-cell suppression where needed; protect totals as well as rows |
| Revocation | Permission changes during report or export; cached/downloaded report | Recheck server-side access at retrieval/delivery; expire protected links; retain permitted audit history |
| Query abuse | Unbounded range, deep offsets, huge export, many charts/jobs, malicious sort/filter | Allowlisted queries, limits/timeouts/cancellation; async bounded jobs; enforce OLAP-only analytics |
| UI concurrency | User changes filters quickly; slow old response arrives later; chart request fails | Cancel/ignore stale responses; consistent dataset; chart/table error distinct from valid empty result |
| Export | Inconsistent filters, CSV formula injection, download after revocation, interrupted job | Same report version/totals and permission policy; escape unsafe cells; idempotent job retry and explicit limits |
| Audit | Legacy missing quote provenance; gaps in sequence; archived evidence unavailable | Flag incomplete reconstruction; do not assert inferred facts; investigate access/recovery failure |
| Retention | No-deadline disputes vs deletion; legal hold; restored backup resurrects profile data | Compliance-approved lifecycle, hold/deletion controls and minimisation; never guess a legal duration |
| Workload isolation | OLAP unavailable; shared CPU/I/O exhausted; ETL long transaction; unbounded worker | Trading remains operational; extraction/resource limits; measure actual order latency and contention |
| Restore/deploy | Restore from backup then replay; schema/metric version change; failed migration | Stable IDs, reconciled counts/amounts, compatible or explicitly migrated readers; no evidence loss |

## 8. Proposed first delivery slices

1. Data contract, quote/currency evidence fixes, segmentation decision and fixture.
2. Isolated ingestion, reconciliation and versioned published data.
3. OLAP analytics APIs and direct permission tests.
4. Priya overview + period/instrument/segment reports with freshness.
5. Monthly snapshot/reproducibility; optional controlled exports.
6. David/Joanna persona boundary checks, representative isolation/recovery tests,
   operational runbooks and stakeholder acceptance.

Build one complete vertical slice first: commit a real trade, ingest it once,
show it in Priya's report, and open its evidence in David's workspace. Then extend
across time periods, asset classes and segments without changing metric meaning.

## 9. Remaining decisions and ownership

| Decision | Proposed owner | Existing position |
| --- | --- | --- |
| Metric definitions, reporting timezone, comparison/cohort rules | Priya + Finance + Operations | Proposed dictionary above; submission/acceptance distinction previously confirmed |
| Client segment and history, including unavailable registration time | Commercial stakeholders | Tenure proposal remains unapproved; account type stays separate |
| Native vs converted currency, unit/fee conventions | Finance + Trading Desk | Currency-separated launch recommended; no approved consolidated valuation |
| Month-end cutoff, report finalisation and amendments | Priya + Finance | Versioned report recommendation, not an explicit BRS workflow |
| Workload/volume, latency, freshness and stale-display policy | Operations + Engineering + Priya | Prior local targets are provisional; representative test needed |
| Analyst detail/export permissions and small-segment privacy | Security + Compliance | Least privilege required; detailed policy needs approval |
| Retention, legal holds, immutable archive and deletion | Compliance | No specific legal duration supplied by BRS; external approval outstanding |
| BR-18 extension | Sponsor | Dispute workflow user-requested; programme acceptance distinct from reporting delivery |

No application behaviour was changed as part of producing this plan.
