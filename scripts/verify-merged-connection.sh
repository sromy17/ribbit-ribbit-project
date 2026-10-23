#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Isolated source/OLAP databases and ports; never reads the project .env.
connection_container="leap-connection-check-$$"
connection_temp="$(mktemp -d)"
connection_backend=""; connection_auth=""; connection_frontend=""
cleanup() {
  echo "Connection verification logs: $connection_temp"
  for connection_pid in "$connection_frontend" "$connection_auth" "$connection_backend"; do
    if [[ -n "$connection_pid" ]]; then kill "$connection_pid" 2>/dev/null || true; fi
  done
  docker stop "$connection_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker run --detach --rm --name "$connection_container" -e POSTGRES_PASSWORD=connection-test-only -e POSTGRES_DB=connection_source -p 127.0.0.1::5432 postgres:17 >/dev/null
for attempt in {1..60}; do
  if docker exec "$connection_container" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
connection_port="$(docker port "$connection_container" 5432/tcp | head -1 | awk -F: '{print $NF}')"
docker exec "$connection_container" createdb -U postgres connection_reporting
docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source < basedSchema.sql > "$connection_temp/schema.log" 2>&1
docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source < insert-data.sql >> "$connection_temp/schema.log" 2>&1
docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_reporting < olap.sql >> "$connection_temp/schema.log" 2>&1
docker exec "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source -c "INSERT INTO roles(rolename) VALUES('PLATFORM_ADMINISTRATOR'),('ANALYST'); INSERT INTO user_roles(userid,roleid) SELECT 1,roleid FROM roles WHERE rolename='PLATFORM_ADMINISTRATOR'; INSERT INTO user_roles(userid,roleid) SELECT 2,roleid FROM roles WHERE rolename='ANALYST';" >/dev/null
mvn -q package -DskipTests > "$connection_temp/package.log" 2>&1
DB_URL="jdbc:postgresql://127.0.0.1:$connection_port/connection_source" DB_USER=postgres DB_PASSWORD=connection-test-only DB_OLAP_URL="jdbc:postgresql://127.0.0.1:$connection_port/connection_reporting" SERVER_PORT=18082 ORDER_EXECUTION_KAFKA_ENABLED=false java -jar target/team-skeleton.jar > "$connection_temp/backend.log" 2>&1 &
connection_backend=$!
DB_URL="postgresql://127.0.0.1:$connection_port/connection_source" DB_USER=postgres DB_PASSWORD=connection-test-only JWT_SHARED_SECRET=please-change-this-jwt-secret-before-production AUTH_STUB_PORT=14000 node shared/auth-stub/server.js > "$connection_temp/auth.log" 2>&1 &
connection_auth=$!
node - "$connection_temp/proxy.json" <<'NODE'
const fs=require('fs');const config=JSON.parse(fs.readFileSync('frontend/proxy.conf.json','utf8'));
for(const [key,value] of Object.entries(config)) value.target=key==='/auth'?'http://127.0.0.1:14000':'http://127.0.0.1:18082';
fs.writeFileSync(process.argv[2],JSON.stringify(config));
NODE
(cd frontend; NG_BUILD_MAX_WORKERS=2 exec node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --port 14200 --proxy-config "$connection_temp/proxy.json") > "$connection_temp/frontend.log" 2>&1 &
connection_frontend=$!
for attempt in {1..60}; do
  if curl -fsS http://127.0.0.1:18082/actuator/health >/dev/null 2>&1 && curl -fsS http://127.0.0.1:14200/auth/health >/dev/null 2>&1; then break; fi
  sleep 1
done
node scripts/verify-merged-connection.cjs
printf 'Logs: %s\n' "$connection_temp"
if [[ "${1:-}" == '--preview' ]]; then
  echo 'UI: http://127.0.0.1:14200 — john_trader (admin staff), jane_investor (analyst staff), bob_daytrader (customer); password mission123. Ctrl-C stops disposable stack.'
  wait "$connection_frontend"
fi
