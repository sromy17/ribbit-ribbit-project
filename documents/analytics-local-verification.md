# Local analytics verification — 2026-10-23

Verified with `mvn test`, `bash scripts/verify-admin-postgres.sh`,
`npm test -- --watch=false`, and `NG_BUILD_MAX_WORKERS=2 npm run build`.
60 backend tests and 13 frontend tests passed. The production frontend build passed.
The design preview was inspected for dataset labels, chart/table agreement and
horizontal overflow; sample preview data is clearly labelled and cannot issue reports.

The PostgreSQL suite creates disposable databases and covers independent arithmetic,
permissions, reports/amendments, publication rollback, mixed workload, reporting
failure isolation, restart and financial replay. The four date-query tests include
23-hour and 25-hour local days.

Latest observed workload output:

```text
LOCAL FIXTURE ONLY; stakeholder limits NOT APPROVED
10000 historical orders; 20 sequential HTTP submissions/executions concurrent with 20 analytics service queries and 3 refreshes.
Order end-to-end p50 ms: 55.779791
Order p95 ms: 124.482125
Mixed workload throughput orders/sec: 10.28985669155035
OLTP lock-wait samples: 0 of 146
Source snapshot age after publication ms: 532
Newest fill to final publication lag ms: 566
Isolation: analytics failure returned 503 while an order returned 201. Replay left balances and fill count unchanged.
```

Analytics queries in the background call the protected service's transaction-bound
calculation method directly; customer order placement and the final Operations and
Analyst checks use HTTP. Quotes are deterministic local fixtures. Lock activity is
sampled every 10 ms; this is not a cumulative wait-time measurement. Fill lag is
measured against final publication, not first appearance in an earlier dataset.
Publication time records ETL completion using `clock_timestamp()`.

These results are a local regression baseline. Production capacity, concurrent
submitter load, infrastructure and broker/Kafka behaviour still require a
representative workload and agreed limits. No stakeholder performance, freshness,
metric, segment-source or legal-retention approval is asserted.

Release-hardening verification also passed: simultaneous same-order replay and
cross-account submissions, durable ID counters, executed-order cancellation
rejection, post-rollback EXECUTION_FAILED evidence, orphan evidence rejection,
abandoned-run recovery, publisher exclusion, default segment withholding,
stale dataset-token rejection, fractional quantity rejection, restricted ETL/user
lookup routes, quote ordering/clock skew and evidence-coverage warnings.

Disposable source/reporting backups were restored into separate databases;
financial records, lifecycle evidence, counters, OLAP facts and retained reports
matched row-set checksums. This is a local restore test, not production RPO/RTO
or a legal retention-policy approval. See [the release checklist](analytics-release-checklist.md).
