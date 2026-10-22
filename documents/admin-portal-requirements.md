# Admin portal: scope and definitions

Status: Draft for stakeholder review. No stakeholder approval is implied.

Updated stakeholder answers: [admin-stakeholder-decisions.md](admin-stakeholder-decisions.md).
That record distinguishes user-confirmed decisions from recommendations and
supersedes conflicting earlier proposals below (including reopen, admin-only
case work, duplicate cases, and reporting definitions).

Source: `../LEAP-Project-Business-Requirements-Specification.pdf`, version 0.9,
reference LEAP-BRS-2026-014. References below use the PDF's requirement IDs and
sections. Proposed choices are implementation recommendations, not additional
requirements attributed to the PDF.

## Purpose and release boundary

Give operations staff enough evidence to explain any trade, and give internal
analysts historical reporting that does not compete with live trading.

Initial release scope updated by user direction: a desktop internal portal with
order search, trade investigation, user-ID lookup, current account views,
reporting, and dispute ticket claim/accept/work/resolution. Financial records
remain read-only; ticket ownership, notes, and case status are writable. Staff access crosses client boundaries only through explicit staff
permissions. Client APIs must continue to enforce client ownership.

Financial adjustments, placing trades for clients, editing completed trades,
cash deposits/withdrawals, account suspension, employee management screens,
and exports are outside this initial release. Their
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
- [x] Include dispute case handling per user direction.
- [ ] Confirm whether this fulfils the programme's BR-18 extension and document its justification.

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

5. Dispute queue and ticket detail: unassigned tickets, my tickets, status filters,
   claim/accept controls, investigation notes, and resolution. Link directly to
   the affected user, account, and disputed transaction.
6. User lookup: exact user-ID search, minimal profile, all authorised accounts,
   and per-account current holdings, balance, orders, and trade history.

Dispute case handling is now explicitly requested by the user. It extends the
BRS investigation requirement; the PDF itself does not prescribe this workflow.

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
| Claim/accept/work/resolve dispute tickets | Yes | Read-only by default | No | No by default |
| Look up any user ID for investigation | Yes | Yes | No | No by default |
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

- Security observations from the initial draft require revalidation against current
  code. The repository now includes `AccountAccessService` and staff provisioning
  routes. Account details permission is used on both GET and some mutation
  routes; split read/write permissions before granting investigation access.
  The inspected `getTrades` method checks existence but not account access; add
  owner/staff read authorisation before reusing it for investigation.
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

Remaining policy choices are OPEN. User direction has established dispute case
handling and current user investigation as in scope; D-02 and D-09 record the
remaining detail rather than reopening that scope decision. Suggested owners identify who should resolve them, not
people who have already accepted responsibility. Defaults are proposals only.

| ID | Decision | Proposed starting point | Suggested owner | Needed before |
| --- | --- | --- | --- | --- |
| D-01 | Staff roles, identity source, permission provisioning and revocation | Capability-based matrix above; controlled provisioning; enforce revocation server-side | Security + Operations | Staff access implementation |
| D-02 | Current account view consistency | User requests current holdings and trade history, plus writable dispute cases; operational reads separate from OLAP snapshots | Sponsor + Operations | API/UI contracts |
| D-03 | Client segmentation and historical classification | Effective-dated business classification; Unknown bucket; account type stays separate | Commercial analyst + Sponsor | BR-16 segment reporting |
| D-04 | Reporting day and timezone | UTC default with explicit timezone labels | Operations + Finance | Date filters and aggregation |
| D-05 | Metric and fee conventions | Metric contract above; notional excludes fees with fees reported separately | Finance + Operations | Dashboard calculations |
| D-06 | Currency conversion and precision | Currency-separated totals; no consolidated monetary total | Finance + Trading Desk | Monetary reporting |
| D-07 | Freshness, stale threshold, query limits, performance and volume targets | Separate OLAP, atomic publication, visible freshness; numbers TBD | Operations + Analyst + Engineering | Performance acceptance |
| D-08 | Retention, immutable storage controls, personal-data minimisation and audit access | No ordinary update/delete of evidence; numerical retention period TBD | Risk/Compliance + Security | Production readiness |
| D-09 | BR-18 justification | Dispute workflow requested and in scope; document value compared with alternatives for programme review | Sponsor + Delivery team | Extension work |
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


## Dispute workflow and user investigation: user-directed scope

