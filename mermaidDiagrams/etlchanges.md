# ETL and Holdings Changes

- `src/main/java/com/neueda/leap/Main.java`: enabled Spring scheduling.
- `src/main/java/com/neueda/leap/trading/domain/Holding.java`: aligned holdings persistence with `basedSchema.sql` and added the last-changed date.
- `src/main/java/com/neueda/leap/trading/service/impl/TradingOrderService.java`: applied holdings changes after an order becomes executed, deleting zero-quantity positions transactionally.
- `src/main/java/com/neueda/leap/trading/service/impl/OltpToOlapEtlService.java`: added the transactional full OLTP-to-OLAP load and configurable scheduled execution.
- `src/main/java/com/neueda/leap/trading/api/EtlController.java`: added `POST /api/etl/run` for manual ETL execution.
- `src/main/resources/application.properties`: added the `ETL_SCHEDULE_CRON`-backed scheduler configuration.