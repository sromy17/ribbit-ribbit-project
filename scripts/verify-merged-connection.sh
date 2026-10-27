#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Isolated source/OLAP databases and ports; never reads the project .env.
# Match the local launcher after a fresh checkout.
if [[ ! -d shared/auth-stub/node_modules ]]; then npm ci --prefix shared/auth-stub; fi
if [[ ! -d frontend/node_modules ]]; then npm ci --prefix frontend; fi
connection_demo=false
connection_backend_port=18082; connection_auth_port=14000; connection_ui_port=14200
if [[ "${1:-}" == '--demo' ]]; then
  connection_demo=true
  connection_backend_port=18182; connection_auth_port=14100; connection_ui_port=14300
fi
connection_container="leap-connection-check-$$"
connection_temp="$(mktemp -d)"
connection_backend=""; connection_auth=""; connection_frontend=""; connection_nginx=""
cleanup() {
  echo "Connection verification logs: $connection_temp"
  for connection_pid in "$connection_frontend" "$connection_auth" "$connection_backend"; do
    if [[ -n "$connection_pid" ]]; then kill "$connection_pid" 2>/dev/null || true; wait "$connection_pid" 2>/dev/null || true; fi
  done
  if [[ -n "$connection_nginx" ]]; then docker stop "$connection_nginx" >/dev/null 2>&1 || true; fi
  docker stop "$connection_container" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker run --detach --rm --name "$connection_container" -e POSTGRES_PASSWORD=connection-test-only -e POSTGRES_DB=connection_source -e POSTGRES_OLAP_DB=connection_reporting -v "$PWD/olap.sql:/opt/ribbit/olap.sql:ro" -p 127.0.0.1::5432 postgres:17 >/dev/null
for attempt in {1..60}; do
  if docker exec "$connection_container" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
connection_port="$(docker port "$connection_container" 5432/tcp | head -1 | awk -F: '{print $NF}')"
docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source < scripts/init-olap.sql > "$connection_temp/olap-init.log" 2>&1
docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source < basedSchema.sql > "$connection_temp/schema.log" 2>&1
docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source < insert-data.sql >> "$connection_temp/schema.log" 2>&1
if [[ "$connection_demo" == true ]]; then
  docker exec -i "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source < scripts/fixtures/admin-analytics-demo.sql >> "$connection_temp/schema.log" 2>&1
fi
docker exec "$connection_container" psql -v ON_ERROR_STOP=1 -U postgres -d connection_source -c "INSERT INTO roles(rolename) VALUES('PLATFORM_ADMINISTRATOR'),('ANALYST'); INSERT INTO user_roles(userid,roleid) SELECT 1,roleid FROM roles WHERE rolename='PLATFORM_ADMINISTRATOR'; INSERT INTO user_roles(userid,roleid) SELECT 2,roleid FROM roles WHERE rolename='ANALYST';" >/dev/null
mvn -q package -DskipTests > "$connection_temp/package.log" 2>&1
DB_OLTP_URL="jdbc:postgresql://127.0.0.1:$connection_port/connection_source" DB_OLTP_USER=postgres DB_OLTP_PASSWORD=connection-test-only DB_URL="jdbc:postgresql://127.0.0.1:$connection_port/connection_source" DB_USER=postgres DB_PASSWORD=connection-test-only DB_OLAP_URL="jdbc:postgresql://127.0.0.1:$connection_port/connection_reporting" ETL_ENABLED=false JWT_SHARED_SECRET=please-change-this-jwt-secret-before-production FINNHUB_API_KEY= MARKET_DATA_NEWS_RSS_URL=http://127.0.0.1:1/unavailable SERVER_PORT="$connection_backend_port" ORDER_EXECUTION_KAFKA_ENABLED=false java -jar target/team-skeleton.jar > "$connection_temp/backend.log" 2>&1 &
connection_backend=$!
DB_OLTP_URL="postgresql://127.0.0.1:$connection_port/connection_source" DB_OLTP_USER=postgres DB_OLTP_PASSWORD=connection-test-only DB_URL="postgresql://127.0.0.1:$connection_port/connection_source" DB_USER=postgres DB_PASSWORD=connection-test-only JWT_SHARED_SECRET=please-change-this-jwt-secret-before-production AUTH_STUB_PORT="$connection_auth_port" node shared/auth-stub/server.js > "$connection_temp/auth.log" 2>&1 &
connection_auth=$!
node - "$connection_temp/proxy.json" "$connection_backend_port" "$connection_auth_port" <<'NODE'
const fs=require('fs');const config=JSON.parse(fs.readFileSync('frontend/proxy.conf.json','utf8'));
for(const [key,value] of Object.entries(config)) value.target='http://127.0.0.1:'+(key==='/auth'?process.argv[4]:process.argv[3]);
fs.writeFileSync(process.argv[2],JSON.stringify(config));
NODE
(cd frontend; NG_BUILD_MAX_WORKERS=2 exec node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --port "$connection_ui_port" --proxy-config "$connection_temp/proxy.json") > "$connection_temp/frontend.log" 2>&1 &
connection_frontend=$!
for attempt in {1..60}; do
  if curl -fsS "http://127.0.0.1:$connection_backend_port/actuator/health" >/dev/null 2>&1 && curl -fsS "http://127.0.0.1:$connection_ui_port/auth/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
MERGE_UI_URL="http://127.0.0.1:$connection_ui_port" MERGE_DEMO="$connection_demo" node scripts/verify-merged-connection.cjs
# Exercise the production Nginx routing against the same isolated application.
node - "$connection_temp/nginx.conf" "$connection_backend_port" "$connection_auth_port" <<'NODE'
const fs=require('fs');
const config=fs.readFileSync('frontend/nginx.conf','utf8')
  .replaceAll('springboot:8081','host.docker.internal:'+process.argv[3])
  .replaceAll('auth:4000','host.docker.internal:'+process.argv[4]);
fs.writeFileSync(process.argv[2],config);
NODE
connection_nginx="leap-routing-check-$$"
docker run --detach --rm --name "$connection_nginx" --add-host=host.docker.internal:host-gateway \
  -v "$connection_temp/nginx.conf:/etc/nginx/conf.d/default.conf:ro" -p 127.0.0.1::80 nginx:1.28-alpine >/dev/null
connection_nginx_port="$(docker port "$connection_nginx" 80/tcp | head -1 | awk -F: '{print $NF}')"
for attempt in {1..30}; do
  if curl -fsS "http://127.0.0.1:$connection_nginx_port/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
MERGE_UI_URL="http://127.0.0.1:$connection_nginx_port" MERGE_DEMO="$connection_demo" node scripts/verify-merged-connection.cjs
echo 'PASS: production Nginx preserves admin/analytics/ETL paths and forwards customer/auth routes.'

printf 'Logs: %s\n' "$connection_temp"
if [[ "${1:-}" == '--preview' || "$connection_demo" == true ]]; then
  echo "UI: http://127.0.0.1:$connection_ui_port — john_trader (admin staff), jane_investor (analyst staff), bob_daytrader (customer); password mission123. Ctrl-C stops disposable stack."
  wait "$connection_frontend"
fi
