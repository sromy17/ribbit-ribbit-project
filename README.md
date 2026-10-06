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
- `POST /login`
- `POST /logout`

### Run locally
```bash
mvn spring-boot:run
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
