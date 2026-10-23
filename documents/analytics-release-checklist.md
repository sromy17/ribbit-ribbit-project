# Analytics release checklist

Updated 2026-10-23. This is an evidence register, not a claim of production sign-off.
The user confirmed that no approved small-segment disclosure policy exists.

| Area | Implemented treatment and automated evidence | Remaining release condition |
| --- | --- | --- |
| Order lifecycle | Count submission/acceptance events independently; delayed/rejected fixture orders, failed pricing validation and persisted execution attempts. Post-acceptance execution failure is recorded after rollback, with its reason, while financial effects remain unchanged. Executed orders cannot be cancelled. | Operations approval of terminal outcome definitions. |
| Partial fills | Multi-price example reconciles to 1012; additional January/February fills preserve 4 and 6 units and cancellation of the remainder does not imply a conflict. Equal-time events retain sequence. | Execution engine still executes whole orders; these tests consume recorded partial fills, not a new partial-matching engine. |
| Retries/replay | Pessimistic order/account locks allow one simultaneous execution to commit; replay after restart changes no cash/holding/fill records. Durable row-locked ID counters prevent cross-account submission/fill ID collisions. | HTTP creation of a *new* order has no submission idempotency-key contract. Retrying an ambiguous placement POST is distinct from replaying an accepted order ID and requires a follow-up contract. |
| Out-of-order evidence | Publication rejects unattributed/mismatched evidence and retains last-good data. Quote cache rejects future receipt clocks, nonpositive prices and older/equal duplicate receipts. Recorded future fill times are excluded at cutoff and counted in coverage. | Upstream clock/error budgets and feed reconciliation SLA require agreement. |
| Refresh failures | Publisher exclusion lock; durable RUNNING state is recovered to ABANDONED by the next lock owner after a crash. Failed publication rolls back data. Concurrent publisher and abandoned-row fixtures cover recovery. | Real process termination/network-partition chaos and production runbooks remain operational validation. OLAP pool requires at least two connections for separately committed run metadata. |
| Dataset consistency | First response establishes dataset token; panels, pagination and issuance send it. Backend snapshot rejects a changed token with 409. UI cancels superseded requests and refuses mixed versions. | Raw historical datasets are not retained. Expired tokens require refresh; saved reports remain retrievable. |
| Historical changes | Immutable saved payloads retain totals, classifications, filters, versions and coverage; an explicit amendment freezes corrected evidence. Original report survives refresh and restart. | Late-arrival/correction policy approval. |
| Time/calendar | DST 23/25-hour days, leap day, UTC/New York month boundary and calendar-month reports tested. Recorded events are not removed just because a date is a weekend/holiday. | No authoritative UK/US/India holiday/session calendar or market-open interpretation is supplied; do not claim trading-day-normalized comparisons. |
| Instrument identity | Repeated ticker fixture uses stable IDs; qualified provider symbols, unknown metadata and ambiguity rejection preserve identity. Renamed ticker labels are frozen in issued reports. | Curated venue/product metadata, FX base/quote direction and corporate-action/delisting conventions require approved sources. |
| Currency/precision | Native currencies, differing fee currency, per-instrument/unit quantities, missing valuation metadata and exact decimal report storage. Fractional order JSON is rejected rather than truncated. | Fractional execution and FX conversion are unsupported; no silent unit/FX conversion or rounding policy is inferred. |
| Client counting | Stable fill-time client IDs; one client across two accounts counts once. Registration time is not fabricated; client names are absent from analytics. | Deletion/closure and identity-merge policy must preserve attribution under approved retention rules. |
| Segment changes | Event-time effective memberships, UNKNOWN gaps, overlap rejection; segment counts need not sum to the period unique count. | Segment source/values and disclosure policy remain unapproved. Segment views/filters are withheld by default. |
| Join multiplication | Several fills plus cases/events do not inflate normalized measures; quantity/notional/fee reconciliation uses independent instrument/currency/unit grains. | Production-scale reconciliation thresholds and alert routing require operating ownership. |
| Empty/error states | Empty cohort rate is null; unavailable reporting returns 503 without OLTP fallback; stale, incomplete, withheld and zero are distinct UI states. | Prior-period percentage comparisons and feed availability SLAs are not part of the implemented metrics. |
| Privacy | Reporting permission enforced on backend; no client/account identifiers in analytic responses. Segment endpoint/filter access and saved reports containing segment rows are blocked without policy approval. Analyst cannot trigger ETL. | An approved disclosure algorithm (including small groups and differencing attacks) is a release gate. An approval configuration flag alone does not define such an algorithm. |
| UI/query concurrency | Superseded filter responses ignored; invalid filters end loading; 366-day/100-row limits, static grouping/sorting, timeouts, bounded report size and dataset tokens. | No exports exist; future exports must use the saved version and identical permissions. |
| Retention/recovery | No automatic report deletion or guessed retention deadline. Immutable report rows; application restart checks and disposable PostgreSQL backup/restore compare financial/evidence/counter/report row-set checksums. | Legal holds, no-deadline dispute retention, profile erasure/minimization, backup retention and production RPO/RTO require an approved policy and operational rehearsal. |

## Acceptance journeys

- **Joanna:** real HTTP submissions execute under concurrent analytics/ETL; current financial effects persist. Reporting failure does not stop another order. Duplicate execution and restart replay are inert. The new simultaneous cross-account fixture checks unique order/fill IDs.
- **David:** investigation coverage now labels missing submission, acceptance, pricing, fills, movements and sequence gaps; a nonempty timeline is not completeness proof. The protected investigation response contains ordered submitted, accepted, attempted, pricing and fill evidence, plus financial movements. Partial cancellation retains actual fills; impossible post-execution cancellation is rejected.
- **Priya:** period/instrument totals and known subtotals reconcile to independent fixtures. Reports preserve dataset, metric version and completeness through amendment/restart. **The client-segment production journey is deliberately withheld until disclosure/source approval.**

## Deployment changes

Apply `scripts/analytics-release-hardening-oltp.sql` to pre-provision durable ID
counters. Runtime startup also initializes this additive table for development.
Retain it with the source database and never clear it in ETL. JSON fractional-to-int
coercion is disabled globally for numeric IDs/quantities; unsupported fractional
orders return 400. No historical values are rewritten.

`analytics.segment-disclosure-approved` defaults to false. Production must keep
it false until the actual disclosure policy is approved and implemented. Synthetic
PostgreSQL fixtures explicitly enable segment data solely for formula testing;
that setting is not stakeholder approval. Reports issued while withheld preserve
an unavailable segment section. Old retained reports containing segment data are
also denied rather than silently returned or rewritten.

Run `mvn test`, frontend tests/build, and `bash scripts/verify-admin-postgres.sh`.
See `target/analytics-verification/recovery.txt` and the mixed-load output for local
verification evidence. Production capacity and policy approvals are not implied.
