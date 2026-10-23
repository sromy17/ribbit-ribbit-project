# Reporting metric dictionary and evidence contract

Version: `reporting-metric-contract-v2`, 2026-10-23.

**Status: proposed metric definitions, awaiting review with Priya, Operations and
Finance. These formulas are recommendations, not definitions supplied by the
PDF. Do not implement new charts against them until approval is recorded.**
This version supersedes the former acceptance-cohort rejection-rate proposal.
The user approved event-time historical segmentation on 2026-10-23; the segment
source, values and tenure proposal remain unapproved.

## Review and approval

| Reviewer | Decision required | Status |
| --- | --- | --- |
| Priya / Commercial | Metric labels, filters, active client, segment source/values, comparisons | Pending |
| Operations | Submission/acceptance/rejection events, fill finality, pending/outcome coverage | Pending |
| Finance | Native currencies, multiplier/product conventions, fees, cash reconciliation, rounding | Pending |

Record reviewer, decision, approval time and contract version before enabling
new calculations/charts. Proposed target definitions do not assert that every
existing report already implements them. The current activity report still
offers fill/instrument and current order-state aggregates, not this full suite.

## Common rules

- Periods are UTC half-open instants: `from <= event_time < to`. A reporting
  timezone defines calendar boundaries before conversion to UTC; show both.
- Common filters: stable instrument ID, market/venue, asset class, side, account,
  account type and, only after approval, client segment. Show all selected filters.
  Ticker is a display/search label, never an identity or a join key.
- Apply filters to the metric's grain and event time, without dropping rejected
  or unfilled orders just because no fill exists. A fill-only attribute filter
  does not silently redefine a submission-cohort denominator.
- Unknown filter attributes remain an explicit `Unknown` bucket. An explicitly
  selected known value excludes unknowns but reports their excluded count.
- Deduplicate source IDs, not equal-looking quantities/prices. Distinct counts
  are recomputed for the whole period; daily/account/segment distinct counts
  generally cannot be added to obtain the overall total.
- Missing or unreliable event times cannot be placed in a month. Preserve those
  rows and report an **unassigned-time count** for the otherwise selected dataset,
  not a fabricated midnight timestamp or a period-specific zero.
- Distinguish complete zero activity, partial known subtotal, unavailable data
  and stale data. Missing identity, unit, currency or convention is not zero.
  Expose affected row counts and completeness with any known subtotal.
- `asOf` is the evidence cutoff of the pinned dataset, not the browser clock.
  Use only committed source data available in that version. A watermark alone
  is not proof of complete source coverage.

## Starting metric dictionary (approval required)

Every row specifies grain, event time, unit, filters, denominator and unknown
behaviour. "Common" means the filters above at the named event time.

