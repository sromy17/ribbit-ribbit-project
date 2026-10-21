# Admin portal: scope and definitions

Status: Draft for stakeholder review. No stakeholder approval is implied.

Source: `../LEAP-Project-Business-Requirements-Specification.pdf`, version 0.9,
reference LEAP-BRS-2026-014. References below use the PDF's requirement IDs and
sections. Proposed choices are implementation recommendations, not additional
requirements attributed to the PDF.

## Purpose and release boundary

Give operations staff enough evidence to explain any trade, and give internal
analysts historical reporting that does not compete with live trading.

Proposed initial release: a desktop, read-only internal portal with order search,
trade investigation, restricted account views, reporting, and data freshness
indicators. Staff access crosses client boundaries only through explicit staff
permissions. Client APIs must continue to enforce client ownership.

Financial adjustments, placing trades for clients, editing completed trades,
cash deposits/withdrawals, account suspension, employee management screens,
exports, and dispute case management are outside this initial release. Their
exclusion is a proposed scope choice; it is not a statement that the PDF bans
all of these capabilities. Advised dealing and real banking integration are
explicitly outside the PDF's phase scope (§4.2).

## Requirement checklist and acceptance criteria

Unchecked items are planned outcomes, not claims about existing implementation.

| ID | Source and priority | Planned outcome | Acceptance evidence |
| --- | --- | --- | --- |
| ADM-01 | BR-14, Must | Durable evidence for accepted orders, pricing decisions, and resulting cash/holding changes, attributable to client and time | A selected executed order links to its accepted intent, pricing evidence, fills, and movements after a restart |
| ADM-02 | BR-15, Must | Search for an order and inspect its complete lifecycle without engineering logs | Operations can explain placement, pricing, outcome, and any recorded failure/rejection from the portal |
| ADM-03 | BR-16, Must | Historical analysis by instrument, period, and client segment | Known fixture results match expected totals for each breakdown; reporting uses isolated reporting resources |
| ADM-04 | BR-17, Should | Small dashboard of activity and trends | Dashboard exposes defined order, execution, and client activity metrics with period and freshness labels |
| ADM-05 | §9.3 | Role-limited internal access | Direct API requests outside each role's permissions fail; sensitive fields are omitted from responses |
| ADM-06 | §9.4; BR-14/15 | Unalterable, retained historical trade evidence | Portal cannot mutate trade evidence; persistence controls prevent ordinary update/delete access; retention period is agreed separately |
| ADM-07 | §9.1; BR-09 | Trustworthy results across failure and recovery | Restart/replay tests show no missing or duplicated fills or movements; filled-trade updates remain atomic |
| ADM-08 | Proposed support for BR-16/17 | Clear reporting freshness and safe refresh failure behaviour | Failed refresh retains the last complete dataset; UI identifies its timestamp and stale state |
| ADM-09 | Proposed support for BR-15 | Restricted account overview | Authorised staff can link an order to its account's holdings, cash, and history; every snapshot has an as-of label |

- [ ] Confirm initial release boundary.
- [ ] Agree roles and access matrix.
- [ ] Agree metric, time, segment, and currency definitions.
- [ ] Agree reporting freshness and performance targets.
- [ ] Agree retention and audit access policy with the relevant stakeholders.
- [ ] Select or defer a separate BR-18 capability.

## Proposed screens

1. Order explorer: order/account/client reference, instrument, side, requested and
   executed quantities, status, and relevant timestamps. Filter by identifiers,
   period, instrument, side, and status; paginate and sort on the server.
2. Trade investigation: ordered lifecycle events, validation/pricing evidence,
   individual fills, fees if applied, and linked cash/holding movements. Missing
   evidence must be visible rather than reconstructed as an asserted fact.
3. Account overview: minimum necessary identity, cash, holdings, and order links.
   Label whether data is an operational read or a reporting snapshot.
4. Reporting/dashboard: agreed metrics by period, instrument, and client segment,
   plus freshness and incomplete-data indicators.

Dispute handling is optional. The ability to investigate a dispute is required;
assignment queues, case notes, and case resolution are not explicitly required
by the BRS. Existing dispute/support tables do not establish approved scope.

## Proposed access matrix

Permission names describe capabilities; final identity-provider role names are
not yet decided. Default deny, including for unknown roles. A person may receive
multiple approved permissions; the platform administrator role alone does not
imply access to client financial data.

