## Context

Brand-new empty repo — only the standards docs (`standards/`), `CLAUDE.md`, and OpenSpec
exist. See proposal.md — Why. There is no code to delta against; this change stands up the
platform (build, layering, contract toolchain, cross-cutting baselines) plus one trivial
vertical slice that exercises every standard end to end. All later features are added as
vertical slices against this foundation, so the choices here are load-bearing.

## Goals / Non-Goals

**Goals:**
- A standards-compliant, green-in-CI foundation: `./gradlew build` compiles, tests, lints.
- Prove — not just assert — that every standard wires together, via the `GET /api/v1/ping`
  slice traversing controller → use case → domain and returning the shared envelope.
- Establish the reusable seams later features plug into: split OpenAPI layout with shared
  Envelope/Problem components, sealed `DomainException` taxonomy + global advice, correlation
  id filter, security config with a public-endpoint switch, and a Testcontainers base class.

**Non-Goals (design-level):**
- No domain logic beyond the trivial ping. No persistence *of* ping data — the Flyway
  baseline exists to establish migration history and prove the DB connection, not to store
  anything. The `platform/health` slice does not touch the database on the request path.
- No opinion on the concrete JWT issuer; the resource server is configured against a
  configurable issuer/JWKS URI supplied by environment, not committed.

## Decisions

### D1. openapi-generator with the `spring` generator, interface-only
Use `org.openapi.generator` Gradle plugin, `generatorName = "spring"`, with
`interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`. Controllers implement the
generated `PingApi` interface; DTOs (Envelope, Problem, etc.) are generated from
`components/schemas`. Generated sources go to `build/generated` and are added to the main
source set (never committed, never hand-edited).
- Alternative considered: springdoc (code-first, spec derived from annotations) — rejected;
  it inverts the contract-first rule in standards/openapi.md.

### D2. OpenAPI split layout (added operation)
Root `openapi.yaml` holds `info`, `servers` (`/api/v1`), global `security` (bearer), and
`$ref`s into `paths/`. The one added operation is **public**:

```yaml
# paths/health.yaml
paths:
  /ping:
    get:
      operationId: ping
      tags: [health]
      summary: Liveness ping
      security: []            # public — overrides global bearer security
      responses:
        '200':
          description: Service is alive
          content:
            application/json:
              schema: { $ref: '../components/schemas/health.yaml#/PingEnvelope' }
        '500': { $ref: '../components/responses/common.yaml#/InternalError' }
```

`components/schemas/common.yaml` defines `Envelope`, `Meta`, `Pagination`, `Problem`
(verbatim shapes from standards/openapi.md §2–3). `health.yaml` defines `PingData`
(`status`, `timestamp`) and `PingEnvelope` (Envelope with `data: $ref PingData`).
`components/responses/common.yaml` defines `BadRequest`(400), `Unauthorized`(401),
`Forbidden`(403), `NotFound`(404), `Conflict`(409), `UnprocessableEntity`(422),
`TooManyRequests`(429), `InternalError`(500), each `application/problem+json` → `Problem`.

### D3. Package layout and component naming (dependency rule: inward only)
Base package `com.acme`. The slice lives under `com.acme.platform.health`; cross-cutting
scaffolding under `com.acme.common`.

- `com.acme.platform.health.domain.model.PingStatus` — domain value (record); no Spring/JPA.
- `com.acme.platform.health.application.port.in.PingUseCase` — inbound port (interface).
- `com.acme.platform.health.application.service.PingService` — implements `PingUseCase`;
  depends only on domain + JDK clock. Returns a domain result (status + timestamp).
- `com.acme.platform.health.adapters.in.web.PingController` — implements generated `PingApi`;
  maps domain result + correlation id → generated `PingEnvelope` DTO. Thin, no logic.

Confirmed inward-only: domain imports nothing outer; application imports only domain; the
controller imports the application inbound port + generated web DTOs. No layer imports an
outer one. `PingService` does NOT touch any outbound port (ping has no persistence).

Cross-cutting (`com.acme.common`):
- `common.error.DomainException` (sealed abstract, carries stable `code`), with initial
  subtypes `ResourceNotFoundException`, `ValidationException`, `ConflictException` to seed the
  taxonomy for later features.
- `common.error.GlobalExceptionHandler` (`@RestControllerAdvice`) — the single place mapping
  exceptions → `ProblemDetail` per the code↔status table in standards/error-handling.md §3,
  including a catch-all 500 that logs the trace server-side and returns a generic body.
