#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# This script owns its disposable database. It never uses the application's DB_URL.
admin_container="leap-admin-check-$$"
trap 'docker stop "$admin_container" >/dev/null 2>&1 || true' EXIT
docker run --detach --rm --name "$admin_container" \
  -e POSTGRES_PASSWORD=admin-test-only -e POSTGRES_DB=admin_verification \
  -p 127.0.0.1::5432 postgres:17 >/dev/null
for attempt in {1..60}; do
  # The image starts a temporary Unix-socket server during initialization.
  # Wait for TCP so we cannot mistake that server for the final instance.
  if docker exec "$admin_container" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$admin_container" pg_isready -h 127.0.0.1 -U postgres
docker exec "$admin_container" createdb -U postgres admin_reporting
docker exec "$admin_container" createdb -U postgres analytics_verification
docker exec "$admin_container" createdb -U postgres analytics_reporting
admin_port="$(docker port "$admin_container" 5432/tcp | head -1 | awk -F: '{print $NF}')"
ADMIN_PG_URL="jdbc:postgresql://localhost:${admin_port}/admin_verification" \
  mvn -q -Dtest=AdminPostgresTest,AnalyticsPostgresTest test
# Round-trip backups of the disposable source and reporting stores only.
for recovery_kind in source reporting; do
  if [[ "$recovery_kind" == source ]]; then
    recovery_original=analytics_verification
    recovery_tables="users accounts trade_request trades_executed order_lifecycle_events cash_movements holding_movements holdings financial_id_allocations"
  else
    recovery_original=analytics_reporting
    recovery_tables="order_facts fill_facts lifecycle_event_facts cash_movement_facts holding_movement_facts analytics_reports etl_refresh_runs"
  fi
  recovery_database="recovered_${recovery_kind}"
  docker exec "$admin_container" pg_dump -U postgres -Fc -f "/tmp/${recovery_kind}.dump" "$recovery_original"
  docker exec "$admin_container" createdb -U postgres "$recovery_database"
  docker exec "$admin_container" pg_restore -U postgres --no-owner --no-acl -d "$recovery_database" "/tmp/${recovery_kind}.dump"
  for recovery_table in $recovery_tables; do
    recovery_query="SELECT md5(COALESCE(string_agg(row_to_json(t)::text,'' ORDER BY row_to_json(t)::text),'')) FROM ${recovery_table} t"
    recovery_expected="$(docker exec "$admin_container" psql -U postgres -d "$recovery_original" -Atc "$recovery_query")"
    recovery_actual="$(docker exec "$admin_container" psql -U postgres -d "$recovery_database" -Atc "$recovery_query")"
    [[ "$recovery_actual" == "$recovery_expected" ]] || { echo "Backup verification failed for ${recovery_table}" >&2; exit 1; }
  done
done
mkdir -p target/analytics-verification
printf '%s\n' 'Disposable PostgreSQL backup/restore: source financial records, lifecycle evidence, ID counters, OLAP facts and issued reports matched their restored row-set checksums.' > target/analytics-verification/recovery.txt
