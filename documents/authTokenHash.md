# Authentication Token Hash Refactor

## Summary

Authentication now stores SHA-256 hashes of refresh and access tokens instead
of storing usable token values. The raw tokens are returned only to the
authenticated client and are never written to the database.

The old `user_sessions`/`refreshtoken` model was removed from the Spring
backend. Authentication state is now represented by the `refresh_tokens` and
`access_tokens` tables defined in `basedSchema.sql`.

## Files changed

### `basedSchema.sql`

- Changed `refresh_tokens.refreshTokenHash` from `INT` to `VARCHAR(64)`.
- Changed `access_tokens.accessTokenHash` from `INT` to `VARCHAR(64)`.
- Added `UNIQUE` constraints to both hash columns.
- A SHA-256 digest is stored as a 64-character lowercase hexadecimal value.

### `insert-data.sql`

- Replaced numeric placeholder token hashes with 64-character hexadecimal
  seed values.
- Kept the seed rows compatible with the new hash column types.

### `shared/auth-stub/server.js`

- Added SHA-256 hashing with Node's `crypto` module.
- Refresh tokens are now cryptographically random opaque strings rather than
  predictable integers.
- Login creates:
  1. A refresh-token row containing only the refresh-token hash.
  2. An access-token row containing only the JWT hash.
- Refresh-token lookup hashes the submitted raw token and compares the hash in
  `refresh_tokens`.
- Refresh tokens are checked for both expiration and revocation.
- Refresh-token rotation now revokes the old row before creating a new pair.
- Access-token logout hashes the submitted JWT and revokes the matching access
  token and its associated refresh token.
- Registration now supplies the required `first_name` and `last_name` values
  required by the current users schema.

### `src/main/java/com/neueda/leap/trading/domain/User.java`

- Removed the obsolete `user_sessions` relationship.

### `src/main/java/com/neueda/leap/trading/domain/Session.java`

- Removed because it mapped to the old `user_sessions` table.

### `src/main/java/com/neueda/leap/trading/repository/jpa/SessionRepository.java`

- Removed because it queried the old refresh-token representation.

### `src/main/java/com/neueda/leap/trading/domain/RefreshToken.java`

- Added a JPA mapping for `refresh_tokens`.
- Represents the token hash, issue/expiration timestamps, and revocation flag.

### `src/main/java/com/neueda/leap/trading/domain/AccessToken.java`

- Added a JPA mapping for `access_tokens`.
- Represents the access-token hash, associated refresh-token ID, issue/expiration
  timestamps, and revocation flag.

### `src/main/java/com/neueda/leap/trading/repository/jpa/AccessTokenRepository.java`

- Added a repository query that finds an access token by SHA-256 hash only when
  it is not revoked and has not expired.

### `src/main/java/com/neueda/leap/trading/security/TokenHash.java`

- Added the shared Java SHA-256 hashing helper used by request validation.

### `src/main/java/com/neueda/leap/trading/security/SessionTokenValidationFilter.java`

- Replaced validation against `user_sessions.dateissued`.
- Hashes the presented bearer token and validates the corresponding
  `access_tokens` row.
- Rejects missing, revoked, unknown, or expired access-token records.

### `frontend/src/app/core/services/auth.service.ts`

- Changed the refresh-token response type from `number` to `string`.
- Sends the opaque refresh token unchanged to `/auth/refresh`.

## Token lifecycle

1. Login verifies the password.
2. The auth stub generates an opaque refresh token and a signed JWT.
3. Only SHA-256 hashes are persisted.
4. The client receives the raw access and refresh tokens.
5. API requests present the raw access token; the Spring filter hashes it and
   checks `access_tokens`.
6. Refresh hashes the submitted refresh token, verifies the active database
   record, revokes the old refresh token, and issues a new token pair.
7. Logout hashes the access token and revokes both the access token and its
   linked refresh token.

## Database migration note

This schema change requires the database to be recreated or migrated from the
old token tables. Existing rows in `user_sessions` are not compatible with the
new implementation. Apply the updated schema before starting the auth stub and
Spring backend.
