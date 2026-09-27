## Context

See proposal.md for the reasons behind this change. The current state that constrains the design:

- **Build is already wired.** `build.gradle.kts` does `redocly bundle` → `openApiGenerate` (spring, `interfaceOnly`, `useTags`, packages `com.acme.generated.{api,model}`) → `compileJava`. `processResources` copies the bundle to `static/openapi/`. The input `src/main/resources/openapi/openapi.yaml` is missing, so this change supplies it.
- **Classpath already includes:** web, security, oauth2-resource-server, data-jpa, validation, actuator, hateoas, the swagger-ui webjar, Flyway, and the H2 + PostgreSQL drivers. `application.yml` sets context path `/api/v1`, `non_null` Jackson, H2 as the default and Flyway on; `application-postgres.yml` sets PostgreSQL through environment variables.
- **Missing security config.** Without a `SecurityFilterChain`, Spring Boot's default security puts HTTP Basic on every endpoint and enforces CSRF on POST (which would produce 401/403). Both break BR-1/BR-5 and the 405 refusal.
- **Actuator is on by default.** It serves `/actuator` (a discovery page) and `/actuator/health`, both undocumented, which breaks BR-4.
- **The Swagger UI entry page uses an inline `<script>`.** That is incompatible with the CSP required by `standards/security.md` §3.
- **The domain deliberately diverges from `standards/security.md` JWT auth** (`domain/business-rules.md`): the read API is public.
- **`redocly bundle` only lifts reachable components.** Components are lifted into the bundle only if they are reachable by `$ref` from the root, so any shared component no operation uses is absent from the served description.

## Goals / Non-Goals

**Goals:** make the build green, and implement the four capability specs using layering that later `catalog` slices can copy. Shared web infrastructure is written once, in `platform`.

**Non-Goals (design-level):**
- No JWT decoder or bean.
- No `Pagination`, `page`/`size`, `NotFound`, `BadRequest`, `MethodNotAllowed` or `UnsupportedMediaType` components yet (see D1). Each arrives, defined once, with the first operation that references it.
- No readiness probe.
- No CORS configuration.
- No removal of unused dependencies (Q2, decided out of scope at Gate 1).

## Decisions

### D1. Availability check is `GET /ping`, tag `Health`, operationId `ping`. Components are defined only when referenced.
This follows the names already referenced in `standards/openapi.md` (`HealthApi.ping()` returning `PingEnvelope`, `PingData._links → PingLinks{self}`, `GeneratedApiCodegenTest`). Using them keeps the standard's codegen assertion valid as written. "Availability check" stays the business term (glossary). Alternative considered: `/availability`. Rejected because it would contradict the names the standard already fixes.

**Component policy (review finding 1, option a).** Because `redocly bundle` drops unreachable components, the authored split files contain only components that some operation references. That keeps the bundle equal to the authored set and avoids `no-unused-components` warnings. For `/ping`, the referenced set is:
- responses: `InternalError` (500) and `NotAcceptable` (406), both documented on `GET /ping` because both can really occur there
- the `X-Correlation-Id` header
- schemas: `Problem`, `Meta`, `Link`

The 404 and 405 answers are not responses of any documented operation (they concern undocumented paths or methods). Their bodies still conform to, and are validated against, the same `Problem` schema. `NotFound`, `BadRequest`, `MethodNotAllowed` and `UnsupportedMediaType` are added to `components/responses/common.yaml` by the first operation that can return them (for example, the first `/movies/{id}` gets `NotFound` and `BadRequest`). The "defined once" requirement is worded accordingly. Rejected option (b), a root `components.responses` block: it deviates from `openapi.md` §1 (the root holds only info/servers/security/tags/paths) and would publish unused components.

Contract (authored split; the bundle resolves the `$ref`s):