| Capability | Operations | Compliance | Analyst | Platform administrator |
| --- | --- | --- | --- | --- |
| Aggregate reports | Yes | Yes | Yes | No by default |
| Order lifecycle and evidence | Yes | Yes | No | No by default |
| Identifiable account/holdings/cash | Necessary for investigation | Necessary for review | No | No by default |
| Client-level activity analysis | No by default | No by default | Pseudonymous identifiers only, subject to approval | No |
| Change trades, holdings, cash | No | No | No | No |
| Trigger reporting refresh | No | No | No | Separate explicit operational permission |
| Provision/revoke staff permissions | No | No | No | Controlled process; UI deferred |

Never return credentials, password hashes, salts, or session tokens. Apply
permissions to backend endpoints and response fields, not only UI navigation.
Role removal/session revocation semantics must be agreed in D-01.

## Proposed metric contract

These definitions are provisional until reviewed by Operations and Finance.
Each response must identify its period, timezone, filters, and dataset as-of
time. All metrics use distinct stable identifiers at their stated grain.

| Metric | Proposed definition | Boundary/exception handling |
| --- | --- | --- |
| Accepted orders | Distinct orders durably accepted in the period, by acceptance time | Includes subsequently rejected/cancelled orders; excludes pre-acceptance validation failures |
| Executed orders | Distinct orders with at least one fill in the period, by fill time | One order can appear in multiple periods; summing period distinct counts is not an overall distinct count |
| Fill count | Number of distinct committed fills in the period | Deduplicate retried events by fill ID |
| Executed quantity | Sum of fill quantities by instrument in the period | Do not combine shares, FX units, and crypto units into one quantity total |
| Executed notional | Sum of fill quantity × execution price, grouped by quote currency | Product/contract multipliers and FX base/quote conventions must be defined per instrument; unavailable metadata makes notional unavailable |
| Active clients | Distinct client IDs with at least one fill in the period | Multiple accounts count once; pre-fill submissions alone do not qualify |
| Acceptance-cohort rejection rate | Accepted orders in the period later rejected / accepted orders in the period | Zero denominator returns unavailable; label as-of time and pending cohort outcomes; pre-acceptance failures are a separate metric |
| Most active instruments | Rank by fill count initially | Label ranking basis; break ties by stable instrument ID; monetary ranking requires currency rules |
| Period change | (current − prior) / prior for equal calendar periods in the selected timezone | Prior zero returns unavailable; incomplete current periods require an explicit like-for-like comparison |

Cash and holdings in the account overview are snapshots, not period trading
metrics. Distinguish available cash from total/ledger cash if reservations exist.
Do not publish profit/loss, portfolio market value, or return percentages until
cost basis, quote freshness, fees, currency conversion, and valuation rules are
specified. Do not treat today's available funds as historical funds at execution.

## Time, currency, and segmentation

Proposed time contract:

- Persist event instants in UTC with sub-day precision and a sequence/version
  where timestamps alone cannot order events.
- Default reporting timezone to UTC until the business selects its convention.
- Use half-open ranges: `from <= event time < to`. Convert selected local date
  boundaries to UTC with timezone rules; do not assume every day is 24 hours.
- Use the timestamp relevant to the metric, not ingestion time. Late arrivals
  may revise a historical period; expose the dataset publication time.
- Keep event time, ingestion time, source watermark, and publication time distinct.
  A publication timestamp alone does not prove source freshness.

Proposed currency contract: show monetary totals separately by currency. A
consolidated base-currency view is deferred until Finance chooses the base
currency, rate source, conversion time, and rounding policy. Use decimal
arithmetic and instrument-specific quantity/price precision.

Client segment is unresolved. `AccountType` describes accounts, whereas BR-16
asks for client segments. Do not silently relabel account type as client segment.
Keep account-type breakdowns separately named. Recommended segment design is an
approved client classification with effective dates and an explicit Unknown
bucket. Decide whether reports use event-time or current classification, and
how clients with multiple accounts are counted. The segment requirement remains
open until a business definition and data source are supplied.

## Freshness, reliability, and operational behaviour

Proposed implementation defaults, pending service target agreement:

- Reporting reads a separate OLAP store; broad aggregation does not use live
  trading queries. Measure refresh/extraction impact on trading as well.
- Publish complete validated datasets atomically. Retain the last successful
  version when refresh fails and prevent overlapping refresh publication.
- Expose last success, source watermark, refresh state, and failure/stale state.
- Distinguish empty results from failed queries, unavailable values, and partial
  evidence. Never turn an error into a zero metric.
- Paginate order results with deterministic sorting and a stable ID tie-breaker.
- Treat historical audit evidence as durable records distinct from replaceable
  reporting projections. Rebuilding OLAP must not erase source evidence.

