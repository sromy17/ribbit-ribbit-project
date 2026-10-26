# Merged application verification

The application uses Tailwind 4 through `frontend/src/styles.css` and the shared UI components. Investor, admin, analytics, account and authentication screens share the same theme and controls. Chart geometry remains component CSS; it uses the shared theme.

## Repeatable full connection check

Prerequisites: Java 25, Maven, Node/npm, Docker, installed frontend and auth-stub dependencies (`npm ci` in each directory).

```sh
bash scripts/verify-merged-connection.sh
```

This creates disposable PostgreSQL source and reporting databases from the repository schema/seed scripts. It builds and starts Spring on 18082, customer authentication on 14000, and Angular on 14200. Requests pass through the real Angular proxy. It checks staff permissions, admin account history, ETL publication, analyst OLAP queries, customer accounts/wallet reads, both refresh providers and logout revocation. It cleans up its processes and database when finished. Logs are retained in the printed temporary directory. Ports 18082, 14000 and 14200 must be free.

For interactive testing:

```sh
bash scripts/verify-merged-connection.sh --preview
```

Open http://127.0.0.1:14200. These credentials exist only in that disposable fixture:

| Username | Password | Staff sign-in | Workspace |
| --- | --- | --- | --- |
| john_trader | mission123 | Checked | Admin |
| jane_investor | mission123 | Checked | Analytics |
| bob_daytrader | mission123 | Unchecked | Investor |

Stop the foreground script with Ctrl-C to remove the disposable stack. Never use the fixture credentials or signing secret for a deployment. Seed history lacks some precise timestamps, instrument metadata and currencies; analytics correctly labels its dataset incomplete rather than inventing evidence.

## Regression checks

```sh
mvn -q test
bash scripts/verify-admin-postgres.sh
cd frontend
npm test -- --watch=false
NG_BUILD_MAX_WORKERS=2 npm run build
```

The PostgreSQL check includes claim races, restart/replay, trading/reporting load, reporting failure isolation, report reproduction, token rotation/revocation, wallet ownership, and source/OLAP backup restoration. The full connection check complements these tests with the actual frontend proxy and Node auth service.

## Existing database compatibility

Before starting this merge against an older Hibernate-created source database, inspect and apply `migrations/2026-10-23-auth-column.sql` using the normal migration process. It renames the previous `user_hashed_salted_password` column to the seed/auth-stub name `userhashedsaltedpassword` when only the former exists, and adds nullable names for legacy users. If both password columns already exist, reconcile them explicitly before deployment; this migration does not choose between conflicting credential values. New registrations require first and last names. Historical unknown names remain unknown.

Staff permissions are issued by the Java backend from database roles. The customer auth stub cannot assign staff roles. Both providers store SHA-256 token hashes in `access_tokens` and `refresh_tokens`; the backend validates the access record, owning user, expiry and refresh-family revocation on every protected request. Role changes revoke the user's stored token families. Staff session routing uses `/api/admin/session` permissions, and refresh uses the same provider as sign-in.

The normal development proxy forwards customer auth to 4000 and Spring to 8082. `/api/admin`, `/api/analytics` and `/api/etl` retain their prefix; investor APIs strip `/api`. A deployed frontend needs equivalent reverse-proxy routes; Angular's development proxy is not part of the production bundle.

## Container configuration

The backend image now copies its artifact from the Maven build stage, and Compose maps port 8082 to the actual server port. Compose syntax was checked; a Docker image build was not run. Supply `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` and a container-reachable `DB_URL`, and configure a separate `DB_OLAP_URL` with the reporting schema. The verified disposable launcher supplies separate source/reporting databases automatically.

## Business approvals still outstanding

Verification does not approve the small-segment disclosure policy, legal retention policy, BR-18 selection, metric definitions, or production performance/freshness targets. The existing disclosure guard and explicit incomplete/unknown states remain in force.

## Scripted admin / analytics demo

Run `bash scripts/verify-merged-connection.sh --demo` for the richer disposable fixture on http://127.0.0.1:14300. Follow [the presenter guide](admin-analytics-demo-guide.md) for credentials, September 2026 totals, order IDs and case actions. Segment disclosure remains withheld.

The boundary fixture exposed legacy source evidence columns declared as timezone-free timestamps while OLAP uses timezone-aware timestamps. The fresh source schema now uses `TIMESTAMPTZ` for those UTC evidence fields. Existing PostgreSQL databases with the older declarations should apply `migrations/2026-10-23-evidence-utc.sql` through the normal migration process before refreshing analytics; it preserves their declared UTC meaning and leaves already-aware fields unchanged.
