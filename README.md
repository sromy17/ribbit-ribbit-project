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
- `GreeterTest` (legacy baseline)
- `OrderProcessingIntegrationTest` (new end-to-end verification)
