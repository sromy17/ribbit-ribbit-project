# Enterprise Trading Platform Fidelity
* Shravan Romy
* Sukie Zhang
* Naga Ritvika Yeyuvuri
* Krish Sharma
* Matthew Chai

## Branching Strategy
* Gitflow
* Reason: Since we will be working at the same time, and working on different parts of the project, we believe that a non-linear gitflow style will be ideal.


## Team Norms

## Sprint 1 Backend Baseline

This repository now includes a Spring Boot backend skeleton for one-order processing end-to-end.

### Tech stack in code
- Spring Boot 3 (REST + DI)
- JPA/Hibernate for CRUD persistence of core entities
- MyBatis for a complex aggregate query (account/order summary)
- PostgreSQL (runtime) and H2 (default/test profile)

### Implemented domain baseline
- User, TradingAccount, Instrument, Order, Trade POJOs (JPA entities)
- Holding POJO with position update method
- Service interfaces and implementations:
	- OrderService / TradingOrderService
	- AccountService / TradingAccountService
	- FeeCalculator / InstrumentFeeCalculator
	- OrderValidator + ValidationResult
	- OrderProcessingEngine

### Contract-aligned endpoint surface
- `POST /users`
- `PUT /users/{userId}`
- `POST /accounts`
- `GET /accounts/{accountId}`
- `PUT /accounts/{accountId}`
- `DELETE /accounts/{accountId}`
- `GET /accounts/{accountId}/balance`
- `PUT /accounts/{accountId}/balance`
- `GET /accounts/{accountId}/holdings`
- `GET /accounts/{accountId}/orders`
- `POST /accounts/{accountId}/orders`
- `GET /accounts/{accountId}/trades`
- `DELETE /accounts/{accountId}/orders/{orderId}/status`

### External auth-stub endpoints (Node service)
- `POST /register`
- `POST /login`
- `POST /logout`

### Stock order screen

The stock buy/sell screen shows the selected account's shares owned and their
current value (shares multiplied by the displayed market quote). These figures
describe the existing position, not the proposed order. Unavailable holdings or
quotes are labeled rather than displayed as zero.

On the Graphs screen, Trade This Stock opens a buy/sell panel beside the stock
chart inside the same details window, rather than a second popup over the chart.
On narrow screens the panel stacks below the chart with scrolling. Closing the
panel leaves the chart open; completed orders keep their existing status notice.

The Wallet reloads connected bank accounts and recent activity when the
dashboard loads or is refreshed. Adding a new bank account is not required
to retrieve existing connections. Wallet loading failures appear on the Wallet
page and can be retried with the dashboard refresh action.

Wallet routing and account number inputs accept digits only, preserving leading
zeros. Routing numbers require 9 digits; account numbers require 4 to 17 digits.
New profile usernames must be valid email addresses. Sign-in still accepts
existing non-email usernames.

### Admin and analyst dashboard integration

The staff dashboards selectively adopt the workspace/header, section navigation,
account lookup/history, and analytics overview structure from the integration
copy. Dashboard layouts remain scoped. The subsequent app-wide theme update
reuses that checkout's Tailwind v4/PostCSS setup with Everforest colors, without
importing its unrelated layouts or backend changes.

- **Admin:** look up accounts by user ID, select an account, filter/paginate its
  orders, inspect recorded order fields, and view orders, executed trades,
  holdings, or wallet deposits/withdrawals. These views use only the existing
  `/accounts/user/{userId}` and `/accounts/{accountId}/...` GET endpoints.
  The current API cannot confirm a user's profile/existence from an empty
  account result. Cash balances are those returned by the account lookup;
  refresh the lookup to update them. Retrieval times are browser request
  completion times, not published dataset timestamps.
- **Analyst:** platform-wide investor activity from the existing OLAP tables,
  top stocks by executed shares, requested-order-size statistics, customer-service
  aggregates, and an on-demand ETL refresh. See the metric definitions below.