```yaml
# openapi.yaml (root: info, servers, tags, paths only)
openapi: 3.1.0
info: { title: Movie Catalog API, version: 1.0.0, description: "... X-Correlation-Id request header (optional UUID) ..." }
servers: [ { url: /api/v1 } ]
tags: [ { name: Health, description: Availability check } ]
paths:
  /ping: { $ref: './paths/health.yaml#/ping' }

# paths/health.yaml
ping:
  get:
    operationId: ping
    summary: Check that the catalog service is available
    tags: [Health]
    security: []            # public (domain divergence from JWT default)
    responses:
      '200':
        description: The service is available.
        headers: { X-Correlation-Id: { $ref: '../components/headers/common.yaml#/X-Correlation-Id' } }
        content: { application/json: { schema: { $ref: '../components/schemas/health.yaml#/PingEnvelope' } } }
      '406': { $ref: '../components/responses/common.yaml#/NotAcceptable' }
      '500': { $ref: '../components/responses/common.yaml#/InternalError' }

# components/schemas/health.yaml
PingEnvelope: { type: object, required: [data, meta], additionalProperties: false,
  properties: { data: { $ref: '#/PingData' }, meta: { $ref: './common.yaml#/Meta' } } }
PingData: { type: object, required: [status, _links], additionalProperties: false,
  properties: { status: { type: string, enum: [UP] }, _links: { $ref: '#/PingLinks' } } }
PingLinks: { type: object, required: [self], additionalProperties: false,
  properties: { self: { $ref: './common.yaml#/Link' } } }
```

`components/schemas/common.yaml` holds `Meta` {timestamp, correlationId}, `Link` {href, templated?, title?} and `Problem` (as in `standards/openapi.md` §3). `components/responses/common.yaml` holds `NotAcceptable` and `InternalError`, each `application/problem+json` → `Problem` plus the correlation header. `components/headers/common.yaml` holds `X-Correlation-Id`.

**Inbound correlation header: a conscious deviation (finding 9).** `openapi.md` §1 places a correlation-id parameter in `components/parameters/common.yaml`. Here it is documented in `info.description` instead, because as an operation parameter it would add an unused argument to every generated method: the filter owns the header, so the controller never reads it. Follow-up: revisit this if a tool consumer needs the header machine-readable. This is recorded as a deviation, not an oversight.

**Operations added:** `GET /ping`. Nothing is changed or removed.

### D2. Package layout and dependency direction (inward only)
```
com.acme.platform
├── availability/
│   ├── domain/model/Availability.java           record(AvailabilityStatus status); AvailabilityStatus enum {UP}
│   ├── application/port/in/CheckAvailabilityUseCase.java
│   ├── application/service/AvailabilityService.java   @Service; returns Availability(UP): liveness, no outbound port
│   └── adapters/in/web/PingController.java       implements generated HealthApi; builds PingEnvelope + self link
├── web/  (shared inbound-web infrastructure reused by every catalog controller)
│   ├── CorrelationIdFilter.java                  OncePerRequestFilter, highest precedence, also on ERROR dispatch
│   ├── CorrelationId.java                         accessor for the current request's id
│   ├── ResponseMetaFactory.java                   builds generated Meta from Clock + CorrelationId
│   ├── GlobalExceptionHandler.java                extends ResponseEntityExceptionHandler; the ONLY place mapping to HTTP status
│   ├── ProblemFactory.java                        status → kind → ProblemDetail (type/code/title/safe detail/correlationId)
│   ├── ProblemInstanceStrippingAdvice.java        ResponseBodyAdvice<ProblemDetail>: nulls instance in beforeBodyWrite
│   └── ProblemErrorController.java                implements ErrorController; /error → problem+json for failures raised outside MVC
└── config/  SecurityConfig.java, ClockConfig.java
```
- **Domain** imports only the JDK.
- **Application** imports domain only. Ports carry no annotations. `@Service` on `AvailabilityService` is explicitly tolerated by `clean-architecture.md` §3 ("`@Service`/`@Transactional` are tolerated on use-case implementations"), so it is kept rather than wired in `config/` (finding 12).
- **`PingController`** depends on the inbound port plus the generated `HealthApi`/DTOs, and uses `WebMvcLinkBuilder` for `self` (HATEOAS stays in the adapter, per `openapi.md` §2a).
- **The shared `web` package** sits at the adapter level. `catalog` adapters may import it, and `catalog` depends on `platform`, never the reverse.
- **No outbound adapters.** The domain layer is thin but kept so that later slices copy a uniform shape. Alternative considered: controller-only with no use case. Rejected because it breaks the layering template.

