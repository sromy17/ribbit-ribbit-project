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
- `POST /logout`
- `GET /health`

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