| Metric | Grain / distinct key | Event timestamp | Units / calculation | Filters | Denominator | Unknown-data behaviour |
| --- | --- | --- | --- | --- | --- | --- |
| Submitted orders | One order; distinct `requestid` with recorded `ORDER_SUBMITTED` | Recorded submission `occurred_at_utc` | Orders | Common at submission, including orders later rejected/cancelled/executed | N/A, count | Do not count lifecycle rows or infer submission from current status/acceptance; orders lacking submission evidence are uncovered and counted separately |
| Accepted orders | One order; distinct `requestid` with `ORDER_ACCEPTED` | Recorded acceptance `occurred_at_utc` | Orders | Common at acceptance | N/A, count | Missing acceptance evidence is not acceptance; later rejection/cancellation does not remove a recorded acceptance |
| Fill count | One committed fill; distinct `executeid` | `executed_at_utc` | Fills | Common at execution | N/A, count | Missing execution time goes to unassigned-time coverage; missing monetary metadata does not erase a known fill |
| Executed orders | Distinct `requestid` across fills in the period | Execution time of each qualifying fill | Orders with at least one fill in the period | Common on qualifying fills | N/A, count | Several fills count once; missing order/time attribution makes the count incomplete; current order status is not a substitute |
| Active clients | Distinct stable `client_id` across qualifying fills, across all accounts | Fill execution time | Clients | Common on qualifying fills; account restrictions apply before client deduplication | N/A, count | Missing client identity is reported as unattributed fills, never one invented client or a username; do not sum account counts |
| Executed quantity | One fill | Fill execution time | Sum actual `executed_quantity`, grouped by instrument ID **and recorded quantity unit** | Common at execution, plus quantity unit | N/A, sum | Missing unit remains an unlabelled-unit bucket and makes unit-qualified totals incomplete; never sum shares, FX base units or crypto units together |
| Gross notional | One fill | Fill execution time | Actual execution price x filled quantity x applicable multiplier; group by recorded execution currency | Common at execution, plus native currency; retain instrument/unit breakdown | N/A, sum | Missing currency/multiplier or unsupported product convention makes that fill unvalued; show unvalued-fill count and incomplete known subtotal, never assume USD or multiplier 1 |
| Rejection rate | One order in the selected **submission cohort** | Submission time selects cohort; rejection events/outcomes assessed through dataset `asOf` | Percent = 100 x distinct rejected cohort orders / distinct submitted cohort orders | Identical submission-time filters on numerator, denominator and pending count; do not filter the numerator by rejection-period instead | All distinct submitted orders in the cohort, **including pending** | Zero denominator = unavailable. Missing outcome evidence = unknown outcome, not pending/accepted. Show pending count, unknown-outcome count and `asOf`; incomplete cohort/evidence makes rate incomplete |

Rejection outcome mapping must be approved by Operations. `VALIDATION_REJECTED`
is recorded today; an execution attempt failure is not automatically a terminal
rejection. Break out pre-acceptance and post-acceptance rejection using evidence.
Count each order at most once in the numerator. Pending means reliably recorded
as unresolved at the cutoff, not merely absence of a rejection. Executed and
cancelled non-rejected outcomes remain in the denominator, not the pending count.
Conflicting terminal evidence is a quality exception.

### Additional measures already proposed in the requirements

| Metric | Grain / event time | Units | Filters | Denominator | Unknown-data behaviour |
| --- | --- | --- | --- | --- | --- |
| Fees | Committed fill / execution time | Actual fee, per recorded fee currency | Common at execution, fee currency | N/A | Unknown fee currency/amount gives incomplete subtotal, not zero; fees are not profit |
| Net cash movement | One cash movement / recorded movement time | Signed amount, per evidenced currency | Account, movement type, period, currency; instrument/segment only through unambiguous attribution | N/A | Historic currency without provenance is unverified; do not merge into verified totals. Settlement already includes fees in the current model: do not subtract them twice |
| Most active instruments | Instrument / qualifying fill times | Rank by fill count initially, stable ID tie-break | Same filters as fill count | N/A | Label ranking measure; no cross-unit quantity ranking or cross-currency notional ranking |
| Prior-period change | Pair of the same metric on comparable periods / its event time | Percent change | Identical filters and contract versions | Prior-period value | Zero/unknown baseline = unavailable; mark partial periods and incomplete coverage; do not compare partial month to full month as equivalent |

## Client segmentation

BR-16 requires analysis by client segment. **Account type is not a segment.**
The historical policy is approved: use membership effective at each metric's
event time, with `effective_from_utc <= event_time < effective_to_utc`; null end
means open-ended. For rejection cohorts, use submission-time membership for
numerator and denominator, not the segment at the rejection date.

The segment source/values remain pending. Do not expose segment analysis merely
because storage exists. NEW (<30 elapsed days), ESTABLISHED (>=30 days) and UNKNOWN
remain an unapproved tenure proposal requiring trustworthy registration times.
Do not derive registration from account creation or first trade.

Assigned commercial segments must retain source, effective dates and recording
time. Reject overlapping or reversed membership intervals; gaps map to `Unknown`.
Keep Unknown in totals. A client changing segment within a period can appear in
two segment active-client counts, but once overall. No exclusive monthly
reference-point policy has been approved. Never join history on client ID alone.

