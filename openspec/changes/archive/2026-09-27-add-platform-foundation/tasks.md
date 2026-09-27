## 0. Setup

- [x] 0.1 Run `npm ci` and `./gradlew installGitHooks`. Verify: `node_modules/.bin/redocly` exists and `.git/hooks/pre-commit` exists and is executable.

## 1. OpenAPI contract

- [x] 1.1 Author `src/main/resources/openapi/openapi.yaml` (root: info with the inbound `X-Correlation-Id` note, `servers: [/api/v1]`, `Health` tag, `$ref` to `paths/health.yaml`) and `paths/health.yaml` (`GET /ping`, `operationId: ping`, `security: []`, 200 plus the shared 406 and 500), per design D1. Verify: `npx --no-install redocly lint src/main/resources/openapi/openapi.yaml` passes.
- [x] 1.2 Author only the referenced shared components (D1 component policy): `components/schemas/common.yaml` (`Meta`, `Link`, `Problem`), `components/responses/common.yaml` (`NotAcceptable`, `InternalError`), `components/headers/common.yaml` (`X-Correlation-Id`), and `components/schemas/health.yaml` (`PingEnvelope`, `PingData`, `PingLinks`, all closed). Verify: lint passes with no unused-component warning, and `./gradlew bundleOpenApiSpec` writes `build/openapi/openapi.bundled.yaml` containing each of these components once and no external `$ref`s.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate`. Verify that `com.acme.generated.api.HealthApi` has `ping()` returning `PingEnvelope`, and that no `*200Response*` models exist.
- [x] 2.2 Add `GeneratedApiCodegenTest`, which asserts the above on the generated sources. Verify: the test passes, and it fails if it is pointed at a class named `Ping200Response`.

## 3. Domain

- [x] 3.1 Add `com.acme.platform.availability.domain.model.Availability` (record) and `AvailabilityStatus` (enum `UP`), with JDK imports only. Verify: a unit test asserts that the record rejects a null status.

## 4. Application / ports

- [x] 4.1 Add the inbound port `CheckAvailabilityUseCase` and `AvailabilityService` (`@Service`, tolerated by clean-architecture.md §3; returns `Availability(UP)`; no outbound port). Verify: a unit test asserts `UP` with no Spring context.

## 5. Outbound adapters + migration

- [x] 5.1 No outbound adapter and no Flyway migration (design D8). Verify: `src/main/resources/db/migration` has no new files, and the application starts on H2 with Flyway reporting no migrations.

## 6. Inbound web + platform infrastructure

- [x] 6.0 Add the `@PlatformWebTest` meta-annotation (a `@WebMvcTest` slice importing `SecurityConfig`, `CorrelationIdFilter`, `GlobalExceptionHandler`, `ProblemFactory`, `ClockConfig`) and a test-only controller under `src/test` with three operations: one that throws, one with a required UUID query param, and one that consumes JSON. Verify: a trivial slice test using the annotation boots.
- [x] 6.1 Add `ClockConfig` (UTC `Clock` bean) and `CorrelationIdFilter` + `CorrelationId` (D6). The filter must override `shouldNotFilterErrorDispatch()` to return `false`, and must reuse the stored id on an ERROR dispatch. Also add a `logging.pattern.level` that includes `%X{correlationId}`. Verify: filter unit tests cover the absent, valid and malformed header cases, plus the ERROR dispatch keeping the original id.
- [x] 6.2 Add `SecurityConfig` (D4: permitAll, CSRF off, stateless, basic/form login off, nosniff, frame DENY, CSP, default HSTS), set `server.forward-headers-strategy: framework`, and add no CORS config. Verify: `@PlatformWebTest` tests show that `POST /ping` returns 405 (not 401 or 403), that the headers are present, that HSTS appears only with `X-Forwarded-Proto: https`, and that a foreign `Origin` gets no `Access-Control-Allow-Origin`.
- [x] 6.3 Add `ProblemFactory` (the status → kind table from D3, fixed safe details), `GlobalExceptionHandler extends ResponseEntityExceptionHandler` (overriding `handleExceptionInternal`/`createResponseEntity`, plus one catch-all), `ProblemInstanceStrippingAdvice` (a `ResponseBodyAdvice<ProblemDetail>` that nulls `instance` in `beforeBodyWrite`), and `ProblemErrorController implements ErrorController` (so Boot's `BasicErrorController` backs off). Set `server.error.whitelabel.enabled: false`. Verify these tests:
  - 404 (`GET` and `PUT` on an unknown path)
  - 405 on `/ping`, with `Allow` including `GET`
  - 405 on `/openapi/openapi.bundled.yaml` and `/swagger-ui/index.html`, with `Allow: GET, HEAD` exactly
  - 406 (`GET /ping` with `Accept: application/xml` returns problem+json, not 500)
  - `GET /ping` with `Accept: application/problem+json` returns 200 with `Content-Type: application/json`
  - 415 (the test-only JSON consumer)
  - 400 (the test-only UUID param, both missing and malformed)
  - 500 (the test-only throwing operation)

  Each failure test asserts every required Problem member, the absence of `instance` in the serialized body, a distinct `type` and `code`, and that no internals leak.
- [x] 6.4 Add `ResponseMetaFactory` and `PingController implements HealthApi` (self link via `WebMvcLinkBuilder`). Verify: a `@PlatformWebTest` with a fixed clock asserts the exact envelope (`status` UP, `self` href, `meta.timestamp`, `meta.correlationId` equal to the header), that `data` contains no other keys, and that forwarded headers yield `https://api.example.test/api/v1/ping`.
- [x] 6.5 Set `management.server.port: -1` in `application.yml` (D5). Verify: `GET /api/v1/actuator`, `/api/v1/actuator/health` and `/api/v1/v3/api-docs` each return 404 problem+json.
- [x] 6.6 Move the Swagger UI inline script to `static/swagger-ui/swagger-initializer.js` and reference it from `index.html`. Verify: the page and all referenced assets return 200, the page has no inline `<script>` body, and a manual try-it-out on `/ping` under the CSP returns 200 (Gate 2).