The user explicitly requested staff to claim a disputed ticket, accept it, work
on it, and look up any user ID to investigate trade history/current holdings.
“Accept” is defined provisionally as accepting responsibility for investigation,
not approving the customer's allegation or issuing a refund.

### Functional requirements

| ID | Requirement | Acceptance evidence |
| --- | --- | --- |
| ADM-10 | Staff see unassigned disputes and their own assigned tickets | Queue supports status/assignee filters and server-side pagination |
| ADM-11 | An authorised staff member claims a ticket | Exactly one assignee wins concurrent claims; the loser sees a conflict and current owner |
| ADM-12 | The assignee accepts and works the ticket | Acceptance changes case state; staff can append notes and inspect linked evidence |
| ADM-13 | Staff search any user by exact user ID | Minimal profile and account list returned only with investigation permission; unknown ID has a clear not-found state |
| ADM-14 | Staff inspect current holdings and trade/order history | Account selector supports multiple accounts; paginated history and operational holdings carry as-of timestamps |
| ADM-15 | Staff record a dispute outcome | Assignee records upheld/not-upheld/withdrawn outcome and required reason; evidence and original trades remain unchanged |
| ADM-16 | Customers can dispute a recorded charge/transaction | Authenticated customer can submit a reason against their own eligible recorded transaction; case appears in the staff queue |

“Charge” currently means an in-platform recorded trade, fee, or cash movement.
Real card/bank chargebacks remain outside the original banking scope. Confirm
which transaction types are eligible and the submission window before building
ADM-16; the current model may not have separately identifiable fees/movements.
Never invent a transaction link from a supplied amount alone.

### Proposed case lifecycle

`OPEN -> CLAIMED -> IN_PROGRESS -> RESOLVED`

- Claim atomically assigns the authenticated staff identity and records claim time.
- Accept moves CLAIMED to IN_PROGRESS. Label the button “Accept investigation”
  to distinguish it from deciding the dispute outcome.
- Assigned staff append timestamped notes and a resolution reason/outcome.
- Release before resolution returns a case to OPEN with an audit reason.
- Reassignment requires a separate supervisor permission and an audit reason.
- Reopening requires a reason and preserves earlier resolutions; the exact
  supervisor/reopen policy remains to be agreed.
- Invalid transitions and edits against a stale version return a conflict.
- Repeat requests must not duplicate cases, notes, or transition audit entries;
  use request idempotency keys and case version checks.

The status vocabulary is proposed. Existing disputed_orders states DISPUTED,
UNDER-REVIEW, COMPLETED and support_ticket states OPEN, IN_PROGRESS, RESOLVED
need an explicit migration/mapping. Do not silently create two independent case
owners or competing sources of status. Choose one authoritative case aggregate.

Minimum case fields: case ID, customer/account reference, transaction/order
reference, category, reason, state, assignee, created/claimed/accepted/resolved
UTC timestamps, version, outcome, resolution reason, and append-only case events.
Separate internal notes from any customer-visible messages. Sending messages to
customers is a separate feature, not implied by internal case notes.

### API reuse and access contract

Reuse the same account read services and DTO contracts as the client wherever
practical. Either use existing GET routes with owner-or-staff-read checks or
thin staff wrappers calling those services. Do not impersonate the customer,
reuse their token, or grant account mutation permission for investigation.

Proposed missing lookup routes:

- `GET /admin/users/{userId}`: restricted profile DTO.
- `GET /admin/users/{userId}/accounts`: account choices for the selected user.

Existing account read routes to reuse after verifying permissions and pagination:

- `GET /accounts/{accountId}`
- `GET /accounts/{accountId}/balance`
- `GET /accounts/{accountId}/holdings`
- `GET /accounts/{accountId}/orders`
- `GET /accounts/{accountId}/trades`

Proposed case routes: `GET /admin/disputes`, `GET /admin/disputes/{id}`,
`POST /admin/disputes/{id}/claim`, `/accept`, `/notes`, `/resolve`, and `/release`.
A customer submission route such as `POST /disputes` must verify ownership of
both the transaction and account. All staff case mutations verify permission,
assignee, expected version, and transition inside the transaction. Staff identity
comes from authentication, never a trusted employee ID in the request body.

Operational user lookup and bounded history reads use the live account services;
aggregate reporting remains on OLAP. Display read timestamps and refresh after
new fills. Separate endpoint reads are not an atomic snapshot: do not imply
cross-panel consistency unless the backend supplies a consistent snapshot.
Audit staff lookups and case mutations without logging credentials or unnecessary
personal data. Lookup of unrelated users is permitted only to authorised
investigation roles and must be attributable to the staff actor and purpose.