- **Excluded:** lifecycle/settlement evidence, durable dataset provenance/versioning,
  custom date/timezone analysis, internal-activity heatmaps, client segmentation, and saved/issued
  reports. These depend on source backend or database changes.

The dashboard integration does not include Kafka, producers/consumers, or new
reporting tables. The analyst follow-up adds a read-only analytics endpoint and
reuses the existing ETL endpoint. The separate employee-authentication
follow-up adds employee session tables and the login flow described below.
The `/admin-dashboard` and `/analyst-dashboard` routes retain their role guards.

### Tailwind and Everforest light/dark themes

The Angular frontend uses Tailwind CSS v4 through `@tailwindcss/postcss`, matching
the integration checkout's setup. Everforest tokens in
[`frontend/src/styles.css`](frontend/src/styles.css) power both semantic Tailwind
utilities (`tw:bg-canvas`, `tw:bg-panel`, `tw:text-ink`, `tw:text-muted`, `tw:border-line`,
`tw:bg-brand`, `tw:text-brand-ink`) and existing component CSS variables. Use these
tokens instead of hard-coded dark colors. Tailwind scans Angular templates and
inline component templates in `src/app`; keep utility names statically written.
The `tw:dark:` variant follows the `theme-dark` class rather than the OS theme.
Utilities use the `tw:` prefix to avoid collisions with established classes such
as `container`, `grid` or `hidden`; variants follow the prefix (`tw:sm:p-7`).

Use the **sun/moon icon** on login/registration/account forms or in the top-right
workspace header, before account information and actions. The icon shows the
theme you can switch to; its tooltip and accessible label describe the action.
Everforest dark remains the default; a selected mode is
saved as `localStorage.theme` and restored across reloads, navigation and sign-out.
This preference is local to the browser and does not change account data.
Blocked browser storage is logged; switching still works for the current page.

Tailwind's theme and utilities layers are enabled app-wide. Its Preflight reset
is intentionally omitted to preserve the established forms, headings, dialogs
and trading layouts while migrating incrementally. Existing component CSS is
retained; Tailwind is actively used by shared theme controls and workspace layout.
Analyst/portfolio charts, heatmap tiles, Help and trade panels use theme tokens.
The embedded TradingView chart follows light/dark mode via its supported vendor
themes; a cross-origin chart cannot be fully restyled with our CSS.

After pulling this frontend change, run `npm ci` in `frontend` and restart the
Angular dev server, or rebuild the frontend image on the Linux VM. No backend
changes or database migrations are needed. Check both themes on employee and
investor login, registration, account selection/creation, and all three dashboards;
reload and navigate between pages to confirm the preference persists. On mobile,
long forms/graphs/tables remain scrollable. Run `npm test -- --watch=false` and
`npm run build` in `frontend` for regression/build validation.

### Portfolio balance history

The portfolio graph uses browser-local account snapshots, not a server-side
historical market-price series. Each recorded value is cash plus held shares
valued at available quotes, with an explicit notice when cost basis is used.
The last observed value is carried to the selected range's start/end when
available; nothing is invented before the first observation. Connected points
are not evidence of intraday prices. Deposits, withdrawals and fees affect
balance changes, so the dollar/percentage summary is **not investment return**.
A zero starting balance has no defined percentage change and displays `N/A`.

Ranges use local calendar dates; month/year subtraction clamps to the target
month's last day. Equal-time observations use the last snapshot, future
observations are excluded, and snapshots are plotted chronologically. Focus,
tap or hover on a point for its value/change since range start.

