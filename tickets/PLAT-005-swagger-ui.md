# PLAT-005: Serve interactive Swagger UI for the public read API (rendering the authored spec)

**Type:** Technical
**Area:** platform (API documentation / developer experience) — supports the `catalog` bounded context (see domain/bounded-contexts.md)
**Status:** Ready (author decisions D1–D4 settled; the Open questions are architect "how" calls for Gate 1)

## Problem / rationale
The product is a public, read-only demo movie API (domain/overview.md, domain/bounded-contexts.md
`catalog` — all capabilities are read-only and public, no auth). PLAT-004 just made the default
runtime zero-dependency (H2 in-memory, `./gradlew bootRun` with no Docker/Postgres), sharpening the
"clone → run → demonstrate" journey we want for a demo API.

The remaining friction is discovery: to explore `GET /movies`, `GET /movies/{id}`, `GET /people`,
etc. and their schemas, a consumer/demoer today needs an external tool (curl, Postman, a saved spec).
An **interactive Swagger UI** served by the app lets anyone browse the operations and their schemas —
and "try it out" against the running instance — straight from a browser, with zero setup.

The crux is doing this **without eroding contract-first** (standards/openapi.md — "the OpenAPI file
is the single source of truth"). Swagger UI must render the *authored* multi-file spec (the redocly
bundle at `build/openapi/openapi.bundled.yaml` that also drives codegen), never a second,
annotation-generated document that could drift from the contract. This is documentation tooling only:
no change to the HTTP contract, operations, schemas, or behaviour.

## Decisions (settled by the author — do NOT re-open)
- **D1 — Contract-first is preserved: Swagger UI renders the AUTHORED spec, never a competing
  generated one.** The single source of truth stays the authored multi-file OpenAPI bundled by
  redocly into `build/openapi/openapi.bundled.yaml` (the same bundle that drives codegen). Swagger UI
  MUST load THAT spec. If springdoc-openapi is used, its own annotation-generated `/v3/api-docs`
  endpoint MUST be disabled (`springdoc.api-docs.enabled=false` or equivalent) so no second,
  drift-prone spec is exposed. Upholds standards/openapi.md (single source of truth).
- **D2 — The bundled authored spec is served as a static resource** at runtime (copied into the app's
  served resources during the build so it is available at a stable URL), and Swagger UI is pointed at
  that URL.
- **D3 — Swagger UI and the served spec are PUBLIC** (the API is `security: []`), so their paths must
  be permitted through Spring Security — honouring `server.servlet.context-path: /api/v1` (so the UI
  is reachable at e.g. `/api/v1/swagger-ui.html`). Registering in the single `PublicEndpoints` source
  of truth is preferred if applicable (but see Risks — the existing consistency test).
- **D4 — No REST API/contract change.** Swagger UI is documentation tooling, not part of the HTTP
  contract; `GeneratedApiCodegenTest` and all existing tests stay green. No change to the authored
  spec's operations/schemas.

## Scope
- Add an interactive **Swagger UI** page, served by the application, reachable in a browser at a
  stable URL under the `/api/v1` context-path.
- Serve the **authored redocly-bundled spec** (`build/openapi/openapi.bundled.yaml`) as a **static
  resource** at a stable URL, and point Swagger UI at that URL (D1, D2). Wire the build so the bundled
  spec is on the runtime-served resources (reproducible from a clean build; works from `bootRun` and
  the boot jar).
- If springdoc-openapi is adopted, **disable its annotation-generated `/v3/api-docs`** (or equivalent)
  so the only spec exposed is the authored one (D1).
- **Permit the Swagger UI and spec paths through Spring Security** as public, honouring the
  context-path, in a way that does not break the existing `PublicEndpoints` ↔ contract consistency
  guarantee (see Risks / Open questions).
- Add a **browserless verification** (integration/smoke assertion) that the Swagger UI resource and
  the spec URL each return `200` publicly (no auth), and that the served spec body is the authored
  OpenAPI (e.g. contains a known path/operationId such as `GET /movies`).
- **Update docs** — README (the Swagger UI URL and that it renders the authored spec) and, if
  relevant, a note in CLAUDE.md/standards/openapi.md that Swagger UI serves the authored bundled spec
  with springdoc generation disabled.

