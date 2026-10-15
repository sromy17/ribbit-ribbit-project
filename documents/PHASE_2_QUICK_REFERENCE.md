# Phase 2 - Quick Reference Guide

## What Phase 2 Does

**Before Phase 2**:
```
Client submits order
    ↓
Order accepted immediately
    ↓
Order stored in database (PENDING)
    ↓
At execution time: Check cash/holdings ❌ LATE!
```

**After Phase 2** (BR-05 Compliant):
```
Client submits order
    ↓
VALIDATION GATE: Check ALL trading rules ⭐
    ├─ Is account in good standing?
    ├─ Is instrument tradable? (ACTIVE status)
    ├─ Is quantity within limits? (1 to 1M units)
    ├─ Is price within limits? ($0.01 to $1M)
    ├─ For BUY: Sufficient cash? (including fees)
    └─ For SELL: Sufficient holdings?
    ↓
IF PASS: Order accepted → PENDING status ✅
IF FAIL: Order rejected → REJECTED status with reason 📝
    ↓
Order stored in database (with status & rejection reason)
    ↓
Only PENDING orders proceed to execution phase
```

---

## Key Components

### 1. InstrumentStatus Enum
```java
ACTIVE     → Can accept orders ✅
SUSPENDED  → Temporarily blocked (maintenance, etc.)
DELISTED   → Permanently removed from trading
HALTED     → Regulatory halt or circuit breaker
```

### 2. Validation Rules (Enforced at Submission)

| Rule | Min | Max | Checked |
|------|-----|-----|---------|
| Quantity | 1 | 1,000,000 | ✅ |
| Price | $0.01 | $1,000,000 | ✅ |
| BUY Cash | Account balance | N/A | ✅ |
| SELL Holdings | Required qty | N/A | ✅ |
| Instrument Status | ACTIVE | ACTIVE | ✅ |

### 3. Order Lifecycle

```
PENDING ─→ EXECUTED ─→ [Complete]
  ↑
  └─→ REJECTED ─→ [Failed Validation]
  
PENDING ─→ CANCELLED ─→ [User cancelled]
```

### 4. Error Response Example

```
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "Order rejected: Insufficient funds for BUY order. 
            Required: $15,000, Available: $10,000"
}
```

---

## Testing Coverage

### 21 Tests in DefaultTradingRulesServiceTest

**Account Tests** (3):
- null account ❌
- negative balance ❌
- valid account ✅

**Instrument Tests** (3):
- SUSPENDED status ❌
- DELISTED status ❌
- ACTIVE status ✅

**Quantity Tests** (4):
- zero qty ❌
- negative qty ❌
- > 1M qty ❌
- valid qty ✅

**Price Tests** (4):
- zero price ❌
- negative price ❌
- > $1M price ❌
- valid price ✅

**SELL Tests** (3):
- no holdings ❌
- insufficient holdings ❌
- sufficient holdings ✅

**BUY Tests** (2):
- insufficient funds ❌
- sufficient funds ✅

**Integration** (2):
- Multiple failures ❌
- All pass ✅

---

## Integration Points

### TradingOrderService (Updated)

**Before**:
```java
public CreateOrderResponse createOrder(CreateOrderRequest request) {
    // Fetch account & instrument
    // Save order with PENDING status immediately
    // Return success
    // (Validation happens later at execution)
}
```

**After**:
```java
public CreateOrderResponse createOrder(CreateOrderRequest request) {
    // Fetch account & instrument
    
    // ← NEW: Validate trading rules NOW
    ValidationResult validation = tradingRulesService.validateOrderSubmission(
        account, instrument, side, quantity, price
    );
    
    if (!validation.isValid()) {
        // ← NEW: Save order with REJECTED status
        order.setStatus(OrderStatus.REJECTED);
        orderRepository.save(order);
        throw new IllegalArgumentException("Order rejected: " + validation.getReason());
    }
    
    // Save order with PENDING status
    order.setStatus(OrderStatus.PENDING);
    return orderRepository.save(order);
}
```

---

## Configuration

Edit limits in `DefaultTradingRulesService`:

```java
private static final int MAX_ORDER_QUANTITY = 1_000_000;     // Change here
private static final int MIN_ORDER_QUANTITY = 1;              // Or here
private static final BigDecimal MAX_ORDER_PRICE = BigDecimal.valueOf(1_000_000);
private static final BigDecimal MIN_ORDER_PRICE = BigDecimal.ZERO;
```

