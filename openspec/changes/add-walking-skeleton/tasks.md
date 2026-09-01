## 1. Build & project scaffolding

- [x] 1.1 Initialize Gradle (Kotlin DSL) project: `settings.gradle.kts`, `build.gradle.kts`, wrapper (`./gradlew`), Java 25 toolchain (`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`).
- [x] 1.2 Add Spring Boot 3.x plugin + dependency management; add starters: web, security, oauth2-resource-server, data-jpa, validation; runtime: flyway-core, postgresql; test: spring-boot-starter-test, testcontainers (junit-jupiter + postgresql), assertj.
- [x] 1.3 Create the Clean/Hexagonal source tree under `com.acme` (`platform/health/{domain,application,adapters}`, `common/{error,web,security,test}`) with package-info or placeholder so the layout compiles.
- [x] 1.4 Add the Spring Boot main application class (`com.acme.Application`) and a minimal `application.yml` (datasource + flyway + oauth2 issuer-uri referenced via env vars, no secrets committed).
- [x] 1.5 Wire Spotless with **google-java-format** (pinned) per standards/formatting.md: `removeUnusedImports()`, `trimTrailingWhitespace()`, `endWithNewline()`, target `src/**/*.java`; add `spotlessCheck` to `check` so `./gradlew build` fails on unformatted code, and confirm `./gradlew spotlessApply` reformats.
- [x] 1.6 Add an in-repo `git-hooks/pre-commit` that runs `./gradlew spotlessApply` on staged `*.java` and re-stages them (so unformatted code never enters history), and a one-command `./gradlew installGitHooks` task that installs it into `.git/hooks`; verify a fresh clone is formatting-enabled in one step.

## 2. OpenAPI contract (contract-first, before controller code)

- [x] 2.1 Create root `src/main/resources/openapi/openapi.yaml`: `info`, `servers` (`/api/v1`), global bearer `security`, `tags`, `$ref` into `paths/health.yaml`.
- [x] 2.2 Create `components/schemas/common.yaml` with `Envelope`, `Meta`, `Pagination`, `Problem` exactly per standards/openapi.md §2–3.
- [x] 2.3 Create `components/responses/common.yaml` with `BadRequest`(400), `Unauthorized`(401), `Forbidden`(403), `NotFound`(404), `Conflict`(409), `UnprocessableEntity`(422), `TooManyRequests`(429), `InternalError`(500), each `application/problem+json` → `Problem`.
- [x] 2.4 Create `components/schemas/health.yaml` (`PingData` with `status`+`timestamp`; `PingEnvelope` = Envelope with `data: $ref PingData`) and `paths/health.yaml` (`GET /ping`, `operationId: ping`, `security: []`, 200 → `PingEnvelope`, 500 → `InternalError`).
- [x] 2.5 Document `X-Correlation-Id` contract-first as reusable components: a request `parameter` in `components/parameters/common.yaml` (`CorrelationId`, optional inbound header) and a response `header` in `components/headers/common.yaml` (`CorrelationId`); reference them from the ping operation's request and 200 response so the contract advertises the correlation-id round-trip.

## 3. Code generation wiring

- [x] 3.1 Add the `org.openapi.generator` Gradle plugin; configure the `spring` generator (`interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`), input = root openapi.yaml, output = `build/generated`.
- [x] 3.2 Add `build/generated/src/main/java` to the main source set and make `compileJava` depend on `openApiGenerate`; confirm `PingApi` + envelope/problem DTOs generate and are excluded from Spotless/VCS.

## 4. Domain layer

- [x] 4.1 Implement `platform.health.domain.model.PingStatus` (record/value object carrying status + timestamp), no Spring/JPA imports; enforce invariants in the factory.

## 5. Application layer (ports + use case)

- [x] 5.1 Define inbound port `platform.health.application.port.in.PingUseCase`.
- [x] 5.2 Implement `platform.health.application.service.PingService` (`@Service`) implementing `PingUseCase`, using an injected `Clock`, returning the domain `PingStatus`; depends only on domain + JDK.

## 6. Persistence baseline (Flyway) + Testcontainers base

- [x] 6.1 Add Flyway baseline migration `src/main/resources/db/migration/V1__baseline.sql` — a real no-op DDL (e.g. `COMMENT ON SCHEMA public IS 'walking-skeleton baseline';`) so the integration test proves a migration actually executes; establishes migration history; no application tables.
- [x] 6.2 Implement `common.test.PostgresIntegrationTest` abstract base per standards/testing.md §3: `@Testcontainers` + `@SpringBootTest`, a singleton static `@Container PostgreSQLContainer` pinned to `postgres:16-alpine` (shared across the suite, not restarted per class), `@DynamicPropertySource` wiring `spring.datasource.*` + `spring.flyway` to the container so Flyway runs against it. Explicitly exclude H2 / any in-memory DB from the test classpath and config.

