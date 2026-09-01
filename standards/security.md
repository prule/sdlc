# Security Standard

Baseline security rules for every service. Authentication is stateless JWT; authorization is
claim/role based; secrets never live in code.

## 1. Authentication — JWT bearer tokens

- Clients authenticate with a **Bearer JWT** in `Authorization: Bearer <token>`. Services are
  **stateless** — no server-side session. Configure Spring Security as an OAuth2 resource server
  (`spring-boot-starter-oauth2-resource-server`) validating tokens with the issuer's JWKS.
- Tokens are **signed** (RS256/ES256, asymmetric — services verify with the public key; only the auth
  server holds the private key). Reject `alg: none` and symmetric algorithms.
- **Access tokens are short-lived** (≈15 min). Use refresh tokens (longer-lived, rotated, revocable)
  to obtain new access tokens. Access tokens are not stored server-side.
- Validate on every request: signature, `iss`, `aud`, `exp`, `nbf`. Reject expired/invalid with 401
  (`UNAUTHENTICATED`), never with a 500.

### Required claims

| Claim | Meaning |
|-------|---------|
| `sub` | Stable user id (UUID) — the principal |
| `iss` | Issuer (auth server) — must match config |
| `aud` | This service / API audience |
| `exp`,`iat`,`nbf` | Expiry / issued-at / not-before |
| `jti` | Token id (for revocation lists if used) |
| `roles` | Array of roles, e.g. `["USER","ADMIN"]` |
| `scope` | Space-delimited OAuth scopes |
| `tenantId` | (multi-tenant) tenant the user belongs to |

Do **not** put sensitive PII (full name, email, address) in the token — it's base64, not encrypted, and
often logged. Keep tokens to identity + authorization claims; fetch profile data from the service.

## 2. Authorization

- Enforce with method security (`@PreAuthorize("hasRole('ADMIN')")` / `hasAuthority('scope:...')`) on
  the inbound port or controller, driven by the token's `roles`/`scope` claims.
- Default deny: every endpoint requires authentication unless explicitly marked public
  (`security: []` in OpenAPI + permitted in the security config).
- **Always check ownership/tenant**, not just role: a `USER` may fetch only their own resources.
  Compare `sub`/`tenantId` from the token against the resource — never trust an id from the request body.
- Map roles/scopes to Spring authorities in a `JwtAuthenticationConverter`.

## 3. Transport & headers

- HTTPS only; HSTS enabled. No secrets or tokens in URLs/query strings (they get logged) — headers only.
- Set security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a restrictive CSP.
- CORS is explicit allow-list of origins/methods; never `*` with credentials.

## 4. Input, output & data

- Validate and constrain all input at the boundary (Bean Validation); reject unknown JSON fields.
- Prevent injection: parameterized queries / JPA only — never string-concatenated SQL. Encode output.
- Never log secrets, tokens, passwords, or full PII. Redact `Authorization` headers in logs.
- Passwords (if this service stores them) are hashed with **bcrypt/argon2** — never reversible/plaintext.
- Enforce rate limiting on auth and other abuse-prone endpoints.

## 5. Secrets

- No secrets, keys, or credentials in source, `application.yml`, or the OpenAPI spec. Inject via
  environment / a secrets manager (Vault, cloud secret store). Config references them by name only.
- Rotate signing keys and support key rollover (JWKS with multiple keys / `kid` header).

## 6. Errors

- Auth failures return 401/403 problem details (see [error-handling.md](error-handling.md)) with a
  generic message — never reveal whether a user exists, why a token failed, or internal detail.
- Log auth failures server-side (with `correlationId`, `sub` if known) for audit — at WARN, without the token.
