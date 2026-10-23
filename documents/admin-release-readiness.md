# Admin dashboard release handoff

Date: 2026-10-21. Status: automated verification passed; external
approvals below are NOT granted by this document.

## Delivered behaviour

- Staff sign-in uses the Spring login/session API and routes admins to the admin
  workspace using server-derived permissions. Customer stub sign-in remains a
  separate choice. Admin logout requests server session revocation.
- Order explorer supports date/symbol/status filters and server pagination. The
  reporting as-of timestamp is displayed; a direct order ID lookup reads current
  operational evidence independently of ETL lag.
- Investigation shows lifecycle sequence, pricing/quote evidence, individual
  fills/fees, cash movements, and holding movements. Missing lifecycle evidence
  is explicit. All operational panels identify their snapshot timestamp.
- Dispute queue is server-paginated. Case detail exposes claim, accept, internal
  notes, outcome/reason resolution, release, reassignment, reload, and case
  history. Assignee-only controls are reflected in the UI and enforced on the
  backend; stale-version conflicts require reload. Resolved cases cannot reopen.
- Exact user ID lookup supports account selection and bounded server-paginated
  orders, trades, cash movements, and current holdings. Cash is shown from the
  same repeatable-read snapshot as the selected history page.
- Reporting shows per-instrument fills/quantity and order-state breakdowns.
  Monetary portfolio valuation is explicitly unavailable when currency metadata
  is insufficient; no invented cross-currency total is displayed.
- Freshness reports the latest attempt separately from the last successful source
  watermark/publication. A 900-second threshold marks reporting stale. Failed
  refreshes retain the previous complete dataset.
- Refresh extraction uses a repeatable-read source transaction; publication uses
  an OLAP transaction and PostgreSQL advisory lock. The lock serialises database
  publication across application processes. This is PostgreSQL-specific.

## Verification commands

```bash
mvn -q test
cd frontend
npm test -- --watch=false
NG_BUILD_MAX_WORKERS=2 npm run build
cd ..
bash scripts/verify-admin-postgres.sh
```

The PostgreSQL script creates and removes its own isolated PostgreSQL 17
container and two databases; Docker must be running. It uses a disposable test
password, never the configured application database credentials. The standalone
PostgreSQL test requires `ADMIN_PG_URL` and must only target a fresh disposable
database named admin_verification. Normal Maven tests skip it without that variable.

The PostgreSQL suite verifies:

1. Real HTTP 401/403/200 account-history access boundaries using signed tokens
   and persisted sessions.
2. Idempotent submission replay, changed-payload conflict, cross-customer access
   rejection, and deliberate new tickets for the same transaction.
3. A two-thread PostgreSQL claim race with exactly one successful claimant.
4. Accept/note/resolve history, and rejection of release on a resolved case.
5. Server pagination across 10,001 orders and invalid-page rejection.
6. Actual order execution and redelivery rejection without additional cash or
   holding movements; first-holding average execution cost is persisted.
7. Repeated reporting loads without duplicate facts, and failed-load rollback
   preserving the last published dataset.
8. Application context shutdown/restart with the same database, preserving case
   history, fills, holdings, and submission/execution replay behaviour.
9. Forty operational history reads concurrent with forty reporting aggregates.
10. Revoking the admin role removes the owning-side role assignment, revokes
    its HTTP session, and returns unresolved work to the unassigned pool.

Measured local run: history p95 **13 ms**, reporting aggregate p95 **5 ms**,
and full refresh **743 ms**. The successful source watermark was under the
15-minute stale threshold. Backend suite: 36 tests passed; PostgreSQL scenario:
1 passed; Angular workspace tests: 4 passed. Production build passed. Browser smoke test confirmed staff login, user lookup,
claim, accept, internal note, and resolution using disposable fixture data.

Timing evidence is generated at `target/admin-verification/performance.txt`.
Measurements are local, use one account/10,001 orders/two fills, and exclude the
browser/network. They do not establish production trading capacity, representative
multi-client load, crash/power-loss durability, or approved service targets.

## External approval record

| Gate | Concrete proposal/evidence | Responsible reviewer | Status |
| --- | --- | --- | --- |
| Legal retention | Preserve financial/case/audit evidence; no automated purge; approve periods, start dates, legal holds, privacy handling and immutable-storage controls | Risk/Compliance | PENDING |
| BR-18 | Select customer dispute submission plus admin assignment/investigation/resolution; adds case workflow beyond the BRS audit-view requirement | Programme Sponsor | PENDING |
| Performance targets | Proposed p95 <=2 s operational reads, <=5 s reports; local measurement file; agree production volume/concurrency and trading-impact limit | Operations + Analyst + Engineering | PENDING |
| Freshness targets | Proposed five-minute refresh, stale after 15 minutes, visible last-good data; scheduler remains disabled unless configured | Operations + Analyst | PENDING |

For each approval record the reviewer, decision date, approved policy/target
version, any conditions, and a link to the actual written decision here. An
implementation or test pass must not be substituted for stakeholder approval.

### Retention policy ready for Compliance review

- Covered evidence: orders, pricing decisions, fills, financial movements, case
  events/notes/resolutions, privileged access and role-change audit records.
- Current release behaviour: no scheduled deletion job; normal application
  workflows do not rewrite completed trade evidence or reopen resolved cases.
- Approval fields: governing jurisdictions; retention duration by record type;
  retention start event; legal-hold procedure and owner; archive access; deletion
  authorisation; personal-data removal/pseudonymisation; backup expiry; independently
  controlled immutable archive and restore-test requirements.
- No-deadline dispute submission must be reconciled explicitly with the approved
  long-term evidence availability and deletion policy.
- Ordinary application append-only behaviour and database backups alone are not
  proof of legal compliance or storage immutability.

### BR-18 proposal ready for Sponsor review

Select the dispute workflow because it lets clients raise trade/fee/cash concerns
and lets admins own, investigate, and resolve them using durable evidence and
current account views. Compared with alerts/watchlists, it addresses the support
burden created by removing the human dealer from routine transactions. Core
trading correctness and required audit/reporting controls retain higher priority.
Approving a case does not perform financial redress; an adjustment workflow needs
separate scope and controls.

## PR review notes

- The working tree already contained substantial in-progress admin/backend work
  when this task began. Review the complete diff, including untracked source
  files; do not include generated target/ or frontend/dist outputs in the PR.
- `basedSchema.sql` and `olap.sql` are destructive bootstrap scripts, not safe
  production migrations. Apply reviewed additive migrations to an existing
  deployment. Session token storage needs capacity 4096 instead of 256. Reporting timestamps
  use TIMESTAMPTZ; any migration of legacy timezone-free timestamps must identify
  their actual source timezone instead of blindly assuming UTC.
- Existing legacy rows may lack precise lifecycle/quote evidence; the UI flags
  missing lifecycle events instead of claiming reconstruction is complete.
- Operational history requests are consistent within each response. Separate
  panels/pages refreshed at different times do not form one global snapshot.
