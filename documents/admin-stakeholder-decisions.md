# Admin portal: stakeholder answers

Updated: 2026-10-23. Companion to `admin-portal-requirements.md`.

Status legend: **Confirmed** means explicitly answered by the user. **Recommended**
means a proposed answer to an unanswered question, pending stakeholder adoption.
**Code** means observed implementation, not proof of tested behaviour. Where this
file conflicts with older proposals in the requirements draft, this decision
record takes precedence. No application code was changed by this document.

## 1. Dispute eligibility

- **Confirmed:** Customers may dispute their own trades or recorded cash movements.
  A trade-fee allegation belongs to the trade dispute; a standalone fee dispute
  type is unnecessary for this release. A fee-related marker should identify it.
- **Confirmed:** No submission deadline for trade disputes.
- **Recommended:** Apply the same no-deadline rule to cash-movement disputes for
  consistency. Historical or closed-account records remain eligible when owned
  by the customer. Missing evidence is flagged for investigation, not fabricated.
- **Confirmed:** Resolved cases cannot be reopened. Customers submit a new case.
- **Recommended:** Link eligible trades through their order ID and cash movements
  through an immutable cash-movement ID. Distinguish pending orders from actual
  cash charges; do not claim that an unfilled order caused an executed debit.
- **Code gap:** Submission currently accepts only `orderId`, reason, and an
  idempotency key. Cash-movement reference/type, fee marker, and title are missing.
  Real bank/card chargeback integration remains outside this release.

## 2. Duplicate disputes

- **Confirmed:** Multiple cases against the same transaction are permitted, both
  before and after earlier cases resolve. There is no business case-count limit.
- **Confirmed:** Admins judge materially different allegations and document their
  decisions. Related cases can be linked; do not overwrite or delete them.
- **Recommended:** Distinguish a deliberate new submission from a network retry.
  Each new submission uses a new idempotency key; replaying the same key and
  payload returns the same case. A reused key with a different payload conflicts.
  Idempotency must be scoped to the authenticated customer so another customer's
  key cannot return their case. Request throttling may protect service capacity
  without imposing a transaction-level lifetime case limit.
- **Code gap:** Current submission looks up a globally supplied key before
  checking ownership. Correct ownership-scoped replay validation is required.

## 3. Dispute permissions

- **Confirmed:** Only ADMIN may claim, accept, add internal notes, resolve,
  release, or reassign cases. ADMIN also fulfils the supervisor role. Reopen is
  disallowed for everyone. Customers may submit disputes about their own records.
- **Recommended:** Retain separate backend permissions but map dispute-work and
  supervisor capabilities to ADMIN only. Operations/Compliance financial read
  access does not imply case mutation permission.
- **Recommended:** Normally only the assignee changes a case. Another admin may
  explicitly release/reassign it using a supervisor action with a required
  reason and audit event; ordinary editing must not silently take ownership.
- **Confirmed:** When an assignee leaves or loses admin access, unfinished tickets
  return to OPEN with no assignee. Preserve all notes, prior assignments, and
  events. Resolved cases stay resolved with their history intact.
- **Recommended:** Revoke permissions and release active assignments in one
  controlled workflow, with retry/reconciliation for failures. Every case update
  rechecks current permission and assignment to prevent a revoked actor racing
  the release. Record automatic release under a system actor and reason.
- **Code gap:** Current assignee checks exist; takeover/reassignment and automatic
  return-to-pool behaviour need implementation/verification. Confirm that only
  ADMIN receives `PERM_DISPUTE_WORK`.

## 4. Case status and validation

**Confirmed:** `OPEN -> CLAIMED -> IN_PROGRESS -> RESOLVED`.

| Action | Allowed starting state | Result | Actor/reason |
| --- | --- | --- | --- |
| Submit | No case yet | OPEN, unassigned | Customer; transaction reference, short title, reason |
| Claim | OPEN | CLAIMED, assigned to actor | Admin; claim reason unnecessary |
| Accept investigation | CLAIMED | IN_PROGRESS | Assigned admin; acceptance reason optional |
| Add internal note | IN_PROGRESS | IN_PROGRESS | Assigned admin; nonblank note |
| Resolve | IN_PROGRESS | RESOLVED | Assigned admin; nonblank resolution reason |
| Release | CLAIMED or IN_PROGRESS | OPEN, unassigned | Assigned admin; recommend nonblank release reason |
| Supervisor release/reassign | Unresolved assigned case | OPEN unassigned, or CLAIMED with new owner | Admin supervisor action; required reason; new owner accepts again |
| Lose access/leave | CLAIMED or IN_PROGRESS | OPEN, unassigned | System action; recorded cause |
| Reopen | RESOLVED | Disallowed | New case instead |

The ordinary lifecycle and assignee checks above match `DisputeCaseService`.
The service does not require a release reason, does not expose reassignment,
and only records a resolution reason (no separate outcome field). Supervisor and
loss-of-access rows are intended requirements rather than existing transitions.
All changes must check expected version atomically; stale/concurrent edits return
conflict. Resolution changes the case only, never the underlying financial record.

