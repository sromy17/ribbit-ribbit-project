# Enterprise Trading Platform Fidelity

For the persistent local UI, run `bash scripts/start-local.sh` and open **http://127.0.0.1:4200**. See [the local application guide](documents/local-application-guide.md) for separate user/staff logins, pgAdmin, OLTP/OLAP databases, and verification steps. Port 14300 is the disposable integration demo.

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
- Spring Boot 4 (REST + DI), Java 25
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

### Demo market-data integration

For a recordable demo that keeps credentials out of the screen, follow
[documents/POSTMAN_MARKET_DATA_DEMO.md](documents/POSTMAN_MARKET_DATA_DEMO.md).

The Spring service reads quotes from Fauxnance through `FAUXNANCE_BASE_URL` and
publishes refreshed snapshots to the Kafka topic `market.quotes`. Set the API
key before starting the service:

```bash
export FAUXNANCE_API_KEY="your-fauxnance-key"
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
export MARKET_DATA_KAFKA_ENABLED=true
export MARKET_DATA_PRIORITY_POLL_MS=30000
export MARKET_DATA_SECONDARY_POLL_MS=300000
mvn spring-boot:run
```

The configured `MARKET_DATA_PRIORITY_SYMBOLS` list is refreshed every 30
seconds, with at most 25 symbols fetched per cycle. Other instruments are
refreshed every five minutes. An order request still fetches a symbol
synchronously when its cached quote is older than
`MARKET_DATA_QUOTE_CACHE_MAX_AGE_MS`, so less frequently refreshed instruments
remain available on demand.

To observe the integration, consume the quote topic with a Kafka client and
submit an order for a seeded instrument such as `AAPL`. The flow is:

```text
Fauxnance -> priority/secondary refresh -> QuoteCache -> Kafka market.quotes
										   -> order-time cache/fallback
```


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
- Controller tests for user and order endpoints
- Order service and trading-rule validation tests
- Application context and configuration binding smoke test
- `OrderProcessingIntegrationTest` (end-to-end verification)

### Analytics evidence and metric approval

Review the [metric dictionary](documents/reporting-metric-contract.md) with Priya,
Operations and Finance before adding charts. Event-time segmentation is approved;
segment source/values and metric sign-off remain pending.

For an existing PostgreSQL deployment, apply
[OLTP migration](scripts/analytics-oltp-migration.sql) to the trading database and
[OLAP migration](scripts/analytics-olap-migration.sql) to the reporting database
before deploying the updated application/ETL. These migrations are additive,
except for removing ticker-only uniqueness and allowing explicitly unknown
timestamps/currency. They do not backfill historical data. Do not use the root
schema initializers to upgrade a live database.

Curate instrument conventions and account currency from trustworthy sources.
New fills snapshot execution/fee currency, quantity unit, multiplier, product
convention and client identity; missing metadata remains unknown. Historical
cash labels without `currency_source` are unverified. Registration time is
recorded only for real new registrations, never inferred for legacy clients.
Order requests accept `instrumentId`; ticker-only requests remain compatible
when unambiguous. Repeated tickers require ID-based orders and qualified
`market_data_symbol` mappings. Reporting joins use stable IDs.

### VS Code reports missing Java types while Maven passes

Run **Java: Reload Projects** from the Command Palette to refresh the Maven
project model. The workspace automatically refreshes Java build configuration
when build files change. If diagnostics persist, run `mvn test` from the repository
root to distinguish source failures from stale editor diagnostics.

### Commercial analytics

The analyst dashboard now uses the protected `/api/analytics` routes and OLAP only.
Apply `scripts/analytics-reporting-migration.sql` to the reporting database before
deployment and run ETL. See [analytics implementation and verification](documents/analytics-implementation.md)
for endpoint parameters, report amendments, limits and local mixed-load measurements.
Metrics and segment-source approval remain pending; screens label the definitions PROPOSED.

Segment analysis is withheld by default because no disclosure policy is approved.
See the [release checklist](documents/analytics-release-checklist.md) for gates,
new regression coverage, durable ID counters and backup/restore verification.

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
For a new deployment, keep `POSTGRES_DB=ribbit-oltp` and `POSTGRES_OLAP_DB=ribbit-olap`; the latter is a separate
analytics database. For an existing deployment, preserve the actual OLTP database
name in `POSTGRES_DB` and set `POSTGRES_OLAP_DB` to the existing reporting database name
before starting. Host-side `DB_*_URL` and `SERVER_PORT` values are overridden
inside containers.

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
The frontend uses a production Angular build. Its component stylesheet budgets
are 22 kB for warnings and 24 kB for errors, retaining the dashboard redesign's
limits rather than the legacy stylesheet's 36 kB error allowance.

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

### 4. Test login and trading

1. Open `http://localhost:4200` and register a new test user, then sign in.
   Do not assume the seeded database users have a known login password.
2. Create a trading account and fund it using the account/wallet UI.
3. Open Trade and select AMD.
4. Buy a small quantity you can afford and confirm the order is executed in
   History. A pending or rejected order does not increase owned shares.
5. Check Portfolio to confirm the executed holding and quantity.
6. Refresh the browser and verify the holding is reloaded for the same account.

The order form includes Your position from the loaded account holdings. Missing holdings or quotes show an unavailable/loading state instead of invented values.

### 5. Inspect failures and stop safely

```bash
docker compose logs --tail=100 postgres springboot auth frontend
docker compose down
```

`down` preserves the database volume. Do not add `--volumes` unless you
intentionally want to delete all users, accounts, and trades. Initialization
SQL runs only when the PostgreSQL data volume is empty; changing a password
in `.env` does not update the password stored in an existing database.

For an isolated clean test without deleting your normal database, stop the
normal stack and use a separate project/volume:

```bash
docker compose -p ribbit-smoketest up --build --wait --wait-timeout 240
# Run the checks above, then stop the isolated stack:
docker compose -p ribbit-smoketest down
```

Explicit container names and published ports mean these two stacks cannot run
simultaneously. If an older volume lacks its configured reporting database, use the isolated test
first; do not rerun the destructive schema scripts against existing data.
If `docker info` fails on the VM, check Docker Engine there. On a systemd-based
VM, use `sudo systemctl status docker`; if it is stopped, start it with
`sudo systemctl start docker`. A permission-denied error may require running
the Docker commands with `sudo`. The local Windows Docker engine is not used.


### Conserving the quote API quota

Display quotes are cached per symbol for one hour on the backend, shared across
users and tabs. Concurrent requests for a symbol share its refresh. The heatmap
automatically refreshes hourly and requests at most 50 symbols. These display
quotes can therefore be up to one hour old; execution uses its separate freshness
check and never falls back to an old display price.

All Fauxnance requests (including execution, validation and Kafka polling) pass
through a 1,000-attempt daily cap, controlled by `FAUXNANCE_DAILY_REQUEST_LIMIT`.
The count is saved in `state/fauxnance-budget.properties` before each attempt and
resets at midnight UTC. Keep this file across restarts and mount persistent state
when running in a container. This budget covers one backend instance; other apps
or instances using the same API key consume the provider quota separately. Calls
made before enabling the budget are not counted. Leave Kafka polling disabled
for conservative local use; enabling its frequent polling can consume the cap
quickly. When the cap is reached, new quotes and execution checks fail explicitly.