Do not overwrite published classifications when membership changes. Corrections
need an attributable new report/dataset version. Current full-refresh OLAP tables
do **not** retain old versions; reproducible historical publication remains a
delivery gate, not an implemented guarantee.

## Evidence and fact model

| Store | Explicit grain / key | Notes |
| --- | --- | --- |
| `order_facts` | One persisted order / `requestid` | Includes rejected orders, not just accepted ones; submission/acceptance are separate events |
| `fill_facts` | One committed fill / `executeid` | Snapshot client ID, execution/fee currency, quantity unit, multiplier, product convention and pricing-event link at execution; do not backfill from today's instrument |
| `lifecycle_event_facts` | One immutable event / `event_id` | Preserve sequence, event time, actual quote provenance, upstream quote time, receipt time, price type, price and currency separately |
| `cash_movement_facts` | One cash movement / `movement_id` | Preserve signed amount, currency and currency evidence source |
| `holding_movement_facts` | One holding movement / `movement_id` | Quantity delta and recorded post-movement quantity, not an inferred old balance |
| `clients_info` | One client / `client_id` | Registration time nullable; new registrations record real time only |
| `client_segment_history` | One membership interval / `membership_id` | Effective-dated history, source and recording time; no seeded guessed memberships |
| `instruments_info` | One current instrument / `instrumentid` | Asset class, market, venue, quote/settlement currency, quantity unit, price/quantity precision, multiplier, product convention, metadata source and qualified provider symbol |
| `accounts_info`, `holdings_info` | Current account / current account-instrument holding | Current snapshots, not historical balances |

Aggregate each fact independently before combining on stable keys. Never multiply
fills by cases/events. The legacy `trade_facts` compatibility projection is not a
metric source; it preaggregates fills and selects the latest related legacy case
deterministically rather than multiplying rows.

### Reconciliation fixture

One order with fills **4 shares at 100** and **6 shares at 102**, both with
known currency and a recorded multiplier of 1 / `PRICE_PER_UNIT`, must produce:

- Fill count: **2**.
- Executed orders: **1**.
- Executed quantity: **10 shares** for that instrument.
- Gross notional: **1,012** in the recorded execution currency, before fees.

Two cases and several lifecycle events must not change any of those results.
Missing multiplier/currency leaves two fills and ten recorded shares, but an
incomplete/unavailable notional. Do not use requested quantity or average price
as a substitute for the individual executions.

## Delivery and legacy-data gates

- Apply the non-destructive OLTP/OLAP analytics migrations before deploying the
  updated application/ETL. The root schema scripts are destructive initializers,
  not production upgrades.
- Do not backfill missing event times, registration times, currencies, conventions
  or historical balances. Legacy `market-data-verification` quote sources and USD
  cash labels lack proof; null receipt/currency-source metadata makes that visible.
  Preserve raw evidence and investigate/correct through an attributable process.
- Provider adapter supports optional `data.timestamp` (ISO instant), `currency`,
  `source` and `priceType`; absent values stay unknown. Its known provider is
  Fauxnance. `eventEpochMillis` in cached snapshots means application receipt
  time, **not upstream quote time**. Caching/Kafka must not replace provenance.
- Existing execution supports whole units and unit-multiplier `PRICE_PER_UNIT`
  pricing. Known unsupported conventions or cross-currency settlement are rejected,
  not priced as equities. Unknown legacy metadata remains explicitly incomplete;
  new fractional/product execution support needs a separately approved design.
- An instrument ID is canonical. Ticker-only ordering must reject ambiguity;
  cross-market instruments require an explicit qualified provider symbol.
- Price/quantity precision metadata records conventions; it does not increase
  existing integer execution quantity or guarantee arbitrary-precision storage.
  Precision/rounding and supported products require Finance review before exposure.
- Before releasing monetary/segment charts, approve source coverage, metadata
  curation, correction/closure policy and retained report versions. All responses
  must identify period/timezone, filters, contract version, dataset run ID, source
  watermark, publication time, refresh status and stale/incomplete state.
- A failed refresh retains the previous successful dataset and is not an empty
  or zero-valued report. Reporting load must remain separate from live trading.
