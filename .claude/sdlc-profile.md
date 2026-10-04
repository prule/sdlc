# SDLC pipeline profile

<!--
Read by every sdlc-pipeline agent and skill (https://github.com/prule/sdlc-pipeline). It holds this
project's stack facts; the agents carry the roles. Detail lives in standards/ and domain/.
-->

## Commands

- **Verify:** `./gradlew build -x spotlessCheck` (compile, test on Testcontainers PostgreSQL, lint;
  Docker must be running). Every agent runs this to prove its work and pastes the output.
- **Code generation:** `./gradlew openApiGenerate` (bundles the split OpenAPI spec with redocly, then
  generates the Spring interfaces and DTOs). Run it after changing the contract.
- **Never run:** `./gradlew spotlessApply`, `./gradlew spotlessCheck`, `gradle publish`.

## Paths

- **Domain knowledge:** `domain/`
- **Standards:** `standards/`
- **Use cases:** `use-cases/`
- **Retrospectives:** `retrospectives/`
- **Session reports:** `reports/sessions/`

## Git

- **Base branch:** `develop`
- **Branch per use case:** `feat/uc-<n>-<slug>`, cut from `develop` before the pipeline starts
- **Pull requests target:** `develop`
- **Commits:** Conventional Commits

## Implementation rules

- **Contract-first:** edit the OpenAPI 3.1 spec in `src/main/resources/openapi/` first (split by
  domain), then run the code-generation command. Controllers implement the generated interfaces;
  never hand-write DTOs or controller interfaces that duplicate the contract (`standards/openapi.md`).
- **Clean Architecture:** dependencies point inward only; the domain package imports no Spring/JPA;
  JPA `@Entity` classes live only in `adapters/out/persistence` and are mapped to and from domain
  objects (`standards/clean-architecture.md`).
- **Database changes:** a new Flyway migration `V<n>__desc.sql` in `src/main/resources/db/migration`,
  compatible with both H2 and PostgreSQL; never edit an applied migration.
- **Testing:** useful tests (happy, edge, failure) for every requirement. Persistence-logic tests run
  on PostgreSQL via Testcontainers (extend the shared Postgres base); H2 tests only prove the default
  runtime wiring (`standards/testing.md` §3).
- **Errors:** RFC 7807 problem responses from the single `@RestControllerAdvice`
  (`standards/error-handling.md`).

## Task order

1. OpenAPI spec → 2. generate stubs → 3. domain → 4. application/ports → 5. outbound adapters +
   migration → 6. inbound controller → 7. tests at each layer

## Review checklist

### Plan review (spec-reviewer)

- **`standards/clean-architecture.md`:** layering is correct and inward-only; domain has no
  Spring/JPA; JPA entities only in `adapters/out`; components placed in the right layer.
- **`standards/openapi.md`:** contract-first (spec before code); split-by-domain layout (not one
  file); standard success envelope; RFC 7807 problem errors reused from common; generated
  interfaces, no hand-written DTOs.
- **`standards/error-handling.md`:** domain exception taxonomy with stable codes; single
  `@RestControllerAdvice`; correct code↔status mapping; correlation id; no leaking internals.
- **`standards/security.md`:** public, stateless API (no auth); operations marked `security: []`;
  rate limiting considered; no secrets in source or config.
- **`standards/testing.md`:** a useful test plan per requirement (happy, edge, failure); the
  per-layer pyramid; persistence logic tested on PostgreSQL via Testcontainers (§3).
- **`standards/formatting.md`:** Spotless/google-java-format wiring and the on-commit hook accounted
  for where relevant.

### Verification (qa)

- **`standards/testing.md`:** every requirement has a useful test (it would fail on regression) for
  the happy, edge and failure paths. A test that asserts persistence logic on H2 instead of
  PostgreSQL is a defect (§3). An unverified claim that a passing test depends on is a defect (§2).
- **`standards/error-handling.md`:** error responses are RFC 7807.
- **`standards/security.md`:** endpoints are public and stateless and send the required security
  headers.
- **`standards/openapi.md`:** success responses use the standard envelope.

### Code review (senior-dev)

- **`standards/clean-architecture.md` §9:** domain importing Spring/JPA, `@Entity` on a domain class,
  controllers with business logic or direct repository access, edited Flyway migrations.
- **`standards/openapi.md`:** one-file specs (must be split by domain), missing success envelope,
  non-RFC-7807 errors, hand-written DTOs duplicating the contract.
- **`standards/clean-code.md` §9:** god classes or methods, long parameter lists, boolean flag
  arguments, primitive obsession, returned `null`, swallowed exceptions.
- **`standards/error-handling.md`:** stack traces or internal detail leaked to clients, missing
  correlation id, HTTP status logic outside the global handler.
- **`standards/security.md`:** secrets in source or config, secrets or PII in URLs or logs, missing
  security headers, sessions or cookies introduced.
- **`standards/testing.md`:** missing edge or failure tests for a requirement, tautological tests,
  persistence logic tested on an engine other than PostgreSQL (§3).

## Formatting

google-java-format is applied by the pre-commit hook via Spotless (`standards/formatting.md`). Agents
never format code, never run `spotlessApply` or `spotlessCheck`, and never flag formatting; that is
why the verify command skips `spotlessCheck`. Disabling or bypassing Spotless is a defect.