Numerical refresh intervals, stale thresholds, response-time targets, maximum
query ranges, and supported data volumes are not specified in the BRS. They
remain open decisions rather than assumed acceptance thresholds.

## Existing implementation constraints to address in later steps

Observed in the current repository; these observations are not a full code audit:

- `SecurityConfig.java` converts JWT roles but globally requires authentication
  without an admin permission matrix. `EtlController.java` has no explicit role
  restriction. Add backend permission enforcement before exposing staff routes.
- `OltpToOlapEtlService.java` truncates published tables before sequential loads.
  Introduce consistent extraction and atomic publication with failure recovery.
- `olap.sql` puts an aggregate at order grain. Preserve individual fills and
  lifecycle events in additional evidence/reporting structures.
- Several source/reporting trading times are `DATE`, which cannot establish the
  precise sequence required for investigation. Do not invent missing times for
  legacy rows; expose unknown precision and migrate future writes.
- ETL `MAX(te.askprice)` is not necessarily the last fill price. Select by an
  explicit deterministic execution ordering if a last-price field is retained.
- Current `OrderStatus` values are PENDING, EXECUTED, CANCELLED, REJECTED. PENDING
  is documented as accepted awaiting execution. Map these meanings explicitly
  to UI labels; do not infer a separate submitted event or partial-fill status
  when it has not been persisted.
- Existing integer quantity columns need review against FX/crypto precision.
- No client-segment field or approved segmentation rule was identified in the
  inspected account/user schema.

## Decision register

All entries are OPEN. Suggested owners identify who should resolve them, not
people who have already accepted responsibility. Defaults are proposals only.

| ID | Decision | Proposed starting point | Suggested owner | Needed before |
| --- | --- | --- | --- | --- |
| D-01 | Staff roles, identity source, permission provisioning and revocation | Capability-based matrix above; controlled provisioning; enforce revocation server-side | Security + Operations | Staff access implementation |
| D-02 | Initial scope and meaning of account overview | Read-only portal; operational current view clearly separated from OLAP snapshots | Sponsor + Operations | API/UI contracts |
| D-03 | Client segmentation and historical classification | Effective-dated business classification; Unknown bucket; account type stays separate | Commercial analyst + Sponsor | BR-16 segment reporting |
| D-04 | Reporting day and timezone | UTC default with explicit timezone labels | Operations + Finance | Date filters and aggregation |
| D-05 | Metric and fee conventions | Metric contract above; notional excludes fees with fees reported separately | Finance + Operations | Dashboard calculations |
| D-06 | Currency conversion and precision | Currency-separated totals; no consolidated monetary total | Finance + Trading Desk | Monetary reporting |
| D-07 | Freshness, stale threshold, query limits, performance and volume targets | Separate OLAP, atomic publication, visible freshness; numbers TBD | Operations + Analyst + Engineering | Performance acceptance |
| D-08 | Retention, immutable storage controls, personal-data minimisation and audit access | No ordinary update/delete of evidence; numerical retention period TBD | Risk/Compliance + Security | Production readiness |
| D-09 | Additional BR-18 capability | Consider disputes, compare value with alternatives; defer case workflow until selected | Sponsor + Delivery team | Extension work |
| D-10 | Partial-fill/cancellation support and status mapping | Display recorded facts; agree supported transitions before adding states | Trading Desk + Engineering | Lifecycle API contract |

## Initial release acceptance scenarios

1. An authorised operations user finds an executed order and explains its
   accepted intent, quote/pricing decision, fills, and cash/holding effects.
2. The same evidence survives restart; replay does not duplicate fills or totals.
3. A rejected or delayed order has an accurate outcome/reason or pending state,
   without fabricated fills or missing evidence silently presented as complete.
4. A client token and an analyst token cannot retrieve identifiable investigation
   details through direct staff API requests.
5. Fixed multi-account, multi-instrument, multi-currency fixtures produce the
   agreed totals; duplicated events do not change them.
6. Date-boundary and daylight-saving fixtures follow the agreed interval rules.
7. A failed refresh leaves the prior complete report available with a stale/error
   label. Reporting workloads meet the agreed trading-isolation targets.
8. Segment reporting uses the approved classification, including Unknown and
   reclassification behaviour. Account type is never substituted silently.

## Next implementation handoff

Step 1 produces this reviewable scope and decision register. It does not approve
policy choices or alter application behaviour. Resolve D-01/D-02 first to begin
staff access and endpoint design; metric and pipeline design can proceed against
the explicitly provisional contracts while their owners review remaining items.