- **Confirmed:** Submission fields are transaction ID, short title, and reason.
- **Recommended validation:** title 1–120 trimmed characters, reason 1–1,000,
  notes/resolution/release reasons 1–1,000. Validate transaction type/reference
  and ownership server-side. Reject blank values and render text safely.
- **Confirmed:** Dispute tickets concern specific financial transactions (trades
  or cash movements per section 1); support tickets concern platform questions
  and do not require a trade ID. Keep support and dispute case identities clear.
- **Recommended:** Separate case resolution labels UPHELD, NOT_UPHELD, and
  DUPLICATE/RELATED with explanation; their adoption needs UI/model changes.
  An upheld case does not automatically authorise a refund.

## 5. Account read/write boundaries

- **Confirmed:** Operations and Compliance may read balances/holdings but cannot
  update them. Only ADMIN may create accounts or edit account metadata among
  staff roles. This does not remove legitimate customer self-service rights.
- **Recommended:** Admin investigation uses shared account read services with
  explicit staff read permissions; do not impersonate the customer. Verify access
  on user lookup, account selection, holdings, orders, and trades individually.
- **Recommended:** No staff role receives general cash/holding mutation authority
  from these decisions. Financial corrections need a separate approved,
  attributable adjustment process; immutable trades remain unchanged.

## 6. Reporting definitions

- **Confirmed:** Submitted means received and in progress; accepted means the
  order has passed acceptance; a submitted order may instead be rejected.
- **Recommended:** Model submission and acceptance as recorded events, not as
  guesses from current status. Acceptance is not execution. A submitted order
  can be rejected before acceptance; an accepted order can fail later execution.
- **Recommended rejection rate:** For orders submitted in the selected period,
  numerator = distinct orders rejected by the report as-of time; denominator =
  all distinct submitted orders in that period. Include pending orders in the
  denominator and display their count. Label this submission-cohort rate; zero
  denominator is unavailable. Break out pre-acceptance versus execution rejection.
- **Recommended partial fills:** Count every unique fill once. Sum actual executed
  quantities and notional, never the full requested quantity. A partially filled
  order later cancelled retains its fills. Show fill progress separately from
  the final status. Distinct executed-order counts are not additive across days.
- **Recommended active client:** One unique customer with at least one committed
  fill in the period, regardless of account count. Report submitting clients as
  a separate metric if useful. Aggregate per instrument/currency appropriately.
- **Confirmed 2026-10-23 historical policy:** Use the effective classification at event
  time for each metric's event (submission for submission metrics, fill for fill
  metrics). Freeze historical classification unless a documented correction is
  applied. Label any current-classification analysis separately.
- **Code note:** Current PENDING is documented as accepted awaiting execution;
  it cannot alone establish the full submitted-versus-accepted history.

## 7. Currency and valuation

- **Recommended for initial release:** Keep monetary reports separated by
  currency. No consolidated base-currency total; no FX conversion required yet.
  Do not aggregate quantities across incompatible instruments either.
- **Recommended if conversion is later requested:** Finance selects a base
  currency and authoritative source before implementation. Record source,
  applicable timestamp, currency pair/direction, rate, precision, and conversion
  method with every converted value. Use event-time rates for historical flows
  and snapshot-time rates for valuations. Use decimal arithmetic, preserve raw
  precision, and round displayed totals to currency precision with an agreed
  policy. Missing/stale rates produce an incomplete/unavailable total, never zero
  or silently omitted amounts. Revisions must be attributable.
- **Recommended initial account view:** Show current quantities and cash without
  promising portfolio performance. If indicative valuation is shown, label quote
  time/source and availability. Display stale quotes with a stale label and omit
  an authoritative total when required quotes are missing. Indicative values do
  not supply execution prices or permission to trade.
- **Recommended deferred scope:** P&L/returns until cost-basis method, corporate
  actions, fee allocation, realised/unrealised treatment, and FX policy are agreed.
  Preserve actual trade fees separately from gross notional and net cash effect.

## 8. Client segmentation

- **Confirmed 2026-10-23:** Event-time historical classification is approved;
  segment source, commercial values and tenure bands remain pending. Storage
  availability does not authorise exposing segment reporting. See the proposed
  [metric dictionary](reporting-metric-contract.md) for review with Priya,
  Operations and Finance.
- **Recommended minimal launch segmentation:** Registered client tenure, derived
  from a persisted registration timestamp: NEW (<30 elapsed days at event time),
  ESTABLISHED (>=30 days), UNKNOWN (missing/unreliable registration time).
  This is a proposal for commercial approval, not an existing field or inferred
  wealth/risk classification. Do not backfill missing registration times from
  account creation dates as if they were known facts.
- **Recommended effective dates:** Derive tenure from event time; if the business
  later assigns commercial segments, keep effective-from/to history with no
  overlapping periods and apply the event-time rule from section 6.
