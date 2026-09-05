# Engineering Standards

These standards apply to all code written in this repository, by every agent.

## Stack
- **Language:** Java 25. Prefer records, sealed interfaces, pattern matching, and virtual threads where they simplify code.
- **Framework:** Spring Boot 3.x — Spring Web, Spring Data JPA, Bean Validation (`jakarta.validation`).
- **Database:** **H2 in-memory is the default local/demo runtime** (no profile active — zero external
  dependencies: `./gradlew bootRun` needs no Docker/Postgres). **PostgreSQL is the tested target and the
  production-capable target**, selected via the `postgres` profile (`--args='--spring.profiles.active=postgres'`
  or `SPRING_PROFILES_ACTIVE=postgres`); the automated test suite always runs against Postgres via
  Testcontainers, never H2 (see `standards/testing.md`). All schema changes via **Flyway** migrations in
  `src/main/resources/db/migration` (`V<n>__desc.sql`). Never edit an applied migration; add a new one. The
  migration set is a **single shared set that must stay H2- and Postgres-compatible** (no vendor split unless a
  future migration genuinely needs Postgres-only SQL). **Native-query UUID portability:** a native SQL query
  that selects a `uuid` column and casts the JDBC result straight to `java.util.UUID` works on Postgres but
  throws `ClassCastException` on H2 (which returns `byte[]` for `uuid` columns even in `MODE=PostgreSQL`).
  Any native query projecting an id column must `CAST(col AS varchar)` and parse with `UUID.fromString(...)`
  instead (see `MovieSearchPersistenceAdapter`/`PersonFilmographyJpaAdapter` for the pattern).
- **Build:** Gradle (Kotlin DSL). Common commands:
  - `./gradlew build` — compile + test + lint
  - `./gradlew test` — unit + integration tests
  - `./gradlew openApiGenerate` — regenerate API stubs from the OpenAPI spec
  - `./gradlew bootRun` — run on the H2 default; `./gradlew bootRun --args='--spring.profiles.active=postgres'` — run against Postgres
- **Testing:** JUnit 5 + AssertJ. Integration tests use **Testcontainers** against real Postgres — no H2, except
  exactly one dedicated runtime-config smoke test that boots the H2 default (see `standards/testing.md`). Web
  layer via MockMvc/WebTestClient.

## Architecture — Clean / Hexagonal
Package layout (dependencies point INWARD only):
```
com.acme.<feature>
  domain/        # entities, value objects, domain services — NO Spring/JPA imports
  application/   # use cases + ports (interfaces) — depends only on domain
  adapters/
    in/web/      # REST controllers implementing generated OpenAPI interfaces
    out/persistence/  # JPA entities + repository adapters implementing ports
```
- Domain must not import Spring, JPA, or anything from `adapters`.
- JPA entities live in `adapters/out/persistence` and are mapped to/from domain objects — do not annotate domain objects with `@Entity`.
- Business rules live in the domain, not in controllers or services-as-transaction-scripts.

**Full guide: [standards/clean-architecture.md](standards/clean-architecture.md)** — read it before writing or reviewing feature code (layer contents, allowed imports, request flow, per-layer testing, violations to reject).

## Contract-first (API-first)
- Define/modify the **OpenAPI 3.1** spec in `src/main/resources/openapi/` **before** writing controller code.
- Generate server interfaces and DTOs from it (`./gradlew openApiGenerate`); controllers implement the generated interfaces. Never hand-write DTOs that duplicate the contract.
- The OpenAPI file is the single source of truth for the HTTP contract.

## Conventions
- Constructor injection only (no field `@Autowired`).
- Immutable where practical; validate inputs at the boundary with Bean Validation.
- Meaningful errors via a global `@RestControllerAdvice`; return RFC 7807 problem details.
- One requirement → at least one test covering happy path, an edge case, and a failure path.
- Conventional Commits for commit messages.

## Standards (read the relevant guide before writing or reviewing code)
- **[standards/clean-architecture.md](standards/clean-architecture.md)** — layering, allowed imports, request flow, per-layer tests.
- **[standards/openapi.md](standards/openapi.md)** — contract-first; split spec by domain, standard success envelope + RFC 7807 errors.
- **[standards/error-handling.md](standards/error-handling.md)** — exception taxonomy, single `@RestControllerAdvice`, code↔status map, correlation ids.
- **[standards/security.md](standards/security.md)** — stateless JWT bearer auth, required claims, claim/tenant-based authorization, secrets handling.
- **[standards/clean-code.md](standards/clean-code.md)** — small single-responsibility classes, naming, immutability, review smells.
- **[standards/testing.md](standards/testing.md)** — useful tests for all new code, the per-layer pyramid, Testcontainers for all DB tests (no H2).
- **[standards/formatting.md](standards/formatting.md)** — google-java-format via Spotless, auto-formatted on commit (pre-commit hook), enforced in CI.

## Domain knowledge
Business/domain context (ubiquitous language, bounded contexts, actors, business rules) lives in
**[domain/](domain/)** — the "what & why" the code can't tell you. Read it before writing tickets or
plans so work uses the right language, personas, and rules. When a change introduces new domain
language or a durable rule, record it in `domain/`.
