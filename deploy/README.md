# Container deployment

The stack builds Angular/Nginx, Spring Boot (Java 25), Node authentication,
and a one-shot migration image. PostgreSQL 16 stores separate OLTP and OLAP
databases. Only Nginx publishes a port. Backend and auth run as non-root users.
Kafka is disabled; order execution is synchronous and quote requests retain the
1,000-attempt daily cap and one-hour display cache.

## Start

From the repository root, with Docker Engine and Compose v2 installed:

```sh
cp deploy/.env.example deploy/.env
# Edit deploy/.env: set a database password and JWT secret (at least 32 characters).
# Generate each secret separately with: openssl rand -hex 32
chmod 600 deploy/.env
docker compose --env-file deploy/.env -p ribbit config --quiet
docker compose --env-file deploy/.env -p ribbit up --build --wait --wait-timeout 300
```

Open http://localhost:4200. Register a user; fresh installations contain the
instrument catalog but no demo users, balances, or trades. Quote API keys are
optional for startup and required for provider-backed quotes and execution.

Initialization SQL runs only on an empty database volume. The migration job
applies additive compatibility migrations and configures instruments before
Spring starts; Spring's existing Hibernate update behavior creates remaining
entity tables. This is a single-instance deployment baseline, not a versioned
production migration system. For existing installations, back up first and
preserve the original Compose project name and database credentials. A missing
OLAP database on an existing volume must be provisioned before upgrading.

## Verify and operate

```sh
docker compose --env-file deploy/.env -p ribbit ps -a
curl -fsS http://localhost:4200/health
curl -fsS http://localhost:4200/auth/health
curl -fsS http://localhost:4200/api/actuator/health
docker compose --env-file deploy/.env -p ribbit logs --tail=100 migrate springboot auth frontend
```

The migration service should exit 0; the other four services should be healthy.
Nginx preserves `/api/admin`, `/api/analytics`, and `/api/etl`; other `/api` routes
lose the prefix, matching local development. `/auth` goes to the auth service.
Verify registration, login, account creation, and page refresh before release.
Test live trading only when provider quota is available.

```sh
# Apply a source update (keep the same project name):
docker compose --env-file deploy/.env -p ribbit up --build --wait --wait-timeout 300
# Stop without deleting data:
docker compose --env-file deploy/.env -p ribbit down
# Back up both databases and roles before upgrades:
docker compose --env-file deploy/.env -p ribbit exec -T postgres sh -c 'pg_dumpall -U "$POSTGRES_USER"' > ribbit-backup.sql
```

Database backups contain credentials and personal data; store them securely.
The `postgres_data` volume holds databases; `quote_state` holds the persisted
provider attempt count. Preserve both, and never use `down --volumes` on a live
installation. Changing the configured PostgreSQL password does not change an
existing database's password. Back up before schema changes; image rollback
alone does not roll back database changes.

## Host deployment

The default HTTP bind is loopback. Put a TLS reverse proxy/load balancer in
front of port 4200 for public deployment, or use an SSH tunnel for private
access. Set `HTTP_BIND_ADDRESS` and `HTTP_PORT` for the target host. Keep
PostgreSQL and backend ports internal. No cloud resources or public deployment
are created by these files. Production hosting still needs TLS, backups,
monitoring, and a reviewed database migration strategy.

This configuration is for one backend instance. The API budget does not
coordinate across replicas or other applications using the same API key.
For isolated verification, use another project name and `HTTP_PORT=4280`;
this creates independent database and quote-state volumes. With that isolated
stack running, execute `node deploy/smoke.cjs http://127.0.0.1:4280` to verify
registration, login, persistent account reads, and staff access restrictions.
This creates a test user/account and makes no external quote requests.
