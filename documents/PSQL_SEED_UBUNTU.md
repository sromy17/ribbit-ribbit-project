# Seed the current database using psql on Ubuntu

These commands run on the Ubuntu VM and connect directly to RDS, not the
PostgreSQL Docker container. The data is synthetic demonstration data.

Files used:

- `basedSchema.sql`: base OLTP schema. Contains DROP TABLE statements.
- `migrations/*.sql`: compatibility updates for older existing installations.
  A fresh database created from the current base schema does not need them.
- `scripts/fixtures/current-schema-seed.sql`: updated demo inserts.

Use `ribbit-oltp` for these files, not the RDS instance identifier
`team-ribbit-db`, and not the separate `ribbit-olap` reporting database.
This procedure does not configure the application to use RDS or initialize OLAP.

## 1. Open an SSH terminal on the Ubuntu VM

Change to the repository directory. Ensure the latest script is present on the VM;
editing the file on your Mac does not update the VM automatically.

```bash
cd ~/ribbit-ribbit-project
ls basedSchema.sql migrations/*.sql scripts/fixtures/current-schema-seed.sql
```

If files are missing or stale, transfer the current files or pull the committed
changes from your repository before proceeding.

## 2. Install the PostgreSQL client

```bash
sudo apt-get update
sudo apt-get install -y postgresql-client ca-certificates curl
psql --version
```

A PostgreSQL server does not need to be installed on the VM to connect to RDS.

## 3. Download the RDS certificate and configure this shell

```bash
mkdir -p "$HOME/.postgresql"
curl -fSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  -o "$HOME/.postgresql/global-bundle.pem"

export PGHOST='team-ribbit-db.c8xuasgw2vz5.us-east-1.rds.amazonaws.com'
export PGPORT='5432'
export PGUSER='postgres'
export PGSSLMODE='verify-full'
export PGSSLROOTCERT="$HOME/.postgresql/global-bundle.pem"
```

Use your RDS master username if it differs from `postgres`. These settings apply
only to the current shell. Each psql invocation can prompt for the RDS password;
do not put the password in SQL, source control, or command history.
The RDS security group must permit TCP 5432 from the EC2 instance/security group.

## 4. Check/create the application database

Connect to the existing maintenance database:

```bash
psql -X -W -d postgres
```

The prompt changes from `ubuntu@...$` to `postgres=>` or `postgres=#`.
Run these commands inside psql:

```sql
\conninfo
\l
```

If `ribbit-oltp` does not exist, create it once:

```sql
CREATE DATABASE "ribbit-oltp";
```

Then connect to it and inspect its tables:

```sql
\connect ribbit-oltp
SELECT current_database(), current_user;
\dt public.*
```

Exit psql:

```text
\q
```

Use the existing database if already present; do not drop/recreate it.

## 5. Initialize the schema ONLY for a new, empty database

**Skip the base schema command if the tables already exist.** `basedSchema.sql`
contains DROP TABLE statements and would delete existing data. Back up an existing
database before upgrades. Do not run a reset while the application is using it.

For a database confirmed empty in step 4, run from the Ubuntu shell:

```bash
psql -X -W -v ON_ERROR_STOP=1 --single-transaction \
  -d ribbit-oltp -f basedSchema.sql
```

Expect CREATE TABLE messages and no ERROR. If there is an error, stop here.

Skip the following migrations for a fresh database created from the current
`basedSchema.sql`. They are retained for older installations that require these
compatibility updates:

```bash
psql -X -W -v ON_ERROR_STOP=1 --single-transaction -d ribbit-oltp \
  -f migrations/2026-10-23-auth-column.sql \
  -f migrations/2026-10-23-evidence-utc.sql \
  -f migrations/2026-10-26-local-compatibility.sql
```

These are the compatibility migrations for this repository, not a general
upgrade procedure for unrelated/unknown database schemas. Stop on errors.

## 6. Load the updated demo data

From the Ubuntu shell, in the repository root:

```bash
psql -X -W -v ON_ERROR_STOP=1 -d ribbit-oltp \
  -f scripts/fixtures/current-schema-seed.sql
```

This uses the SQL file directly, avoiding multiline clipboard errors. It stops on
the first error. The seed wraps its changes in BEGIN/COMMIT; on an error the
connection closes and uncommitted seed changes roll back.

Expected ending: COMMIT and `Demo data loaded; customer password: mission123`.
The seed checks existing IDs/usernames and fails instead of overwriting records.
It is a one-time import. If it reports conflicts, inspect the existing records;
do not delete them just to force a rerun. Instruments are matched by ticker so
an existing catalog can have different IDs. Ambiguous duplicate tickers abort.

## 7. Verify the rows in psql

```bash
psql -X -W -d ribbit-oltp
```

```sql
SELECT current_database();

SELECT userid, username, first_name, last_name
FROM users WHERE userid BETWEEN 1 AND 5 ORDER BY userid;

SELECT accountid, userid, account_type, currency, availablefunds
FROM accounts WHERE accountid BETWEEN 101 AND 105 ORDER BY accountid;

SELECT 'users' AS entity, count(*) AS seeded_rows FROM users WHERE userid BETWEEN 1 AND 5
UNION ALL
SELECT 'accounts', count(*) FROM accounts WHERE accountid BETWEEN 101 AND 105
UNION ALL
SELECT 'orders', count(*) FROM trade_request WHERE requestid BETWEEN 2001 AND 2007
UNION ALL
SELECT 'fills', count(*) FROM trades_executed WHERE executeid BETWEEN 3001 AND 3004
UNION ALL
SELECT 'disputes', count(*) FROM dispute_cases WHERE idempotency_key LIKE 'seed-dispute-%';
```

Expected scoped counts: users 5, accounts 5, orders 7, fills 4, disputes 3.

```text
\q
```

## Seed behavior and common errors

- Demo customer usernames: `john_trader`, `jane_investor`, `bob_daytrader`,
  `alice_portfolio`, `charlie_hedge`. Password: `mission123`. These are public
  sample credentials, for demonstration environments only.
- Legacy employees are reference rows, not working staff logins. Authentication
  creates real tokens on login; no dummy tokens are inserted.
- No holdings, opening positions, ledger movements or quote evidence are invented
  from incomplete historical data. This is not a complete reporting/ledger fixture.
- `relation ... does not exist`: wrong database or schema setup not completed.
- `database ... does not exist`: connect to `postgres` and list databases with `\l`.
- `password authentication failed`: use RDS credentials; Docker credentials can differ.
- Certificate file missing: rerun step 3 in the shell where psql is invoked.
- `No space left on device`: fix host/RDS storage before retrying; do not reset data.
- `current transaction is aborted` in an interactive session: run `ROLLBACK;`,
  then correct the original error. The recommended `-f` invocation exits instead.

References:

- psql file/error handling: https://www.postgresql.org/docs/current/app-psql.html
- RDS TLS: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/ssl-certificate-rotation-postgresql.html
