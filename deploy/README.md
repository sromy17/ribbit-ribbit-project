# Full application container deployment

Compose runs Spring Boot, Node authentication, and Angular/Nginx against external RDS (separate OLTP/OLAP databases). The frontend
gateway waits for backend and auth health checks before starting.

## Configure and start

From the repository root, copy `deploy/.env.example` to `deploy/.env` only if
that private file does not already exist. Set PostgreSQL credentials, a JWT
secret of at least 32 characters, and provider keys. Preserve existing database
credentials across restarts. Never replace an existing configuration with the
example. The private file should have permissions `600`.

```sh
docker compose --env-file deploy/.env -p ribbit config --quiet
docker compose --env-file deploy/.env -p ribbit up --build -d --wait --wait-timeout 300
docker compose --env-file deploy/.env -p ribbit ps -a
curl -fsS http://127.0.0.1:4200/health
curl -fsS http://127.0.0.1:4200/api/actuator/health
curl -fsS http://127.0.0.1:4000/health
```

All three services should be healthy. Database schemas are prepared manually
before startup; no migration container runs automatically. Fresh volumes
contain instruments, not demo users or transactions. Backend and auth bind to
loopback by default via API_BIND_ADDRESS/API_PORT and AUTH_BIND_ADDRESS/AUTH_PORT.
PostgreSQL remains internal. The defaults are API port 8081 and auth port 4000.

Open http://localhost:4200 (HTTP_PORT, default 4200). Nginx forwards `/auth` to
authentication and `/api` to Spring Boot, preserving the `/api` prefix for
admin/analytics/ETL. Angular uses these same-origin routes. No separate CORS
configuration or public backend/auth ports are required for this gateway flow.
HTTP_BIND_ADDRESS defaults to loopback. Put a TLS reverse proxy in front of the
frontend for public deployment and configure its network access appropriately.
Keep existing deployment credentials unchanged when adding the frontend.

## Storage and updates

Docker Compose does not impose a root-disk capacity limit. It uses the Docker
host's filesystem. On EC2, after enlarging EBS, verify `df -h /` shows the larger
filesystem; enlarging the EBS volume alone may require partition/filesystem
extension. No Docker daemon data-root changes are needed when expanding the
existing root filesystem.

The Java build now uses a BuildKit Maven cache instead of embedding downloaded
dependencies in an intermediate image layer. The cache still consumes disk but
can be reclaimed. Angular is built in its own multi-stage image. Its build cache also consumes
disk; allow space for both builds and runtime images.

```sh
# Reclaim unused build cache; preserve database volumes:
docker builder prune -af
# Apply updates:
docker compose --env-file deploy/.env -p ribbit up --build -d --wait --remove-orphans
# Stop without deleting data:
docker compose --env-file deploy/.env -p ribbit down
```

Do not use `down --volumes` on a live deployment. `postgres_data` stores the
source and reporting databases; `quote_state` stores the provider attempt count.
Keep the same Compose project name to reuse these volumes.

Back up before upgrades:

```sh
docker compose --env-file deploy/.env -p ribbit exec -T postgres sh -c 'pg_dumpall -U "$POSTGRES_USER"' > ribbit-backup.sql
```

Store backups securely. Existing schema compatibility migrations are additive;
Hibernate update still creates remaining entity tables. A reviewed versioned
migration strategy is required before production use. Images alone cannot roll
back database changes. Initialization SQL runs only on empty volumes; older
volumes missing the reporting database require it to be provisioned separately.

Kafka remains disabled, execution is synchronous, and display quotes use a
one-hour cache. The 1,000-attempt UTC daily budget persists in `quote_state` and
covers one backend instance, not other apps using the same provider key.

For an isolated test, use another project name and unused API_PORT/AUTH_PORT
values. Run `node deploy/smoke.cjs http://127.0.0.1:8081 http://127.0.0.1:4000`
against that test stack only: it creates a user/account without quote API calls.

## Fresh RDS preparation

The current `basedSchema.sql` and `olap.sql` already include the compatibility
fields required by the seed. Do not rerun these reset scripts against populated
databases. After initializing a fresh OLTP database, the optional
`scripts/prepare-instrument-catalog.sql` adds the complete USD equity catalog
without replacing existing instrument IDs/status. Run it using psql with
`-v ON_ERROR_STOP=1` against OLTP. It requires no Node migration container.

The default Compose file now targets RDS using POSTGRES_HOST and verified TLS. Download the RDS global CA bundle to deploy/certs/global-bundle.pem before starting. If EC2 has already
been configured for RDS, preserve those host, database and TLS settings rather
than replacing the entire Compose file from another machine. Remove only the
`migrate` service and its `depends_on` entries. Older migration scripts remain
in the repository for upgrading older installations.

### International market data and branding

The frontend catalog includes London (`.L`, GBP) and NSE (`.NS`, INR) equities.
London quotes and international daily history use Yahoo's chart feed; NSE display
quotes use Fauxnance. London `GBp`/`GBX` prices are converted from pence to GBP
before display. History and display quotes are cached for one hour; requests to
both providers count against the existing backend daily request budget.

`scripts/prepare-international-catalog.cjs` exports a function that accepts a
connected `pg.Pool` and adds the ten international instruments transactionally.
It is safe to repeat and preserves existing instruments, balances and trades.
Trading requires an account in the stock's currency; there is no implicit FX
conversion. Market history is used for display, never as an execution price.

Deploy every file and subfolder inside `frontend/dist/frontend/browser/` to the
S3 bucket root, preserving `data/` and `brand/`. Upload hashed assets before
`index.html`, then invalidate CloudFront's cached HTML/catalog. The logo uses the
frog quadrant of `frontend/public/brand/ribbit-logo.jpg` through CSS cropping.

### General platform support

Help accepts platform questions, technical issues and account-assistance requests
without requiring a trade or dispute. Apply `scripts/prepare-support-tickets.sql`
once to an existing OLTP database before deploying the support API. This extends
`support_ticket`, retains existing rows, and aligns its generated ticket IDs.
Fresh databases use the updated `basedSchema.sql` definition.

Customers can submit and list tickets only for accounts they own. Submissions use
a unique request reference so a retry returns the same ticket. The Administration
Support queue uses the existing staff read/work permissions; staff responses and
status changes appear under the customer's Help requests. Resolving a ticket
requires a customer-visible response. No email delivery is configured.