## Out of scope
- Any change to the authored OpenAPI operations/schemas or REST behaviour (D4).
- Making Swagger UI a *write*/"try it out" surface beyond what the read API already allows — the API
  is read-only anyway; no write affordances are introduced.
- Auth for the docs — the API is public (domain/business-rules.md); no authentication/authorization
  work.
- Adopting annotation-driven/springdoc-**generated** specs as a source of truth (explicitly rejected —
  D1).
- ReDoc or any other doc renderer — Swagger UI only for this slice.

## Constraints (standards that apply)
- **standards/openapi.md** — contract-first, single source of truth: Swagger UI MUST render the
  authored bundled spec; no second generated spec may be exposed. The authored split spec and the
  redocly-bundle → openapi-generator pipeline are untouched; `GeneratedApiCodegenTest` stays green.
- **standards/security.md** — default-deny stateless resource server; the docs surface is an
  intentional **public divergence** and must be explicitly permitted. Public paths remain a controlled
  source of truth (`PublicEndpoints`/`SecurityConfig`); do not weaken auth on any API operation. The
  existing CSP header (`default-src 'none'`) is set in `SecurityConfig` — verify Swagger UI's own
  assets/inline scripts still load, or that any CSP relaxation is scoped only to the docs paths (see
  Risks).
- **standards/testing.md** — new behaviour ships with tests (happy/edge/failure). DB tests stay on
  Testcontainers-Postgres (no H2); the single H2 runtime smoke test from PLAT-004
  (`H2DefaultRuntimeSmokeTest`) is the only H2 test. Decide whether the docs assertions belong there
  (real HTTP client honouring the context-path) or in a web-layer/security test.
- **standards/clean-architecture.md** — this is a platform/representation-tooling concern; the domain
  and application layers gain no knowledge of Swagger/springdoc. Any code stays in the platform/common
  or web-adapter/config layer.
- **standards/formatting.md** — google-java-format via Spotless applies to any touched Java.

## Acceptance / done criteria
- [ ] **Swagger UI loads and renders the authored spec.** With the app running on the default H2
      profile (zero deps), navigating to the Swagger UI URL — `/api/v1/swagger-ui.html` (or the exact
      URL the architect selects, incl. the `/api/v1` context-path) — returns the Swagger UI page,
      which loads the **authored bundled spec** and lists the real operations (`GET /movies`,
      `GET /movies/{id}`, `GET /people`, etc.), proving it is the authored contract, not an
      annotation-generated one.
- [ ] **The served spec is the authored bundled spec.** The spec URL Swagger UI consumes returns the
      redocly-bundled authored OpenAPI (the same content used for codegen), served as a static
      resource — NOT a springdoc-generated document. If springdoc is present, its `/v3/api-docs` is
      disabled/not exposed (a request to it does not return a generated spec).
- [ ] **Public access.** The Swagger UI URL and the spec URL are reachable with no auth (no `401`/
      `403`), consistent with the public read API; permitted in the security config honouring the
      `/api/v1` context-path, and the existing `PublicEndpoints` ↔ contract consistency test still
      passes.
- [ ] **No contract/behaviour change.** All existing endpoints behave unchanged; `GeneratedApiCodegenTest`
      and the full test suite stay green; the authored OpenAPI files are unchanged.
- [ ] **Build wiring is reproducible.** From a clean build, the bundled spec is produced and placed so
      it is served at runtime; the Swagger UI → spec load works from a normal `bootRun` and from the
      boot jar.
- [ ] **Browserless verification exists.** An integration/smoke test asserts the Swagger UI resource
      and the spec URL each return `200` publicly, and that the spec body is the authored OpenAPI
      (e.g. it contains a known path/operationId like the `/movies` GET). (Failure path: the spec URL
      is not a springdoc `/v3/api-docs` document; and a request to any authenticated endpoint still
      returns Problem+json `401` — the docs carve-out did not widen the public surface.)

## Standards / docs to update (part of this work)
- **README.md** — document the Swagger UI URL and that it renders the authored spec (contract-first),
  as part of the zero-setup demo run.