- `common.web.CorrelationIdFilter` (`OncePerRequestFilter`, high precedence) — reads the
  inbound `X-Correlation-Id`, else generates a UUID; puts it in the MDC (so it appears in
  log lines) and on the response header; clears MDC in `finally`.
- `common.security.SecurityConfig` — OAuth2 resource server (`oauth2ResourceServer().jwt()`),
  stateless session, default `authenticated()`, with `/api/v1/ping` (and actuator health if
  added) permitted. `JwtAuthenticationConverter` maps `roles` → `ROLE_*` authorities and
  `scope` → `SCOPE_*` authorities.
- `common.security.ProblemAuthenticationEntryPoint` (implements `AuthenticationEntryPoint`)
  and `common.security.ProblemAccessDeniedHandler` (implements `AccessDeniedHandler`),
  registered on the resource-server config via
  `.exceptionHandling(e -> e.authenticationEntryPoint(...).accessDeniedHandler(...))`.
  **Rationale (fixes BLOCKER 1):** `@RestControllerAdvice` only catches exceptions inside the
  `DispatcherServlet`; `AuthenticationException`/`AccessDeniedException` are raised earlier in
  the Spring Security filter chain (`ExceptionTranslationFilter`) and would otherwise emit
  Spring's default non-Problem bodies. These two components render the shared `Problem` shape
  directly — `application/problem+json`, `correlationId` read from MDC, code `UNAUTHENTICATED`
  → 401 and `FORBIDDEN` → 403 per error-handling.md §3 — so 401/403 match
  `components/responses/common.yaml`. Both emit a **WARN-level auth-failure audit log line**
  (with `correlationId`, and `sub` if known, never the token) per security.md §6.
- `common.security.AudienceValidator` + a `JwtDecoder` bean using a delegating
  `OAuth2TokenValidator` (`DelegatingOAuth2TokenValidator`) that composes the default
  validators (`JwtValidators.createDefaultWithIssuer` → signature, `iss`, `exp`, `nbf`) with
  an **audience (`aud`) validator**. **Rationale (fixes BLOCKER 3):** issuer-uri auto-config
  does not validate `aud`, which security.md §1 requires. The expected audience is supplied via
  env/config (`security.jwt.expected-audience`), never committed (security.md §5).
- **Ping vs. the JWT filter (BLOCKER 2 decision):** the resource server's
  `BearerTokenAuthenticationFilter` rejects a *malformed/invalid* bearer token with 401 even on
  a `permitAll` path. Therefore the public-endpoint guarantee is narrowed to what actually
  holds: with **no** token, or with a **valid** token, `/api/v1/ping` is served (never
  401/403); a request bearing a malformed token is out of the public-endpoint contract. The
  spec scenario and tests are aligned to this contract.
- `common.security.PublicEndpoints` — a single constant list of public path patterns, the
  one source of truth shared by `SecurityConfig`; OpenAPI marks the same operations
  `security: []`. A test asserts the two agree (patterns permitted in config ⇔ operations with
  `security: []` in the spec).

### D4. Correlation-id contract
Header name `X-Correlation-Id`. The filter is the single generator/echoer; the controller
reads the current correlation id (from MDC) to populate `meta.correlationId`, and the global
handler reads it to populate `Problem.correlationId`. This keeps one id per request across
the success envelope, error bodies, and logs. **Contract-first:** the header is documented in
the OpenAPI spec as a reusable component — a request `parameter` (`components/parameters/common.yaml#/CorrelationId`,
optional inbound header) and a `header` on responses (`components/headers/common.yaml#/CorrelationId`)
— so the contract advertises that every request may supply and every response echoes it.

### D5. Testcontainers base class, no H2 (per standards/testing.md §3)
`com.acme.common.test.PostgresIntegrationTest` — abstract base annotated with
`@Testcontainers` + `@SpringBootTest`, a singleton static `@Container PostgreSQLContainer`
pinned to `postgres:16-alpine`, and `@DynamicPropertySource` wiring `spring.datasource.*` +
`spring.flyway` to the container. Integration tests extend it so Flyway runs against real
Postgres and the container is shared across the suite (static, singleton) rather than
restarted per class. H2 (or any in-memory substitute) is **banned** — it hides dialect
differences, real constraints, and migration bugs; every DB-touching test uses this base.

### D6. Testing the slice at each layer (per standards/testing.md §2 pyramid)
Every requirement in the spec delta gets happy-path + edge + failure tests; tests assert
observable behavior (HTTP response, returned value), inject `Clock` for determinism, and
mock only at architectural seams (the inbound port in the web slice).
- Domain: plain JUnit 5 + AssertJ unit test of `PingStatus` invariants — no Spring, no I/O.
- Application: unit test of `PingService` with a fixed `Clock` and the inbound port
  exercised directly — asserts `status == ok` and the timestamp (ports mocked where any
  outbound seam exists; ping has none).
