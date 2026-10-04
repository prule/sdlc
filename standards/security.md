# Security Standard

Baseline security rules for every service. The API is public: there is no authentication, no user
accounts and no tokens (`domain/business-rules.md`). Security comes from the transport, the boundary,
abuse controls and secrets hygiene below.

## 1. Access

- Every endpoint is public and unauthenticated. The security filter chain is stateless (no session,
  no cookies), with HTTP Basic, form login and logout disabled.
- Mark each operation `security: []` in OpenAPI so the contract states that it is public.
- **Rate limiting** is the primary abuse control for public access (per client).
- Adding an authenticated surface is a design decision: it amends this standard and `domain/` first.

## 2. Transport & headers

- HTTPS only; HSTS enabled. No secrets in URLs/query strings (they get logged).
- Set security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a restrictive CSP.
- CORS is an explicit allow-list of origins/methods.

## 3. Input, output & data

- Validate and constrain all input at the boundary (Bean Validation); reject unknown JSON fields.
- Prevent injection: parameterized queries / JPA only — never string-concatenated SQL. Encode output.
- Never log secrets or full PII.

## 4. Secrets

- No secrets, keys, or credentials in source, `application.yml`, or the OpenAPI spec. Inject via
  environment / a secrets manager (Vault, cloud secret store). Config references them by name only.

## 5. Errors

- Errors are problem details (see [error-handling.md](error-handling.md)) with a generic message —
  never internal detail, stack traces or infrastructure names.
