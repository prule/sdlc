## 1. OpenAPI contract

- [ ] 1.1 Add `paths/password-reset.yaml` with `POST /auth/password-reset/requests` (202) and `POST /auth/password-reset/completions` (200), both `security: []`, referenced from the root spec.
- [ ] 1.2 Add `PasswordResetRequest` (email) and `PasswordResetCompletion` (token, newPassword) schemas plus the shared RFC 7807 `Problem` response ref; document 400/422/429 outcomes.

## 2. Generate stubs

- [ ] 2.1 Run `./gradlew openApiGenerate` and confirm the generated Spring interfaces + DTOs for both operations compile.

## 3. Domain

- [ ] 3.1 Add `PasswordResetToken` (hash, expiresAt, consumedAt) with `isValid(now)` and `consume()`, plus `PasswordResetOutcome` enum — no Spring/JPA imports.
- [ ] 3.2 Add sealed `DomainException` subtypes `ResetTokenInvalidException` (`RESET_TOKEN_INVALID`), `ResetTokenExpiredException` (`RESET_TOKEN_EXPIRED`), `RateLimitedException` (`RATE_LIMITED`).
- [ ] 3.3 Unit-test token validity/expiry/single-use transitions (happy, expired, already-consumed).

## 4. Application (use cases + ports)

- [ ] 4.1 Define ports: `PasswordResetTokenRepository`, `UserAccountPort`, `MailPort`, `PasswordPolicy`, `RateLimiter`, `AuditLogPort`, `TokenGenerator`/`TokenHasher`.
- [ ] 4.2 Implement `RequestPasswordReset` use case: rate-limit check, enumeration-neutral lookup, issue+hash token (30-min TTL), dispatch mail, audit `RESET_REQUESTED`.
- [ ] 4.3 Implement `CompletePasswordReset` use case: validate token, enforce password policy, update password hash, consume token, audit outcome.
- [ ] 4.4 Unit-test both use cases with test doubles: neutral response for unknown email, rate-limit rejection, invalid/expired/reused token, policy violation, success.

## 5. Outbound adapters + migration

- [ ] 5.1 Add Flyway `V<n>__create_password_reset_token.sql` (id, user_account_id, token_hash unique+indexed, expires_at, consumed_at, created_at).
- [ ] 5.2 Implement `PasswordResetTokenEntity` + JPA repository adapter mapping to/from the domain object.
- [ ] 5.3 Implement `AuditLogPort` adapter, in-memory token-bucket `RateLimiter` (per email + per IP), and `TokenGenerator`/`TokenHasher` (256-bit random, SHA-256 hash).
- [ ] 5.4 Testcontainers integration test for the repository adapter (persist, find-by-hash, consume).

## 6. Inbound controller + wiring

- [ ] 6.1 Implement the controller against the generated interface, delegating to the use cases; add both endpoints to Spring Security permit-all and confirm `security: []`.
- [ ] 6.2 Map new domain exceptions to Problem responses in the global `@RestControllerAdvice` (400 invalid/expired, 422 policy, 429 rate-limited); redact token/password from logs.

## 7. End-to-end tests

- [ ] 7.1 MockMvc/WebTestClient: request for known vs unknown email returns identical 202 body (enumeration neutrality).
- [ ] 7.2 MockMvc/WebTestClient: full happy path (request → extract token → complete → 200), token reuse rejected, expired token rejected, policy violation → 422, rate-limit → 429.
- [ ] 7.3 Assert audit entries recorded for each outcome and contain neither raw token nor password.
