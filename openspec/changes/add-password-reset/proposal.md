## Why

Users who forget their password have no way to regain access. AUTH-142 adds a secure,
self-service password-reset flow via email so users can recover their accounts without
operator involvement, while preventing account enumeration and token abuse.

## What Changes

- Add a **request-reset** endpoint: user submits an email; the service always returns the
  same neutral response whether or not an account exists (no account enumeration).
- When the email maps to an existing account, generate a single-use, time-limited reset
  token (30-minute TTL) and send a reset link by email via an outbound mail port.
- Add a **complete-reset** endpoint: user submits the token plus a new password; on success
  the password is updated, the token is consumed (invalidated) and cannot be reused.
- Enforce **rate limiting** on the request-reset endpoint per email and per client IP.
- New passwords must satisfy the shared **password policy** (see Assumptions).
- **Audit-log** every reset request and every completion outcome (requested, completed,
  failed-invalid-token, failed-expired-token, rate-limited).
- Add DB schema (Flyway) for reset tokens; store only a hash of the token, never the raw value.
- Both endpoints are **public** (no bearer auth) — the user is by definition unauthenticated.

## Capabilities

### New Capabilities
- `identity/password-reset`: request a reset link by email and complete a reset with a
  single-use, time-limited token; enumeration resistance, rate limiting, and audit logging.

### Modified Capabilities
<!-- None. Greenfield repo; no existing capability requirements change. -->

## Impact

- **New OpenAPI operations** (public, no security): `POST /auth/password-reset/requests`,
  `POST /auth/password-reset/completions`. Non-breaking (new endpoints in a greenfield API).
- **New DB schema (Flyway migration)**: `password_reset_token` table. Additive; no changes to
  applied migrations. See Assumptions re: the `user_account` table this depends on.
- **New outbound dependencies**: an email/notification port (implementation out of scope —
  see Non-goals) and a token-hashing utility.
- **New code**: `domain`, `application` (ports + use cases), `adapters/in/web`,
  `adapters/out/persistence` under `com.acme.identity`.

## Non-goals

- User registration, login, JWT issuance, or refresh-token flows (assumed to exist or be
  delivered separately). This slice touches only the reset flow.
- Building/choosing an email provider. This change defines an outbound mail **port** and
  emits the message; the concrete SMTP/provider adapter and email templating are out of scope.
- Multi-factor auth, security questions, or admin-initiated resets.
- Password policy definition itself (this change consumes the shared policy, see Assumptions).
- Distributed/production-grade rate-limit infrastructure (see design for the first-slice mechanism).

## Assumptions

- A `user_account` concept exists (or arrives with the auth slice) exposing lookup by email
  and a way to store a new password hash (bcrypt/argon2). This change references it via a port
  and adds the token table with a FK/association to the user id.
- A shared **password policy** validator exists (or a minimal one is introduced here):
  min length, complexity. This change calls it and rejects non-compliant passwords with 422.
- Passwords are hashed with bcrypt/argon2 by the account component; this change never stores
  plaintext and never logs the token or password.
