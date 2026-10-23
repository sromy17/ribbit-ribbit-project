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