- Web slice: `@WebMvcTest(PingController.class)` + MockMvc, `PingUseCase` port mocked —
  asserts 200, envelope shape, `data.status`, `meta.correlationId` present, and that the
  endpoint is reachable without a bearer token (public rule honored). Correlation-id and
  problem-detail (unknown route) behavior are asserted here too.
- Full-flow integration (sparingly): one `@SpringBootTest` extending the Testcontainers base
  to prove the context loads, Flyway migrates, and the DB connects (the "walking" part of the
  skeleton). This is the one end-to-end test; the pyramid stays weighted to fast unit tests.

### D7. Formatting (google-java-format via Spotless) + CI (per standards/formatting.md)
Java is formatted with **google-java-format** (the single canonical style — resolves the
earlier formatter open question) wired through the Spotless Gradle plugin, with
`removeUnusedImports()`, `trimTrailingWhitespace()`, `endWithNewline()`, targeting
`src/**/*.java`. Both google-java-format and Spotless versions are **pinned** for
reproducible formatting (an unpinned bump reformats the whole tree). `spotlessCheck` is part
of `check`, so `./gradlew build` fails on unformatted code; `spotlessApply` reformats.
- **Auto-format on commit**: an in-repo git **pre-commit hook** (managed under a `git-hooks/`
  directory, not left to manual setup) runs `spotlessApply` on staged `*.java` files and
  re-stages them so unformatted code never enters history. A one-command Gradle task
  `./gradlew installGitHooks` installs it so a fresh clone is formatting-enabled in one step.
- **CI backstop**: CI runs `./gradlew build` (which includes `spotlessCheck`) on push, so a
  hook that was bypassed (`--no-verify`) or missing still fails CI — the hook is the
  convenience, CI is the guarantee. CI also runs an OpenAPI lint/validate step
  (`openApiGenerate`/validate, or Spectral) so a drifted or invalid spec fails the build.
  Generated sources and formatted output stay reproducible because versions are pinned.

## Risks / Trade-offs

- **Java 25 + Spring Boot 3.x + generator toolchain compatibility** → pin exact versions in
  the build (see Open Questions) and let CI be the gate; the walking skeleton's whole point
  is to surface version friction now, cheaply, before features depend on it.
- **Generated sources on the source path can confuse IDEs / first build** → mark
  `build/generated` as generated, make compile depend on `openApiGenerate`, never commit it.
- **Testcontainers requires a Docker daemon in CI** → CI runners must provide Docker; document
  it in the CI workflow. No H2 fallback (standards mandate real Postgres).
- **Security default-deny could accidentally lock out the public ping** → the single
  `PublicEndpoints` list + a web-slice test asserting ping is served with no token and with a
  valid token, plus a consistency test that `PublicEndpoints` and the OpenAPI `security: []`
  operations agree, guard against this.
- **Filter-chain auth errors bypass `@RestControllerAdvice`** → a custom
  `AuthenticationEntryPoint`/`AccessDeniedHandler` render the shared Problem shape for 401/403;
  a test asserts a protected path returns `application/problem+json` with the right code.
- **Correlation id via MDC + virtual threads** → MDC is thread-bound; keep the id set for the
  request scope only and clear it in `finally`; acceptable for the synchronous request path.

## Migration Plan

- **DB**: one additive Flyway baseline migration `src/main/resources/db/migration/V1__baseline.sql`
  (empty/comment or a trivial `flyway_schema_history` no-op) establishing migration history.
  Applied automatically on startup and in the Testcontainers integration test. No existing
  migration is edited (there are none). **Rollback**: drop the schema / recreate the database;
  since the baseline creates no application tables, rollback is trivial and there is no data
  to preserve. Later features add `V2__…`, `V3__…`; never edit an applied migration.
- **Deploy**: not in scope beyond CI build (see proposal Non-goals). No container image or
  deploy pipeline is produced by this change.

## Open Questions

- Exact pinned versions (Spring Boot patch, openapi-generator, google-java-format + Spotless,
  Java 25 vendor/toolchain, `postgres:16-alpine` patch) — deferrable to implementation; CI
  green is the acceptance gate and the choice does not change the specs, approach, or task
  breakdown. (The formatter *choice* is resolved: google-java-format per standards/formatting.md.)
  The JWT issuer-uri and expected `aud` *values* are env/config-supplied per deployment (not
  committed) — a deployment input, not an open design question.