### D3. Failure mapping, in one place, classified by status (finding 3)
`GlobalExceptionHandler extends ResponseEntityExceptionHandler`:
- **Framework exceptions.** Spring's base class already maps every standard MVC exception to its correct status. This includes `NoResourceFoundException` and `NoHandlerFoundException`, 405, 406, 415, missing or ill-typed parameters, unreadable bodies, `MissingPathVariableException` (500) and `AsyncRequestTimeoutException` (503).
- **Overrides.** It overrides `handleExceptionInternal` (plus `createResponseEntity`) so every response body is rebuilt by `ProblemFactory` from the resolved status.
- **Catch-all.** One `@ExceptionHandler(Exception.class)` handles anything else. It logs at ERROR with the trace and returns 500.
- **Domain exceptions.** None exist yet. They get explicit handlers when `catalog` introduces them.

`ProblemFactory` classifies by status into exactly one kind:

| Resolved status | Emitted status | code | type |
|---|---|---|---|
| 404 | 404 | `NOT_FOUND` | `urn:problem-type:not-found` |
| 405 (+ `Allow` preserved) | 405 | `METHOD_NOT_ALLOWED` | `urn:problem-type:method-not-allowed` |
| 406 | 406 | `NOT_ACCEPTABLE` | `urn:problem-type:not-acceptable` |
| 415 | 415 | `UNSUPPORTED_MEDIA_TYPE` | `urn:problem-type:unsupported-media-type` |
| any other 4xx | 400 | `BAD_REQUEST` | `urn:problem-type:bad-request` |
| any 5xx, and the catch-all | 500 | `INTERNAL_ERROR` | `urn:problem-type:internal-error` |

- **Collapsing other statuses.** Other 4xx statuses collapse to 400, and 5xx statuses (for example 503 on async timeout) collapse to 500. This keeps the kind set closed and matches the four kinds UC-000 names, plus the 406/415 kinds it forces. Adding a new kind is a deliberate, additive change.
- **Detail text.** `detail` is a fixed, safe sentence per kind; the framework or exception message is never echoed. 5xx is logged at ERROR with the trace, and 4xx at DEBUG.
- **Standards gap.** `standards/error-handling.md` §3 has no 405, 406 or 415 rows. Task 8.1 adds them (Q1, approved at Gate 1).
- **406 on an XML `Accept`.** When a client sends `Accept: application/xml`, Spring 6 writes `ProblemDetail` as `application/problem+json` even though it does not match the `Accept` header. The 406 test pins this.
- **Problem `instance` (finding 4; mechanism corrected at Gate 1).** Spring fills `instance` with the relative request URI. It does this after the handler returns, while writing the response (`HttpEntityMethodProcessor` / `RequestResponseBodyMethodProcessor`), so nulling it in `ProblemFactory` would not stick. The relative value would also violate `Problem.instance`'s `format: uri`. Instead, `ProblemInstanceStrippingAdvice` (a `@ControllerAdvice` implementing `ResponseBodyAdvice<ProblemDetail>`) sets `instance` to `null` in `beforeBodyWrite`, the last step before serialization, and `non_null` Jackson inclusion then omits the member. The advice applies to every `ProblemDetail` written by an MVC handler, including the exception handler and `ProblemErrorController`. The tests assert the serialized body has no `instance`. Alternative considered: an absolute URI built from the forwarded-aware request URL. Rejected because the value adds nothing beyond `self` and the correlation id, and it echoes client-controlled host headers into error bodies.

**Write methods: pinned to the real Spring 6.2 behaviour (corrected at Gate 1; supersedes round-1 finding 8).** The static-resource handler is mapped at `/**`, and in Spring 6.2 `ResourceHttpRequestHandler` resolves the resource before it checks the method. That produces three deterministic outcomes:
- **A path that isn't offered, with any method** (GET, POST, PUT, PATCH, DELETE): the lookup fails first, so `NoResourceFoundException` → `404 NOT_FOUND`. "Nothing exists here" is the truthful answer whatever the method.
- **An existing static asset** (`/openapi/openapi.bundled.yaml`, `/swagger-ui/index.html` and the webjar files): the resource resolves, then the handler rejects a non-GET/HEAD method with `HttpRequestMethodNotSupportedException` → `405 METHOD_NOT_ALLOWED`, `Allow: GET, HEAD`.
- **`/ping`:** the generated `@RequestMapping` matches the path but not the method → `405 METHOD_NOT_ALLOWED` with `Allow` including `GET` (Spring may also list `HEAD` and `OPTIONS`; the spec requires only that `GET` is present).

