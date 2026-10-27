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