---

## Examples

### Example 1: Successful BUY Order ✅

```
Request:
  Account: 123 (balance: $50,000)
  Instrument: AAPL (status: ACTIVE)
  Side: BUY
  Quantity: 100
  Price: $150

Validation:
  ✅ Account in good standing
  ✅ Instrument ACTIVE
  ✅ Quantity 100 is between 1-1M
  ✅ Price $150 is between $0.01-$1M
  ✅ Total cost: 100 × $150 + fee = $15,015 < $50,000

Result: Order PENDING → Can execute
```

### Example 2: Rejected BUY - Insufficient Funds ❌

```
Request:
  Account: 123 (balance: $10,000)
  Instrument: AAPL (status: ACTIVE)
  Side: BUY
  Quantity: 100
  Price: $150

Validation:
  ✅ Account in good standing
  ✅ Instrument ACTIVE
  ✅ Quantity 100 is between 1-1M
  ✅ Price $150 is between $0.01-$1M
  ❌ Total cost: 100 × $150 + fee = $15,015 > $10,000

Result: Order REJECTED with reason
  "Insufficient funds for BUY order. Required: $15,015, Available: $10,000"
```

### Example 3: Rejected SELL - No Holdings ❌

```
Request:
  Account: 456 (holdings: NONE)
  Instrument: TSLA (status: ACTIVE)
  Side: SELL
  Quantity: 50
  Price: $200

Validation:
  ✅ Account in good standing
  ✅ Instrument ACTIVE
  ✅ Quantity 50 is between 1-1M
  ✅ Price $200 is between $0.01-$1M
  ❌ No holdings for TSLA

Result: Order REJECTED with reason
  "Insufficient holdings. No position exists for this instrument"
```

### Example 4: Rejected - Instrument Suspended ❌

```
Request:
  Account: 789 (balance: $100,000)
  Instrument: MAINTENANCE (status: SUSPENDED)
  Side: BUY
  Quantity: 10
  Price: $50

Validation:
  ✅ Account in good standing
  ❌ Instrument MAINTENANCE is not ACTIVE

Result: Order REJECTED with reason
  "Instrument MAINTENANCE is not currently tradable. Status: SUSPENDED"
```

---

## Database Changes

Add instrument status tracking:

```sql
-- Add status column to instruments table
ALTER TABLE instruments ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

-- Update existing instruments to ACTIVE (default)
UPDATE instruments SET status = 'ACTIVE';

-- Example: Suspend UBER for maintenance
UPDATE instruments SET status = 'SUSPENDED' WHERE ticker = 'UBER';

-- Example: Delist a stock
UPDATE instruments SET status = 'DELISTED' WHERE ticker = 'DELISTED_STOCK';
```

---

## Compliance Mapping

| BRS Requirement | Phase 2 Implementation |
|-----------------|----------------------|
| BR-05 | ✅ All trading rules validated at submission |
| BR-06 | ✅ Orders recorded with status (PENDING/REJECTED/EXECUTED/CANCELLED) |
| BR-14 | ✅ Rejected orders persisted with rejection reason for audit |
| BR-15 | ✅ Complete lifecycle recorded: REJECTED → PENDING → EXECUTED |

---

## Testing Checklist

Before Phase 1 (Authentication) or Phase 3 (Market Data):

- [ ] `mvn clean compile` - Compiles without errors
- [ ] `mvn clean test` - All 29 tests passing
- [ ] Try placing a BUY order with insufficient cash → Receive rejection error
- [ ] Try placing a SELL order with no holdings → Receive rejection error
- [ ] Try placing order on SUSPENDED instrument → Receive rejection error
- [ ] Try placing valid order → Order saved as PENDING
- [ ] Check database: Orders have status and rejection reasons recorded

---

## Support

**Questions about Phase 2?**
- See `PHASE_2_IMPLEMENTATION.md` for detailed documentation
- Check test cases in `DefaultTradingRulesServiceTest.java` for examples
- Review error messages above for validation details

**Ready for Phase 3 (Market Data)?**
- Phase 2 (order validation) is complete and tested ✅
- Phase 3 will replace static pricing with real market data
- Phase 1 (authentication) can proceed independently when ready

**Ready for Phase 1 (Authentication)?**
- Phase 2 validates trading rules; Phase 1 validates users
- Both can proceed in parallel
- Phase 1 will secure the endpoints and add user context to orders
