# Reporting Metric Contract

This contract is the reporting boundary for the normalized OLAP facts. It uses
UTC half-open time ranges: `from <= event_time < to`.

## Grains

- `order_facts`: one row per accepted or rejected order.
- `fill_facts`: one row per committed fill.
- `lifecycle_event_facts`: one row per immutable lifecycle event.
- `cash_movement_facts`: one row per cash movement.
- `holding_movement_facts`: one row per holding movement.
- `accounts_info`: account dimension/current account snapshot.
- Instrument and approved client-segment dimensions should be added before
  segment reporting is exposed.

Do not join all one-to-many facts into one aggregate row. Aggregate each grain
first, then join by stable identifiers.

## Metric rules

### Submitted and accepted orders

- Submitted orders count `ORDER_SUBMITTED` lifecycle events.
- Accepted orders count distinct orders with `ORDER_ACCEPTED` events.
- Validation failures before an order is accepted are not accepted orders.
- A rejected or cancelled order may still count as accepted if it was accepted
  before that later outcome.

### Execution and fills

- Executed orders count distinct orders with at least one committed fill.
- Fill count counts distinct `executeid` values.
- Executed quantity sums `fill_facts.executed_quantity`.
- Requested quantity comes from `order_facts.quantity_requested`.
- Partial fills remain multiple fill rows and do not become a fabricated full
  execution.

### Rejection rate

Acceptance-cohort rejection rate is:

```text
orders later rejected / orders accepted in the selected acceptance period
```

The denominator is not all submitted requests. A zero denominator is reported
as unavailable, not zero.

### Active clients

Active clients are distinct approved client identifiers with at least one fill
in the selected period. Multiple accounts belonging to one client count once.
Do not use usernames as the stable reporting identifier.

### Currency

All monetary measures are grouped by currency. USD, GBP, INR, and other
currencies must not be summed into one total without an approved conversion
rate source, rate timestamp, and rounding policy.

### Client segments

Account type is not a client segment. Segment reporting remains unavailable
until an approved segment dimension with effective dates exists. Historical
reports use the segment effective at the event time; unknown or missing
classification is emitted as `Unknown`.

### Freshness

Every report response must identify:

- selected period and timezone,
- filter values,
- dataset run ID,
- source watermark,
- publication time,
- refresh status,
- stale or incomplete state.

A failed refresh retains the previous successful dataset and is not represented
as an empty or zero-valued report.
