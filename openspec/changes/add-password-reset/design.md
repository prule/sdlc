## Context

See proposal.md — Why. Greenfield repo, standard stack (Java 25, Spring Boot 3.x, Postgres +
Flyway, Clean/Hexagonal, contract-first OpenAPI 3.1, RFC 7807). No existing auth/user code, so
this design depends on a `user_account` concept and a password-policy validator via ports
(see proposal Assumptions) rather than assuming concrete tables/classes it does not own.

## Goals / Non-Goals

**Goals:**
- Enumeration-neutral request endpoint; single-use, 30-minute tokens; audit trail.
- Keep all business rules (token validity, single-use, expiry) in the domain layer.
- Store only token hashes; raw token exists only in the emailed link.

**Non-Goals (design-level):**
- Concrete email provider/template and account persistence internals (consumed via ports).
- Distributed rate-limiting store — first slice uses a single-node bucket (see Decisions).

## Decisions

**1. Two operations, both public.** The user is unauthenticated, so both endpoints declare
`security: []` in OpenAPI and are permitted in Spring Security config. Resource-style paths:

```yaml
# openapi: paths/password-reset.yaml (referenced from root spec)
/auth/password-reset/requests:
  post:
    operationId: requestPasswordReset
    security: []
    requestBody:
      required: true
      content: { application/json: { schema: { $ref: '#/components/schemas/PasswordResetRequest' } } }
    responses:
      '202': { description: Accepted (neutral; identical for existing/non-existing accounts) }
      '422': { $ref: '#/components/responses/Problem' }
      '429': { $ref: '#/components/responses/Problem' }
/auth/password-reset/completions:
  post:
    operationId: completePasswordReset
    security: []
    requestBody:
      required: true
      content: { application/json: { schema: { $ref: '#/components/schemas/PasswordResetCompletion' } } }
    responses:
      '200': { description: Password updated (neutral success) }
      '400': { $ref: '#/components/responses/Problem' }   # RESET_TOKEN_INVALID / RESET_TOKEN_EXPIRED
      '422': { $ref: '#/components/responses/Problem' }   # VALIDATION_FAILED (policy)
# PasswordResetRequest { email }; PasswordResetCompletion { token, newPassword }
```
Alternative considered: a single toggling endpoint — rejected as it muddies the contract and status mapping.

**2. Return 202 for request, 200 for completion.** 202 signals "accepted, side-effect may or
may not follow" which is exactly the enumeration-neutral semantics we want. Alternative (200
for both) also works; 202 more honestly reflects the async email dispatch.

**3. Hash tokens at rest (SHA-256 of a 256-bit random value).** DB compromise must not yield
usable tokens. The random value is URL-safe base64 in the link; lookup is by hash. bcrypt is
unnecessary here (token is high-entropy, not a low-entropy secret) and its per-verify cost
would complicate hash-keyed lookup. Alternative (store raw) rejected on security grounds.

**4. Components (dependency direction inward-only):**
- `domain`: `PasswordResetToken` (value object/entity with `isValid(now)`, `consume()`),
  `PasswordResetOutcome` enum, domain exceptions `ResetTokenInvalidException` /
  `ResetTokenExpiredException` extending the sealed `DomainException` (codes
  `RESET_TOKEN_INVALID`, `RESET_TOKEN_EXPIRED`).
- `application`: use cases `RequestPasswordReset`, `CompletePasswordReset`; ports (interfaces)
  `PasswordResetTokenRepository`, `UserAccountPort` (find-by-email, update-password-hash),
  `MailPort`, `PasswordPolicy`, `RateLimiter`, `AuditLogPort`, `TokenHasher`/`TokenGenerator`.
  Depends only on domain.
- `adapters/in/web`: controller implementing the generated OpenAPI interface; delegates to use
  cases; never builds error bodies (global `@RestControllerAdvice` maps exceptions → Problem).
- `adapters/out/persistence`: JPA entity `PasswordResetTokenEntity` + repository adapter
  implementing `PasswordResetTokenRepository`; audit adapter. Mail adapter is a stub/interface
  boundary (concrete provider out of scope).
Domain imports no Spring/JPA; application imports only domain; adapters implement ports.

**5. Rate limiting (first slice): in-memory token-bucket** keyed by email and by client IP,
enforced in the application/inbound boundary, exceeding → domain-level `RateLimitedException`
(code `RATE_LIMITED`, 429). Documented as single-node; a shared store (Redis) is a later change.

**6. Audit logging via `AuditLogPort`** writing structured entries (correlationId, timestamp,
outcome, accountId when known) — never the raw token or password. correlationId comes from the
existing filter per error-handling standard.

## Risks / Trade-offs

- [Timing side-channel reveals account existence] → For non-existing accounts, run the same
  work profile (or a constant-time path) so response timing does not branch on existence.
- [In-memory rate limiter is per-node and resets on restart] → Acceptable for first slice;
  documented as a known limitation with a follow-up to a shared store.
- [Email dispatch failure after token issue] → Token still expires in 30 min; user can re-request.
  Dispatch is best-effort and audited; do not surface failure to the client (enumeration-neutral).
- [Depends on not-yet-existing user_account / password policy] → Isolated behind ports so this
  slice is implementable and testable with test doubles; integration blocked until those land.

## Migration Plan

- **Flyway**: new migration `V<n>__create_password_reset_token.sql` adding `password_reset_token`
  (`id` uuid pk, `user_account_id` uuid, `token_hash` varchar unique, `expires_at` timestamptz,
  `consumed_at` timestamptz null, `created_at` timestamptz). Index on `token_hash`; FK to
  `user_account(id)` added once that table exists (or as a soft reference in this slice).
- Additive only; never edits an applied migration. **Rollback**: drop the new table
  (`V<n+1>__drop...` if needed); no data migration required as the table starts empty.

## Open Questions

- Rate-limit thresholds and window (e.g., 3/hour per email, 10/hour per IP) — deferrable to
  config; does not change specs or task breakdown.
- Reset link base URL / front-end route the emailed link points at — owned by the email/UX
  slice; deferrable.