## 7. Tests at each layer (cross-cutting)

- [x] 7.1 Add a parameterised read-only test sending POST, PUT, PATCH and DELETE with no CSRF token and no credentials. Expected results:
  - on `/ping`: 405, with `Allow` including `GET`
  - on `/openapi/openapi.bundled.yaml` and `/swagger-ui/index.html`: 405, with `Allow: GET, HEAD`
  - on an unknown path: 404 `NOT_FOUND`

  Every response must be problem+json, and none may be 2xx, 401, 403 or 5xx. Verify: the test passes.
- [x] 7.2 Add `InterfaceDescriptionContractTest` (test dependency `com.networknt:json-schema-validator`). It must:
  - validate real `/ping` 200, 406, 404 and 405 bodies against the schemas in the served bundle
  - assert that no problem body has `instance`
  - assert that every documented operation is routed
  - assert that `Meta`, `Link`, `Problem` and `X-Correlation-Id` are each defined once, and that all response schemas are `$ref`s
  - assert that `servers[0].url == /api/v1`

  Verify: the test passes, and validating a `/ping` body with an extra key injected by the test fails validation.
- [x] 7.3 Add the shared `Uc000Assertions` (`/ping`, both description forms, 404, 405, 406). Run it from `H2DefaultRuntimeSmokeTest` (no profile, the only H2 test) and from a `PostgresIntegrationTest` base plus `PersistentModeIntegrationTest` (`postgres` profile, Testcontainers). Verify: both tests pass.
- [x] 7.4 Add log-capture tests. The 500 path must log the correlation id and the stack trace. A failure rendered via `/error` (for example, a test filter that throws) must carry the same id in the header, the body and the log. The `/error` case needs `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a real HTTP client, because MockMvc performs no ERROR dispatch. Verify: the tests pass.
- [x] 7.5 Run `./gradlew build` (bundle, generate, compile, spotlessCheck, all tests). Verify: the build is green locally, and CI's `redocly lint` step passes.

## 8. Docs

- [x] 8.1 Add (Q1 approved at Gate 1) the 405/406/415 rows and the "other 4xx → 400" rule to `standards/error-handling.md` §3. Update `domain/bounded-contexts.md` `platform` to replace "(planned)" with the four capability names. Verify: the docs diff is reviewed at Gate 2.
