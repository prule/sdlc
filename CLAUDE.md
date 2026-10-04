# Engineering Standards

These rules apply to all work in this repository, by every agent. This file is the short index;
the detail lives in `standards/` (how we build) and `domain/` (what things mean). Read the relevant
documents before planning, writing or reviewing.

## Stack
- **Java 25** (records, sealed types, pattern matching, virtual threads where they simplify) ·
  **Spring Boot 3.x** (Web, Data JPA, Bean Validation) · **Gradle** (Kotlin DSL).
- **Database:** H2 in-memory is the default local/demo runtime (no profile). PostgreSQL is the tested
  and production target (`postgres` profile). Tests run on the target database:
  persistence logic on PostgreSQL via Testcontainers; H2 tests only prove the default runtime wiring
  (`standards/testing.md` §3). Schema changes are
  Flyway migrations in `src/main/resources/db/migration`, one set compatible with both; never edit an
  applied migration.
- **Commands:** `./gradlew build` (compile, test, lint) · `./gradlew test` ·
  `./gradlew openApiGenerate` · `./gradlew bootRun` (H2) · `npm ci` once for the OpenAPI bundler.

## How we build — `standards/`
- **[clean-architecture.md](standards/clean-architecture.md)**: layers point inward; domain has no
  Spring/JPA; values are always bound in queries.
- **[openapi.md](standards/openapi.md)**: contract-first, split by domain, success envelope, HAL links,
  paging; controllers implement generated interfaces.
- **[error-handling.md](standards/error-handling.md)**: one `@RestControllerAdvice`, RFC 7807, the
  code↔status table, correlation ids.
- **[security.md](standards/security.md)**: stateless JWT, claims, ownership/tenant checks, secrets.
- **[clean-code.md](standards/clean-code.md)**: small classes, naming, immutability, review smells.
- **[testing.md](standards/testing.md)**: useful tests for every requirement, the per-layer pyramid,
  database tests on the target database (Testcontainers), production framework wiring.
- **[formatting.md](standards/formatting.md)**: google-java-format via Spotless, applied by the
  pre-commit hook. Agents never format; they build with `-x spotlessCheck`.

## What things mean — `domain/`
- **[glossary.md](domain/glossary.md)**: the ubiquitous language. Use these terms in use cases,
  specs, and class and method names.
- **[business-rules.md](domain/business-rules.md)**: durable rules (read-only, paging, search
  matching, …) that every capability must respect.
- **[bounded-contexts.md](domain/bounded-contexts.md)**, **[actors-and-personas.md](domain/actors-and-personas.md)**,
  **[overview.md](domain/overview.md)**: what lives where, and who the users are.

When a change introduces a new term or a durable rule, record it in `domain/` as part of the change.

## Conventions
- Constructor injection only; immutable where practical; validate at the boundary.
- Contract-first: change `src/main/resources/openapi/` before controller code.
- Conventional Commits. Work on a branch; PRs target `develop`.
- Commit every `reports/sessions/` report and `retrospectives/` record with the change.

## Delivery pipeline
`/build-ticket` runs the agents in `.claude/agents/` through OpenSpec (`openspec/config.yaml` holds the
design-time rules). Every run writes a record in `retrospectives/`. See `README.md`.