The chart uses the [Everforest palette](https://everforest.vercel.app/palette):
medium-contrast light/dark backgrounds and foregrounds, light green `#8da101`
and red `#f85552`, dark green `#a7c080` and red `#e67e80`. The line and gradient
use the same direction color; axes, grid and tooltip follow the active theme.

### Employee login (admin and analyst)

Open `/employee-login` at the same address as the app, for example
`http://localhost:4200/employee-login` through your SSH tunnel. The page uses
the investor login styling but has **no account-creation button**. A link from
the investor login opens it. Investor registration still creates only investor
records in `users`; there is no employee-registration endpoint.

Employees are provisioned in the OLTP `employees` table. Login uses bcrypt
password hashes and accepts only `ADMIN` and `ANALYST`. The database role
determines the destination; the browser does not choose or submit a staff role:

| Demo username | Demo password | Destination |
| --- | --- | --- |
| `admin_user` | `AdminDemo123!` | `/admin-dashboard` |
| `analyst_user` | `AnalystDemo123!` | `/analyst-dashboard` |

These are public **development/demo credentials**, not production secrets.
Before exposing the app publicly, replace both password hashes with bcrypt
hashes of unique private passwords. Existing custom passwords are not overwritten
by the upgrade script. Support/compliance/dispute employees are not granted
dashboard login by this change.

Employee JWTs carry an employee ID and role, not an investor user ID.
Employee access/refresh tokens are hashed in separate session tables. Refresh
rotates both tokens; sign-out revokes the employee session. The Spring service
checks the stored session and current employee role. Admin API access is limited
to the existing read-only account/history endpoints above and the ticket review
API below; analyst sessions do not gain investor/account or ticket-review API access.
Changing an employee's database role
invalidates their old role token. Investor authentication remains unchanged.

#### Update an existing Linux VM without resetting investor data

After transferring the updated source, run these from the VM repository root.
Use `docker-compose` instead of `docker compose` if your VM has the legacy CLI.

```bash
# Start the existing database without deleting its volume.
docker compose up -d postgres

# Add employee session tables and provision the demo employees.
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < scripts/employee-auth.sql

# Rebuild all three changed application images.
docker compose up -d --build auth springboot frontend
docker compose ps
```

Do **not** rerun `basedSchema.sql` or the whole `insert-data.sql` on an existing
deployment: they are fresh-install scripts and the schema drops tables.
`scripts/employee-auth.sql` is the non-destructive, transactional upgrade.
Fresh empty-volume installations use the updated schema and seeds automatically.
No OLAP changes or volume deletion are required.

Verify both employee logins redirect to the correct dashboard, refresh the
page to verify the session persists, then sign out. Confirm employee login has
no create-account button, an investor's credentials fail there, and investor
registration/sign-in still work at `/login`. Sign in as an analyst and follow
the reporting checks below to verify real analytics.

### Analyst reporting and manual ETL

Sign in at `/employee-login` with an `ANALYST` employee, then open
`/analyst-dashboard`. This is **platform investor activity**, not
trading by employees or external exchange sentiment. No sample rankings or
success-shaped fallback data are shown.

- **Refresh reporting data (OLTP to OLAP)** calls `POST /api/etl/run`.
  This runs the existing ETL immediately, replacing only the reporting copy.
  It does not modify OLTP orders, balances, or tickets. `ETL_ENABLED=true`
  continues to enable startup/scheduled refresh; manual refresh works independently
  of that flag. Keep it enabled if you want automatic updates.
- **Reload analytics** calls `GET /api/analyst/overview` without running ETL.
  Both backend routes (without the proxy's `/api` prefix) require a validated
  employee analyst session. Admin and investor sessions cannot call them.
- **Graph/table period:** `ALL` (default), `LAST_30_DAYS`, or `LAST_90_DAYS`,
  passed as the overview's `period` query parameter. Unknown values return 400.
  The six visual insight sections share this filter; existing all-time overview,
  order-size and support metrics remain explicitly all-time.
  Date boundaries are inclusive UTC calendar dates, through today. Weeks start
  Monday; first/last weeks can be partial. Gaps are filled with zeros for activity,
  but undefined averages/rates stay N/A. No future-dated events enter these insights.
- Refreshes are serialized with a database transaction lock, including
  scheduled/manual overlaps. Reporting reads share the lock so every response
  describes one committed copy. Source reads use a repeatable-read snapshot;
  failed refreshes roll back the OLAP replacement and preserve the previous copy.
  Multiple dispute/cancellation/rejection records cannot multiply executed shares.
- **Top stocks:** top ten by summed executed shares, then submitted-order count,
  then ticker for stable ties; excludes stocks with no executions. Buy/sell
  shares, all submitted orders, and distinct clients are shown per symbol.
- **Funding:** weekly deposit/withdrawal bars and a net-deposit line, with exact
  tables. Net deposits are recorded deposits minus withdrawals, not revenue,
  profit or assets under management. Deposit statistics use transfer amounts:
  mean/median, counts, distinct depositing user IDs across accounts, and size bands
  under $100 / $100 to under $1,000 / $1,000 to under $10,000 /
  $10,000 to under $100,000 / $100,000 and over. Weekly distinct-client counts
  must not be summed to get period-wide clients.
- **Stock graphs:** buy/sell executed-share stacked bars and a weekly heatmap of
  submitted orders plus distinct clients, for the same selected period's top ten.
  This is trading popularity, not market price or investment performance.
  Executed quantities on selected orders include all their recorded fills;
  they cannot be accurately allocated to individual fill days with this schema.
- **Order outcomes:** bars/table for mutually exclusive rejected, cancelled,
  fully filled, partially filled, and unfilled orders. Rejected/cancelled status
  takes precedence even if some shares executed. Other orders with executed
  quantity at least the positive requested quantity are fully filled; nonzero
  execution below that is partial. Share-fill rate sums executed/requested shares,
  across all selected orders, and is not capped to conceal source inconsistencies.
  This is the current state of selected orders, not historical weekly status.
- **Growth/participation:** new-account bars, trading-account trend and a separate
  percentage trend, plus exact weekly values. Period participation is distinct
  accounts submitting orders divided by accounts created before period end;
  weekly denominators use accounts created by that week's end. These use the
  current account roster, not historical closures, retention or unique people.
  No bank details, individual customer names or account numbers are exposed.
- **Mean/median/modes:** requested share quantity per order across every status
  and symbol. All tied modes are returned; all-unique sizes show no repeated mode.
  Median averages the middle two sizes for even-sized datasets. Empty datasets
  have no mean, median, or execution rate.
- **Execution participation:** orders with any executed shares divided by all
  submitted orders, including partial executions. This is not a share-fill rate.
  Active clients/accounts have at least one submitted order in the copy.
  Available cash is summed across all accounts and excludes holdings value.
- **Recent activity:** latest 30 active UTC order-submission dates, not necessarily
  30 consecutive days. Executed shares are grouped by order date, not fill date.
- **Customer service:** total/open support tickets and orders with disputes.
  Disputes count orders, not every historical claim: ETL keeps one claim per order,
  preferring an open claim, otherwise the highest dispute ID. Admin ticket review
  and resolution are unchanged.

The existing schema needs **no migration** for these features. Latest order-row
sync time is shown separately from retrieval time and event coverage; it is not
a durable ETL completion/source-cutoff marker. An empty fact table has no sync
timestamp, even after a successful refresh. Dollar traded volume/P&L are deliberately
omitted: the existing reporting price is not a weighted multi-fill notional.

#### Verify on the Linux VM

Rebuild/recreate `springboot` and `frontend` using your existing Compose CLI;
do not reset the volume or rerun the fresh-install schema/seed scripts.

1. Sign in as the analyst. Click **Refresh reporting data (OLTP to OLAP)**;
   wait for the committed-success message and the updated report. A missing
   OLAP schema or connection problem must show an error, not fake zeros.
2. Compare orders, executed shares and rankings with known investor orders.
   Partial/multiple fills contribute their executed quantities once, even
   when an order has multiple dispute records.
3. Submit an investor order in a separate session, then refresh as the analyst.
   Submitted-order totals change; executed-share rankings change only when
   recorded executions change. Reload alone does not run ETL (a scheduled
   refresh may independently update the copy).
4. Confirm admin/investor sessions cannot open the analyst dashboard, and admin
   ticket review still works.
5. Check all six visual insight sections and expand their exact-value tables.
   Change to 30/90 days: graphs/tables should share the new displayed UTC range,
   while the all-time overview stays unchanged. Empty periods must not retain
   old rankings; undefined rates/averages should display N/A.
6. Make a known deposit/withdrawal as an investor, refresh reporting as the
   analyst, and compare the funding totals and weekly values. Negative net
   deposits should appear below zero on the line chart. Changing the graph period
   reloads OLAP analytics only, never triggers ETL.

For automated regression checks:

```bash
cd frontend
npm test -- --watch=false --include=src/app/features/dashboard/analyst/analyst-dashboard.component.spec.ts --include=src/app/features/dashboard/analyst/analytics-chart.component.spec.ts
cd ..
mvn -B -Dtest=AnalyticsControllerTest,EmployeeAuthenticationTest,OlapDataSourceConfigurationTest test
# Optional PostgreSQL test: provide a DISPOSABLE cluster admin URL with CREATE DATABASE rights.
# The test creates/drops two uniquely named databases; never point this at production.
ANALYTICS_TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:55439/postgres?user=postgres' \
  mvn -B -Dtest=AnalyticsIntegrationTest test
```

#### Authentication regression tests

```bash
cd shared/auth-stub
TEST_DATABASE_URL=postgresql://USER:PASSWORD@localhost:5432/TEST_DB node --test employee-auth.test.js
```

Use a disposable PostgreSQL test database, not the deployment database. Tests
create/drop a unique isolated schema, exercise fresh seeds and the repeatable
upgrade, and verify employee login, role claims, refresh/reuse/expiry, and logout.
Frontend login/service/dashboard specs and the Spring
`EmployeeAuthenticationTest` cover routing, investor isolation, and API access.

### Order disputes and account support tickets

- **Investor History:** each order has a small **Dispute** button. Its popup
  submits the selected order, a category (`Fraud`, `Accident`, or
  `Incorrect transfer`), and a description. The account's dispute list below
  the order table shows persisted statuses and assigned employees.
- **Investor Help:** **Contact support** opens the same style of popup for
  general/account-wide issues outside a specific order. Existing FAQs remain
  available, with the account's support-ticket list below the form.
- **Admin > Tickets:** switch between order disputes and general support,
  filter status, inspect every returned ticket and its customer/account/order
  details, and use **View account history** to open that specific account.
  **Mark under review** records `UNDER-REVIEW` for disputes or `IN_PROGRESS`
  for support. **Resolve ticket** closes it as `COMPLETED` or `RESOLVED`.
  The acting admin is recorded as the assigned employee. Unresolved claims can
  remain open or under review; closed tickets cannot be reopened in this UI.

Resolution is ticket-only: it does **not** cancel/reverse trades, refund money,
or change holdings/balances. Descriptions are limited to **75 characters** to
match the existing schema; do not enter passwords or bank details.
Only one open dispute per order is permitted. Concurrent submissions are
serialized, and admin updates require the previously displayed status so a
stale page cannot overwrite another admin's change.

This feature reuses `disputed_orders` and `support_ticket` exactly as defined
in `basedSchema.sql`. **No ticket schema migration, reseeding, OLAP change, or
database reset is needed.** The current dispute table has no submission or
completion timestamp; the UI states that rather than inventing dates.
Support tickets retain their existing creation/closure timestamps.
Restart Spring Boot and rebuild/restart the frontend after deploying this change.
For Compose, rebuild `springboot` and `frontend` while keeping the existing volume.

| Endpoint | Access |
| --- | --- |
| `POST /accounts/{accountId}/orders/{orderId}/disputes` | Authenticated owner of that account and order |
| `POST /accounts/{accountId}/support-tickets` | Authenticated account owner |
| `GET /accounts/{accountId}/tickets/{kind}` | Authenticated account owner |
| `GET /admin/tickets/{kind}` | Admin employee only |
| `PATCH /admin/tickets/{kind}/{ticketId}` | Admin employee only; body contains `status` and `expectedStatus` |

`kind` is `dispute` or `support`. The frontend prefixes these endpoints with
`/api`; the existing proxy strips that prefix. Employee IDs and investor user
IDs are never interchangeable, and investor/analyst tokens cannot review or
resolve other customers' tickets.

For PostgreSQL persistence/concurrency regression tests, set
`TICKET_TEST_DATABASE_URL` to a **disposable** JDBC database URL, then run:

```bash
mvn -Dtest=TicketControllerTest,TicketServiceIntegrationTest,EmployeeAuthenticationTest,OlapDataSourceConfigurationTest test
```

The PostgreSQL test creates/drops its own unique schema, loads the current
schema/seeds there, and verifies ownership, duplicate submission, status
conflicts, assignment, closure timestamps, and unchanged financial records.
Without that environment variable, the PostgreSQL-only test is skipped.

### Run locally

1. Start the Node auth-stub

```bash
cd shared/auth-stub
npm install
npm start
```

2. Start the Spring trading service

```bash
mvn spring-boot:run
```

Both services must share the same `JWT_SHARED_SECRET` value.

The primary datasource and default `transactionManager` serve OLTP JPA operations
(accounts, wallets, and orders). The ETL uses its separate OLAP datasource and
explicitly selects `olapTransactionManager`; it must not replace the default JPA
transaction manager.

### Test account order creation
```bash
curl -X POST http://localhost:8081/accounts/1/orders \
	-H "Content-Type: application/json" \
	-d '{
		"instrumentId": 1,
		"side": "BUY",
		"quantity": 10,
		"price": 100.00
	}'
```

### Existing tests
- `GreeterTest` (legacy baseline)
- `OrderProcessingIntegrationTest` (new end-to-end verification)

## Run and test the full app with Docker Compose

Run these commands in a shell on the Linux VM, from the repository root.
Docker Engine and the Docker Compose plugin must be installed and running on
the VM; Docker Desktop is not required on your computer. Use `sudo docker`
instead of `docker` if your VM account does not have Docker permissions.
Check for existing services using ports 4200, 8081, or 5432 before starting.

The VM needs the updated project, not just `docker-compose.yml`: the stack
builds images from the backend source, `pom.xml`, root `Dockerfile`,
`frontend/`, and `shared/auth-stub/`. It also mounts `basedSchema.sql`,
`insert-data.sql`, `olap.sql`, and `scripts/init-olap.sql`. Transfer these changes
or pull them after they have been committed and pushed. Keep the VM's `.env`
private and do not overwrite existing database credentials.
For an existing deployment, keep the same project directory and Compose
project name so Compose reuses the correct database volume.

### 1. Configure the environment

If you do not already have `.env`, copy the example without overwriting an
existing configuration:

```bash
if [ ! -f .env ]; then cp .env.example .env; fi
```

In `.env`, set `POSTGRES_PASSWORD` and a `JWT_SHARED_SECRET` of at least 32
characters. Both application services receive the same secret and database
credentials from Compose. Set a valid `FAUXNANCE_API_KEY` to test live quotes
and trades. The default `FAUXNANCE_USE_ORDER_PRICE_FALLBACK=false` intentionally
fails trades when the provider is unavailable rather than using a fake price.
Keep `POSTGRES_DB=ribbit_oltp`; `ribbit_olap` is a separate analytics database.
Host-side `DB_*_URL` and `SERVER_PORT` values are overridden inside containers.

### 2. Validate and start

```bash
docker info
docker compose config --quiet
docker compose up --build --wait --wait-timeout 240
docker compose ps
```

The first build downloads dependencies and may take several minutes. All four
services (`postgres`, `springboot`, `auth`, `frontend`) should be healthy.
The backend JAR is built inside Docker; no local Maven build is required.
The frontend uses a production Angular build. Its component stylesheet error
budget is 36 kB to accommodate the existing roughly 34 kB investor dashboard;
the 4 kB warning remains so stylesheet growth is still visible.

### 3. Check HTTP routing

```bash
curl -fsS http://localhost:4200/health
curl -fsS http://localhost:4200/auth/health
curl -fsS http://localhost:4200/api/actuator/health
```

Expect `ok`, an auth response with `status: ok`, and a backend response with
`status: UP`, respectively. These checks cover Nginx and both proxy routes.
The backend is also available directly at `http://localhost:8081`.
Here `localhost` means the VM, because the checks run in its shell.

To open the UI from your computer, use an SSH tunnel (run this on your computer,
replacing `USER` and `VM_HOST` with your SSH login and VM address):

```bash
ssh -L 4200:127.0.0.1:4200 USER@VM_HOST
```

Keep that SSH session open, then browse to `http://localhost:4200` on your
computer. If local port 4200 is busy, use `-L 4201:127.0.0.1:4200` and browse
to `http://localhost:4201` instead. Alternatively, use `http://VM_HOST:4200`
only if your VM networking/firewall allows access; no firewall changes are
needed for the SSH tunnel.

### 4. Test login, trading, and the position summary

1. Open `http://localhost:4200` and register a new test user, then sign in.
   Do not assume the seeded database users have a known login password.
2. Create a trading account and fund it using the account/wallet UI.
3. Open Trade and select AMD. Before owning it, Your position should show
   0 shares and $0.00.
4. Buy a small quantity you can afford and confirm the order is executed in
   History. A pending or rejected order does not increase owned shares.
5. Reopen AMD. Shares owned should match Portfolio, and Current value should
   equal owned shares times the price displayed in the order screen, rounded
   to cents. Changing the proposed order quantity must not change your position.
6. Refresh the browser, then reopen AMD to verify the position is reloaded.
   Check another unowned ticker or another account to verify holdings are not
   carried over. If pricing fails, held shares remain visible and their current
   value says Price unavailable rather than zero.

### 5. Inspect failures and stop safely

```bash
docker compose logs --tail=100 postgres springboot auth frontend
docker compose down
```

`down` preserves the database volume. Do not add `--volumes` unless you
intentionally want to delete all users, accounts, and trades. Initialization
SQL runs only when the PostgreSQL data volume is empty; changing a password
in `.env` does not update the password stored in an existing database.

For an explicitly approved clean reset, preserve a database backup outside
Docker, transfer the latest initialization SQL to the VM, and set
`POSTGRES_DB=ribbit_oltp` and `ETL_ENABLED=true` in `.env`. Run
`docker compose down --volumes` to delete this Compose project's database
volume, then `docker compose up -d postgres`. Check
`docker compose logs --tail=150 postgres` for successful completion of all
initialization scripts before starting the rest of the stack. If any SQL script
fails, stop and fix it: restarting PostgreSQL alone does not rerun initialization
against the partially initialized volume.

For an isolated clean test without deleting your normal database, stop the
normal stack and use a separate project/volume:

```bash
docker compose -p ribbit-smoketest up --build --wait --wait-timeout 240
# Run the checks above, then stop the isolated stack:
docker compose -p ribbit-smoketest down
```

Explicit container names and published ports mean these two stacks cannot run
simultaneously. If an older volume lacks `ribbit_olap`, use the isolated test
first; do not rerun the destructive schema scripts against existing data.
If `docker info` fails on the VM, check Docker Engine there. On a systemd-based
VM, use `sudo systemctl status docker`; if it is stopped, start it with
`sudo systemctl start docker`. A permission-denied error may require running
the Docker commands with `sudo`. The local Windows Docker engine is not used.
