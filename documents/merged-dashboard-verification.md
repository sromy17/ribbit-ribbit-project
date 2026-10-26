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

## October 26 merge integration

The second datasource/ETL implementation introduced by the merge is now a compatibility entry point to the existing configuration and canonical publisher. There is one JPA/source datasource and a separate OLAP datasource/transaction manager. `DB_OLTP_URL/USER/PASSWORD` take precedence over `DB_URL/USER/PASSWORD`; Spring and customer authentication use the same source settings. Existing `ETL_ENABLED` / `ETL_FIXED_DELAY_MS` settings delegate to the atomic, reconciled publisher. An explicit ETL cron supersedes the compatibility fixed-delay schedule. Both `/api/etl/run` and `/etl/run` require server-side ETL permissions and publish the same versioned facts and freshness metadata. Wallet reporting tables publish in that same transaction; bank identifiers retain only their trailing digits in OLAP.

New backend market routes (frontend accesses them through `/api`):

- `GET /market/quotes?symbols=AAPL,MSFT`: authenticated, at most 50 supplied symbols, bounded concurrent work and response wait; only actual available quotes are returned. Missing changes are excluded from heatmap coloring rather than interpreted as zero.
- `GET /market/news?category=general`: authenticated news proxy with server-side `FINNHUB_API_KEY`, bounded cache/concurrency and provider timeouts.
- `GET /market/company-news?symbol=AAPL&from=2026-10-01&to=2026-10-22`: validates symbol and at most a 31-day range.

Live market quotes still depend on Fauxnance configuration/availability. Live news requires a valid `FINNHUB_API_KEY` on Spring; no key is shipped in Angular. News without configuration or during provider failure returns 503 and displays unavailable, rather than synthetic articles. The disposable demo intentionally leaves this key unset. Backend provider success/error behavior is tested against a local fixture server. TradingView is an external chart widget and remains dependent on its provider/network.

New accounts must declare currency. The UI includes a currency selector, the balance API returns currency, and portfolio/allocation labels use it. Older accounts without currency remain UNKNOWN. Financial entity mappings now retain ten decimal places for prices, balances and cost basis, avoiding Hibernate's default two-decimal narrowing. This does not reconstruct previously rounded historical values. Browser observations are explicitly labelled as browser-observed history and cached separately for each signed-in user; source balances, holdings, execution outcomes and permissions remain backend-authoritative.

The investor view styles are scoped to its workspace and imported into the shared Tailwind component layer. This preserves new chart/news/help widgets without exceeding the production component stylesheet budget or applying investor layout rules to admin/analytics.

The connection script also checks registration, explicit-currency account creation, invalid currency omission, cross-account wallet denial, unconfigured-news behavior, invalid batch quotes, precise persisted cost basis, report issuance/retrieval/amendments and token revocation.