The read-only guarantee is therefore "never 2xx, 401, 403 or 5xx"; 404 and 405 are both correct refusals. No handler mapping is changed to force a single status. Tests pin all three outcomes (tasks 6.3 and 7.1), so a Spring upgrade that changes them fails the build instead of drifting silently.

**Errors outside MVC (finding 5).** `ProblemErrorController implements ErrorController` and is mapped to `/error`. Because an `ErrorController` bean now exists, Boot's `BasicErrorController` (`@ConditionalOnMissingBean(ErrorController.class)`) backs off, and filter-chain or container errors are rendered as problem+json from the stored status and exception, through the same `ProblemFactory`, with `server.error.whitelabel.enabled: false`. `CorrelationIdFilter` overrides `shouldNotFilterErrorDispatch()` to return `false`, so it also runs on the ERROR dispatch. On that dispatch it reuses the id already stored in the request attribute (never generating a new one) and re-populates the MDC. The header and log lines therefore keep the original id. MockMvc performs no ERROR dispatch, so this path is tested with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and a real HTTP client (task 7.4).

**Success Content-Type is always `application/json` (Gate 1 note).** The generated `HealthApi` mapping `produces` both `application/json` and `application/problem+json`, because `/ping` documents problem responses. Left to content negotiation, `Accept: application/problem+json` would label the success envelope as a problem. `PingController` therefore returns `ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)`. A preset Content-Type bypasses body-level negotiation, while the mapping-level `produces` check still yields `406` for `Accept: application/xml`. The resulting behaviour: `Accept: application/problem+json` gets `200 application/json`. That is lenient but never mislabelled, and it is pinned by a test. Every later catalog controller follows the same rule.

### D4. Security config: public, stateless, read-only (finding 10)
One `SecurityFilterChain`:
- `authorizeHttpRequests(anyRequest().permitAll())`.
- `csrf.disable()`. This is safe because there are no cookies or sessions and no state-changing endpoints, and CSRF enforcement would otherwise turn a write attempt into 403 instead of 405.
- `sessionManagement(STATELESS)`.
- `httpBasic`, `formLogin` and `logout` disabled.

Security headers, against `security.md` §3:
- **Baseline headers:** `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and CSP `default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'`. Swagger UI injects inline styles, so `style-src` needs `'unsafe-inline'`; scripts stay strict.
- **HSTS:** Spring Security's default, which is emitted only on secure requests. TLS is expected to terminate at the gateway or CDN (`actors-and-personas.md`). `server.forward-headers-strategy: framework` makes `X-Forwarded-Proto: https` mark the request as secure, so HSTS is sent to real clients. The same setting makes `self` hrefs use the external scheme and host. HTTPS-only enforcement (redirect or rejection of plain HTTP) is the gateway's job and is not added in-app.
- **CORS:** no CORS configuration, so the service is same-origin only. `security.md` requires an explicit allow-list, and the empty allow-list is the smallest correct one. Browser-based third-party consumers calling the API directly are unsupported for now (Q4). Server-side consumers and same-origin Swagger UI are unaffected.

No `JwtDecoder` bean is defined. The custom `security.jwt.*` keys already keep resource-server auto-config off. Rejected alternative: excluding `SecurityAutoConfiguration`. That would lose the security headers.

### D5. Interface description in every mode; no management HTTP surface (finding 2)
Swagger UI is always on (A2, confirmed at Gate 1). The inline initializer script moves to `static/swagger-ui/swagger-initializer.js` so that the CSP stays strict.

Actuator's HTTP surface is disabled entirely with `management.server.port: -1`. This removes the `/actuator` discovery root as well as every endpoint, in one setting, and cannot drift if an endpoint is later exposed by default. Alternative considered: `management.endpoints.web.exposure.exclude: "*"` plus `management.endpoints.web.discovery.enabled: false`. Rejected because it takes two settings to express one intent. The dependency stays for future non-HTTP use (Q2). Springdoc is not added.

### D6. Timestamp and correlation id
- A `Clock` bean (UTC) is injected into `ResponseMetaFactory` so tests can fix time.
- `CorrelationIdFilter` works as follows:
  - It honours `X-Correlation-Id` when it parses as a UUID; otherwise it generates `UUID.randomUUID()`. It never rejects.
  - It stores the id in the MDC key `correlationId` and in a request attribute.
  - It sets the response header before the chain proceeds.
  - It clears the MDC in `finally`.
  - On the ERROR dispatch it reuses the attribute (D3).