- **CLAUDE.md / standards/openapi.md** (if relevant) — note that Swagger UI serves the **authored
  bundled spec** with springdoc generation disabled, so contract-first (single source of truth) is
  preserved.

## Risks
- **`PublicEndpoints` ↔ contract consistency test (primary).** `PublicEndpointsConsistencyTest`
  asserts `PublicEndpoints.PATTERNS` matches *exactly* the OpenAPI operations marked `security: []`.
  Because D4 forbids adding the docs paths to the authored spec, simply appending swagger-ui/spec
  patterns to `PublicEndpoints.PATTERNS` would **break that test**. The docs paths are non-contract
  tooling, so they need a permit mechanism that keeps `PATTERNS` in exact agreement with the contract
  — e.g. a separate "tooling/static" permit list in `SecurityConfig` (outside `PATTERNS`), or a second
  `PublicEndpoints` list the consistency test deliberately excludes. Resolve before design (Open
  questions). This is the tension inside D3's "prefer PublicEndpoints if applicable".
- **Content Security Policy.** `SecurityConfig` sets a strict global CSP (`default-src 'none';
  frame-ancestors 'none'`). Swagger UI needs to load its own JS/CSS (and often inline styles/scripts);
  under this CSP the page may render blank. Any relaxation must be scoped to the docs paths only and
  must not loosen CSP on API responses.
- **Spec on the runtime classpath / build ordering.** The bundle is a `build/`-only artifact
  (git-ignored, produced by `bundleOpenApiSpec` before `openApiGenerate`). It must be copied onto the
  served resources such that `processResources`/`bootJar` include it and the copy runs after bundling.
  If wired wrongly, the spec URL 404s at runtime, or a stale bundle is served.
- **Second-spec drift.** If springdoc is added but its `/v3/api-docs` is not disabled, two specs get
  exposed and can silently diverge — the exact failure D1 forbids. Must be verifiably off.
- **Try-it-out CORS/base URL.** Swagger UI "try it out" issues requests to the server's base URL under
  the context-path; if the spec's `servers` or the UI's base URL is wrong, calls 404. Read-only so
  low blast radius, but worth confirming the base URL honours `/api/v1`.

## Open questions (need a human/architect decision at Gate 1)
- **Library choice & wiring.** `springdoc-openapi-starter-webmvc-ui` (with `springdoc.api-docs.enabled=false`
  and `springdoc.swagger-ui.url` pointed at the static bundled spec) vs the raw
  `org.webjars:swagger-ui` webjar pointed at the static spec. **Recommendation:** the option with the
  least drift risk and least new surface — leaning to the webjar (or springdoc with generation firmly
  disabled) since we deliberately do NOT want annotation-driven generation; architect decides.
- **How/where the bundled spec is served.** Static-resource copy during the build into
  `src/main/resources/static/` vs a build-time copy into the jar's static resources (`build/resources`)
  vs a tiny controller streaming the bundled file — and the exact stable spec URL, accounting for the
  `/api/v1` context-path. **Recommendation:** a build-time copy of `build/openapi/openapi.bundled.yaml`
  into the served static resources (keeping the authored split spec the source of truth, never
  committing the bundle); architect confirms.
- **Security wiring (the crux).** Exactly which path patterns to permit (swagger-ui HTML + assets, the
  spec URL, any springdoc/webjar resource paths), and — given `PublicEndpointsConsistencyTest` — where
  they live so `PublicEndpoints.PATTERNS` stays in exact agreement with the contract (separate tooling
  permit list vs excluding docs paths from the consistency check). Also whether/how to scope the CSP
  relaxation to only the docs paths.
- **Environment scoping.** Enabled in all profiles (default H2 + `postgres`) or only the demo/default
  runtime? **Recommendation:** enabled by default for this public demo API (its whole purpose is
  discoverability); note any reason to gate it (e.g. if a future real production deployment wants docs
  off).

## Domain gaps
- None. This is a platform/documentation-tooling enabler; it introduces no new business language,
  persona, or durable domain rule. "Swagger UI renders the authored bundled spec (springdoc generation
  disabled) to preserve contract-first" is a technical convention that belongs in CLAUDE.md /
  standards/openapi.md, not `domain/`.