## 7. Cross-cutting: correlation id, error handling, security

- [x] 7.1 Implement `common.web.CorrelationIdFilter` (`OncePerRequestFilter`, high precedence): adopt `X-Correlation-Id` or generate a UUID; set MDC + response header; clear MDC in `finally`.
- [x] 7.2 Implement the sealed `common.error.DomainException` (carries stable `code`) plus initial subtypes (`ResourceNotFoundException`, `ValidationException`, `ConflictException`).
- [x] 7.3 Implement `common.error.GlobalExceptionHandler` (`@RestControllerAdvice`) mapping exceptions → RFC 7807 `ProblemDetail` per the code↔status table (incl. validation 422 with `errors[]` and a catch-all 500 that logs the trace and returns a generic body with the correlation id).
- [x] 7.4 Implement `common.security.PublicEndpoints` (single source of truth for public path patterns) and `common.security.SecurityConfig`: stateless OAuth2 resource server (`jwt()`), default `authenticated()`, permit the public endpoints; add `JwtAuthenticationConverter` mapping `roles`→`ROLE_*` and `scope`→`SCOPE_*`.
- [x] 7.5 Implement `common.security.ProblemAuthenticationEntryPoint` (`AuthenticationEntryPoint`, 401 `UNAUTHENTICATED`) and `common.security.ProblemAccessDeniedHandler` (`AccessDeniedHandler`, 403 `FORBIDDEN`); register them on the resource-server config via `exceptionHandling(...)`. Each renders the shared `Problem` shape (`application/problem+json`, `correlationId` from MDC) per error-handling.md §3 — because `@RestControllerAdvice` cannot catch filter-chain security exceptions — and emits a WARN-level auth-failure audit log line (correlationId, `sub` if known, never the token) per security.md §6.
- [x] 7.6 Add a `JwtDecoder` bean with a `DelegatingOAuth2TokenValidator` composing the issuer defaults (signature, `iss`, `exp`, `nbf`) with a `common.security.AudienceValidator` that enforces `aud`; supply the expected audience via config `security.jwt.expected-audience` (env-injected, not committed) per security.md §1 and §5.

## 8. Inbound controller (vertical slice)

- [x] 8.1 Implement `platform.health.adapters.in.web.PingController` implementing the generated `PingApi`: call `PingUseCase`, read the correlation id from MDC, map domain result → `PingEnvelope` DTO; thin, no business logic.

## 9. Tests at each layer (per standards/testing.md pyramid)

- [x] 9.1 Domain unit test: `PingStatus` invariants — plain JUnit 5 + AssertJ, no Spring, no I/O.
- [x] 9.2 Application unit test: `PingService` with a fixed `Clock` (happy path + timestamp assertion); ports mocked where a seam exists (ping has none).
- [x] 9.3 Web-slice test: `@WebMvcTest(PingController)` + MockMvc, `PingUseCase` port mocked — assert 200, envelope shape, `data.status == "ok"`, `meta.correlationId` present/valid UUID. Assert the narrowed public-endpoint contract: served with **no** token AND with a **valid** token (neither returns 401/403).
- [x] 9.4 Correlation-id test: assert a supplied `X-Correlation-Id` is echoed in `meta.correlationId` and that a header-less request still returns a valid UUID.
- [x] 9.5 Error-handling test: (a) request an unknown route → `application/problem+json` body with `status`, stable `code`, `correlationId`, and no stack trace/internal detail; (b) request a **protected** path with a missing/invalid token → `application/problem+json` with code `UNAUTHENTICATED` (401), and an authenticated-but-forbidden request → code `FORBIDDEN` (403), proving the custom entry point / access-denied handler render Problem bodies.
- [x] 9.6 Full-flow integration test: `@SpringBootTest` extending `PostgresIntegrationTest` (real Postgres via Testcontainers, no H2) — context loads, Flyway migrates (the V1 no-op DDL executes), DB connects.
- [x] 9.7 Consistency test: assert `PublicEndpoints` and the OpenAPI operations marked `security: []` agree (every public pattern maps to a `security: []` operation and vice versa), so the two sources of truth cannot drift.

## 10. CI

- [x] 10.1 Add a CI workflow (on push) running `./gradlew build` on a runner with a Docker daemon (for Testcontainers); `build` includes `spotlessCheck`, so a bypassed/missing pre-commit hook still fails CI (per standards/formatting.md §3).
- [x] 10.2 Add an OpenAPI lint/validate step to CI so an invalid or drifted spec fails the build.

## 11. Verification

- [x] 11.1 Run `./gradlew build` locally — compiles, tests, and lints green.
- [x] 11.2 Run the app and `curl http://localhost:8080/api/v1/ping` — returns 200 with the standard envelope (`data.status`, `data.timestamp`, `meta.correlationId`).
- [x] 11.3 Run `openspec validate add-walking-skeleton` — passes.