- **Recommended unknown handling:** Include an explicit UNKNOWN bucket in totals;
  show its count and never silently exclude unclassified customers.
- **Recommended account type:** Report separately as an account-level dimension.
  A client with multiple account types must not be double-counted in overall
  unique-client totals. Segment and account type are different concepts.

## 9. Freshness and performance

The following are **recommended initial acceptance targets**, not measured
capabilities or requirements supplied by the BRS. Validate with representative
PostgreSQL data and concurrent trading/reporting workloads.

| Item | Proposed answer |
| --- | --- |
| ETL | Every five minutes; serialise runs; publish only validated complete datasets |
| Stale threshold | More than 15 minutes behind the trustworthy source watermark, or freshness unknown |
| Failed refresh | Keep previous complete dataset and show failure/as-of information; never show an error as zero |
| Stale display | Yes for historical analytics, visibly labelled; current user investigation uses operational reads |
| Date range | Default 30 days; maximum 366 days per interactive query; paginate bounded history; older records remain investigable |
| Interactive latency | p95 <=2 seconds for lookup, queue, and paginated history; p95 <=5 seconds for aggregate reports at agreed test volume |
| Page size | Default 50, maximum 100 rows |
| Exports | Deferred initially; proposed later asynchronous export cap 100,000 rows per job, with explicit truncation-free limit errors |
| Larger reports | Asynchronous jobs or narrower filters, not unrestricted synchronous scans |

Source publication time alone is not a data watermark. Do not infer “zero lag”
from an empty table or recent refresh timestamp. The environment/test dataset size,
concurrency, and permitted trading latency impact must be recorded before calling
these targets passed. Current ETL scheduling is configurable and disabled by
default (`etl.schedule.cron=${ETL_SCHEDULE_CRON:-}`), not already five-minute.

## 10. Audit and retention

- **Recommended:** Retain financial evidence and case/audit histories durably;
  disable automated deletion for the initial release. Do not invent a legal
  retention duration: Compliance must supply jurisdiction-specific periods,
  trigger dates, legal-hold rules, and deletion approval before production.
  No-deadline disputes require an explicit long-term evidence availability policy;
  this conflict cannot be solved by an arbitrary seven-year default.
- **Recommended access:** Compliance may read the complete audit trail. Admins
  read case/investigation audit events needed for their work; Operations reads
  relevant trade evidence. Analysts have no raw audit access. Log privileged
  reads and all case/permission mutations with actor, time, subject, and reason
  where applicable. Audit access does not imply modification/deletion rights.
- **Recommended storage:** Append-only application access plus separate database
  privileges, protected backups, and restore testing for the prototype. Before
  production, export evidence to independently controlled immutable storage with
  retention/hold settings approved by Compliance. Ordinary app-level append-only
  code alone does not establish that records cannot be altered after the fact.
- **Recommended minimisation:** Store stable identifiers and necessary evidence;
  exclude credentials/tokens and unnecessary profile data from events and notes.
  Restrict free-text exposure. Separate deletable profile data from retained
  financial evidence; evaluate deletion requests against approved retention and
  legal holds rather than cascading deletion through trades or tickets.

This section is a proposed technical policy and explicitly leaves legal retention
approval outstanding; it is not a statement of legal compliance.

## 11. BR-18 extension

- **Recommended:** Select the dispute investigation workflow as BR-18. It is
  already user-requested scope; programme acceptance of it as the extension is
  still to be confirmed.
- **Justification:** It gives customers a direct path to raise transaction/fee
  concerns and lets admins investigate using durable trade evidence and current
  account reads. It builds on BR-14/15 while adding customer submission, ownership,
  case progression, notes, and resolution. These are additional workflows beyond
  merely viewing an audit trail.
- **Recommended priority:** Core trading correctness, access control, durable
  evidence, and isolated required reporting first; dispute workflow next; visual
  enhancements, exports, and optional valuations later. Compared with price
  alerts or watchlists, disputes directly address operational support and the
  loss of the human dealing intermediary described in the BRS.

## Implementation differences to carry into the todo list

1. Add title, transaction type, cash-movement reference and trade-fee marker.
2. Preserve unlimited intentional new cases while making retries ownership-scoped
   and payload-checked; address concurrent submissions.
3. Enforce admin-only case mutations; implement explicit supervisor reassignment
   and automatic return-to-pool on loss of access, with race-safe checks.
4. Add validation bounds and required release/supervisor reasons; prohibit reopen.
5. Keep Operations/Compliance financial access read-only and staff account writes
   admin-only; verify shared endpoint permissions.
6. Apply agreed cohort metrics, partial-fill aggregation, and event-time segments.
7. Configure/measure the proposed freshness and query limits before claiming them.
8. Obtain Compliance retention policy and programme BR-18 acceptance. Those remain
   external decisions; recommendations above are ready for review, not approvals.