- The log pattern includes `%X{correlationId}` through `logging.pattern.level`.

### D7. Contract-conformance testing (BR-4, flow 6a)
Conformance is enforced at three levels:
- **Compile time:** the controller implements the generated `HealthApi`.
- **Codegen:** `GeneratedApiCodegenTest` asserts that `HealthApi.ping()` returns `PingEnvelope` and that no `*200Response*` classes exist.
- **Runtime:** `InterfaceDescriptionContractTest` loads the served bundled YAML and validates real 200, 404 (GET and PUT on an unknown path), 405 and 406 bodies against the declared schemas with `com.networknt:json-schema-validator` (test scope; it supports JSON Schema 2020-12, which OAS 3.1 uses). It also asserts that every documented path+method is routed, and that shared components are single and referenced.

The success schemas are closed (`additionalProperties: false`), so any undeclared key in a real body fails validation (finding 11). Alternative considered: `swagger-request-validator`. Rejected because its OAS 3.1 support is incomplete.

### D8. Runtime modes and DB
No schema and no Flyway migration. `db/migration` stays empty; Flyway starts cleanly with no migrations. **Rollback:** there is nothing to roll back in the DB, and reverting the commit is sufficient.

Both modes are covered by one shared assertion set (`Uc000Assertions`), used by two tests:
- `H2DefaultRuntimeSmokeTest`, the single H2 test that `standards/testing.md` permits.
- `PersistentModeIntegrationTest`, which extends `PostgresIntegrationTest` (Testcontainers `postgres:16-alpine`) with the `postgres` profile.

"Not retained across restarts" is guaranteed by the in-memory H2 URL (`jdbc:h2:mem:`). It becomes behaviourally testable only when the first catalog slice stores data (finding 11).

## Risks / Trade-offs

- [openapi-generator 7.14 has partial OAS 3.1 support] → Keep schemas to constructs it handles (no `type: [x, null]`, no `$defs`). The codegen test catches regressions.
- [`redocly lint` recommended rules may flag the absence of global `security` and `securitySchemes`] → Each operation has `security: []`. If lint still objects, add a `redocly.yaml` that disables only `security-defined`, with a comment citing the domain divergence.
- [`forward-headers-strategy: framework` trusts `X-Forwarded-*` from any caller, so a caller can spoof the host in its own `self` links and trigger HSTS] → The impact is limited to that caller's own response. Production is expected to sit behind a gateway that overwrites these headers. Note this in deployment docs when a gateway is chosen.
- [Spring may change the static-resource lookup order, the preset Content-Type handling or the 406 negotiation behaviour pinned in D3] → Both are pinned by tests, so an upgrade that changes them fails the build instead of silently drifting.
- [Collapsing other 4xx statuses to 400 and 5xx statuses to 500 loses nuance (for example 503 on async timeout)] → No UC-000 path produces them. A future capability that needs a distinct kind (such as readiness's 503, see A1) adds it explicitly.
- [Web slice tests using `@WebMvcTest` need `SecurityConfig`, the filter and the handler imported explicitly] → The `@PlatformWebTest` meta-annotation is created first (task 6.0).

## Migration Plan

This is the first deploy. There is no data and no schema. Rollback is to revert the commit.

## Resolved at Gate 1 (human decisions)

- **A1, confirmed:** "available" means liveness only. The availability check answers `UP` whenever the process serves HTTP. It does not probe the database, and there is no 503 kind. Readiness may come with a later catalog capability.
- **A2, confirmed:** the browsable description (Swagger UI) is offered in every mode, including persistent operation.
- **Q1, yes:** task 8.1 adds the 405 `METHOD_NOT_ALLOWED`, 406 `NOT_ACCEPTABLE` and 415 `UNSUPPORTED_MEDIA_TYPE` rows, plus the "other 4xx → 400 `BAD_REQUEST`" rule, to `standards/error-handling.md` §3.
- **Q2, out of scope:** the unused `spring-boot-starter-oauth2-resource-server` dependency and the `security.jwt.*` keys stay untouched.
- **Q3, keep URNs:** problem `type` values stay `urn:problem-type:<kind>`.
- **Q4, same-origin only:** no CORS configuration (D4).

## Open Questions

None.
