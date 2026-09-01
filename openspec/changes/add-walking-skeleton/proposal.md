## Why

This is a brand-new empty repo: nothing exists yet except the standards docs, agent
config, and OpenSpec. Before any business feature can be built as a standards-compliant
vertical slice, the platform it slices through must exist — a build that compiles and is
green in CI, the Clean/Hexagonal package skeleton, the contract-first OpenAPI toolchain,
the shared error/envelope/security/persistence baselines, and one trivial end-to-end
endpoint proving every layer wires together.

## What Changes

- **Build**: Gradle (Kotlin DSL) with a Java 25 toolchain and Spring Boot 3.x; `./gradlew
  build` compiles, tests, and lints green. Spotless wired for auto-format.
- **Package skeleton**: Clean/Hexagonal layout under base package `com.acme` per
  standards/clean-architecture.md (`domain` / `application` / `adapters/in/web` /
  `adapters/out/persistence`), with the dependency rule pointing inward only.
- **Contract-first OpenAPI 3.1 base**: root `openapi.yaml` + split `paths/` and
  `components/` layout; shared `components/schemas/common.yaml` (Envelope, Meta,
  Pagination, Problem) and `components/responses/common.yaml`
  (400/401/403/404/409/422/429/500). `openApiGenerate` wired into the build so controllers
  implement generated interfaces and never hand-write DTOs.
- **Global error handling**: a single `@RestControllerAdvice` producing RFC 7807
  `application/problem+json`, a sealed `DomainException` taxonomy, and a correlation-id
  filter that stamps every request/response and log line.
- **Security baseline**: stateless OAuth2 resource server (JWT bearer) with a
  `JwtAuthenticationConverter` mapping roles/scopes to authorities; default-deny with an
  explicit mechanism to mark public endpoints.
- **Persistence baseline**: Flyway wired with a baseline migration; a Testcontainers
  Postgres base test class that other integration tests extend (no H2).
- **CI**: pipeline running `./gradlew build` plus OpenAPI spec lint on push.
- **Vertical slice** (the only user-observable behavior): a public `GET /api/v1/ping`
  returning the standard success envelope with a small payload (status + timestamp +
  correlationId), implemented controller → use case → domain, with unit + web-slice tests.

No breaking changes: greenfield repo, first change, nothing to break. First DB migration
is the additive Flyway baseline.

## Capabilities

### New Capabilities
- `platform/health-check`: a public, unauthenticated liveness endpoint (`GET /api/v1/ping`)
  that returns the standard success envelope carrying a status, a server timestamp, and the
  request's correlation id — proving the request path traverses every layer (controller →
  use case → domain) and that the envelope, correlation-id, and public-endpoint mechanisms
  work end to end.

### Modified Capabilities
<!-- None. Greenfield repo; this is the first change, no existing capability requirements change. -->

## Impact

- **New OpenAPI operation** (public, `security: []`): `GET /api/v1/ping`. Non-breaking
  (first endpoint in a greenfield API). Establishes the split-spec layout and shared
  Envelope/Problem components every later endpoint reuses via `$ref`.
- **New DB schema (Flyway)**: a baseline migration (`V1__baseline.sql`) establishing the
  migration history. Additive; no applied migration is edited.
- **New build & CI**: Gradle Kotlin DSL project, `openApiGenerate`, Spotless, and a CI
  workflow — the foundation every subsequent feature builds on.
- **New code (skeleton + one slice)**: `com.acme.platform.health` domain/application/
  adapters, plus cross-cutting `com.acme.common` (error handling, correlation-id filter,
  security config, Testcontainers base test class).
- **New dependencies**: Spring Boot Web, Security (OAuth2 resource server), Data JPA,
  Validation, Flyway, PostgreSQL driver, JUnit 5, AssertJ, Testcontainers,
  openapi-generator, Spotless.

## Non-goals

- Any real business feature or domain logic beyond the trivial `ping` slice.
- User/account modeling, authentication issuance, login, or refresh-token flows (the
  service only *verifies* JWTs as a resource server; it issues none).
- Password policy, real email/SMTP provider, or notification infrastructure.
- Distributed infrastructure (Redis, message brokers, distributed rate limiting).
- Production deployment or containerization of the app beyond the CI build (no Dockerfile,
  no deploy pipeline, no k8s manifests).