### Additional acceptance and edge cases

- Two staff claim simultaneously: one owner, one conflict, no overwritten owner.
- Another staff member attempts acceptance, notes, or resolution: rejected unless
  explicit supervisory override applies; no client can call staff case routes.
- Staff permission revoked during work: subsequent requests fail; preserve case
  history and allow authorised reassignment if the owner becomes unavailable.
- Ticket already resolved or stale after a browser tab was left open: conflict
  and reload rather than silently reopening or overwriting it.
- Customer submits the same request twice: one case for an idempotent retry;
  distinct allegations against the same transaction follow an agreed duplicate policy.
- Customer supplies another user's transaction, an unknown transaction, or an
  account/transaction mismatch: reject without exposing another user's details.
- User has no accounts, no holdings, multiple accounts, or a closed account:
  clear empty/account-state labels; preserve authorised historical investigation.
- Trades execute while the case is open: current holdings refresh independently;
  investigation retains the disputed transaction's historical evidence.
- Missing or legacy transaction evidence: flag incomplete evidence; do not treat
  absence as proof that the charge was correct or incorrect.
- Notes contain markup or long text: validate length and render as safe text.
- Resolving as upheld does not automatically refund money or edit a trade.
  Financial redress requires a separately defined adjustment workflow.

### Implementation order for this scope

1. Define eligible disputed transactions, duplicate policy, case state mapping,
   and supervisor permissions; retain the agreed claim/accept distinction.
2. Split account read permissions from writes and close any missing ownership
   checks; add restricted user/account lookup and history pagination.
3. Implement the authoritative case model, transactional claim/accept transitions,
   append-only case events, versioning, and idempotency.
4. Add customer dispute submission against owned eligible transactions.
5. Build admin queue, ticket detail, user lookup, and linked investigation panels.
6. Verify concurrent claims, permission boundaries, retry behaviour, current-data
   labels, and resolution without financial mutation.

### Implementation status (2026-10-21)

| Area | Status | Evidence |
| --- | --- | --- |
| Backend permission enforcement and client ownership | Implemented | `SecurityConfig`, `AccountAccessService`, staff permissions, account/order ownership checks |
| Order evidence and normalized reporting facts | Implemented | Lifecycle, fill, cash, holding evidence entities and separate OLAP fact grains |
| Reliable full-load refresh | Implemented | Refresh run IDs, serialized execution, OLAP transaction, validation, failure status and freshness metadata |
| Bounded admin order/reporting read APIs | Implemented | `/api/admin/orders`, investigation events/fills, account summary, activity and reporting status |
| Exact user-ID lookup | Implemented | `/api/admin/users/{userId}` and `/api/admin/users/{userId}/accounts` |
| Dispute case persistence and workflow API | Implemented | Trade/cash eligibility, title/reason/fee marker, customer-scoped payload-checked idempotency, admin-only claim/accept/note/resolve, supervisor release/reassign, optimistic version checks, append-only case events, and automatic release on access revocation |
| Dispute eligibility/duplicate/supervisor policy | Open | Requires stakeholder decisions; current implementation rejects ownership mismatches and deduplicates idempotency retries |
| Angular admin queue, investigation timeline, lookup and dispute screens | Partial | Admin shell now consumes bounded order/reporting APIs and displays an open dispute queue with optimistic claim; full timeline, account lookup, and case detail actions remain |
| Concurrency/replay/reconciliation integration suite | Partial | Existing full suite and targeted controller/order tests pass; PostgreSQL concurrency/restart/replay scenarios remain to be added |

The remaining unchecked stakeholder decisions are intentionally not marked as
implemented: retention duration, final metric/currency/segment definitions,
performance targets, and the programme's BR-18 justification require approval
outside the codebase.

### Admin completion handoff (2026-10-21)

The earlier partial implementation table is superseded for UI/test status by
[admin-release-readiness.md](admin-release-readiness.md). Investigation, case
actions/history, operational user/account history, server pagination, and as-of
labels are implemented. A repeatable disposable PostgreSQL verification script
covers concurrent claims, application restart, replay and refresh rollback.
External legal retention approval, programme BR-18 acceptance, and representative
performance/freshness approval remain pending; implementation cannot grant them.
