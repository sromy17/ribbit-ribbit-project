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
