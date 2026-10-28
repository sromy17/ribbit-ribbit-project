# Auth Stub

Standalone Node.js authentication stub that issues JWTs for the Spring trading service.

The stub reads and writes users in the same PostgreSQL `users` table used by the Spring service.

The users in the repository seed script use `mission123` as their password. Existing databases
created with older placeholder password values must be reseeded or migrated before those users
can log in.

## Run

1. Copy `.env.example` to `.env` and set `JWT_SHARED_SECRET`, `DB_URL`, `DB_USER`, and `DB_PASSWORD`.
2. Install dependencies and start:

```bash
npm install
npm start
```

The service starts at `http://localhost:4000` by default.

## Endpoints

- `POST /register`
- `POST /login`
- `POST /refresh`
- `POST /logout`
- `POST /employee/login`
- `POST /employee/refresh`
- `POST /employee/logout`
- `GET /health`

### Employees

Employee login accepts the same `{ "username": "...", "password": "..." }`
body as investor login, but queries `employees` rather than `users`.
Only database roles `ADMIN` and `ANALYST` can sign in. There is deliberately
no employee registration endpoint, and `/register` never inserts employees
or accepts a role assignment.

Fresh development seeds include `admin_user` / `AdminDemo123!` and
`analyst_user` / `AnalystDemo123!`. These are public demo passwords: replace
their bcrypt hashes before any public deployment. Existing databases can run
`scripts/employee-auth.sql` from the repository root without resetting investor
data; the script preserves real passwords and fixes the old admin placeholder.

Employee responses include `role` (`admin` or `analyst`) and the existing
token/session fields. JWTs use `principal_type: "employee"`, `eid`, and uppercase
`roles`, with no investor `uid`. Separate `refresh_tokens_employee` and
`access_tokens_employee` tables store token hashes. Employee refresh accepts
`{ "refreshToken": "..." }`, locks and revokes the old session, and issues a
new pair atomically. Employee logout accepts `{ "accessToken": "..." }` and
revokes both tokens. Spring validates these sessions against the employee
tables and role, without confusing employee IDs with investor IDs.

To run the PostgreSQL-backed employee regression tests:

```bash
TEST_DATABASE_URL=postgresql://USER:PASSWORD@localhost:5432/TEST_DB node --test employee-auth.test.js
```

Use a disposable test database. The suite creates and drops its own isolated
schema and does not require new npm dependencies.

### Register

```json
{
  "username": "alice",
  "password": "mission123"
}
```

Returns `201` with `userId` and `username`.

On register, the stub inserts into `users(userid, username, salt, userhashedsaltedpassword)` using:

- `salt = "bcrypt"`
- `userhashedsaltedpassword = bcrypt(password)`

### Login

```json
{
  "username": "alice",
  "password": "mission123"
}
```

Returns a JWT token payload:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600
}
```

## Token Claims

The stub signs HS256 JWTs with claims:

- `sub`: username
- `uid`: numeric user id
- `roles`: string array
