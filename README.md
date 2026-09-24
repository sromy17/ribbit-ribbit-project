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

### End-to-end order flow (current sprint)
`POST /api/orders/process` performs:
1. Input validation
2. Account + instrument load
3. Fee calculation
4. Order creation (PENDING)
5. Fund debit/credit
6. Trade execution record creation
7. Order status update (EXECUTED)
8. MyBatis account summary fetch

### Run locally
```bash
mvn spring-boot:run
```

### Test the order API
```bash
curl -X POST http://localhost:8080/api/orders/process \
	-H "Content-Type: application/json" \
	-d '{
		"accountId": 1,
		"instrumentId": 1,
		"side": "BUY",
		"quantity": 10,
		"price": 100.00
	}'
```

### Existing tests
- `GreeterTest` (legacy baseline)
- `OrderProcessingIntegrationTest` (new end-to-end verification)
