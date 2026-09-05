## Why

The product is a public, read-only demo movie API whose value is "clone → run →
demonstrate". PLAT-004 made the default runtime zero-dependency (H2 in-memory), so the app
boots with no Docker/Postgres. The remaining friction is discovery: to explore the operations
and schemas (`GET /movies`, `GET /people`, etc.) a consumer needs an external tool. An
interactive Swagger UI served by the app lets anyone browse the operations and schemas from a
browser with zero setup — provided it renders the *authored* contract and does not erode
contract-first.

## What Changes

- Serve an interactive **Swagger UI** page from the app at a stable URL under the `/api/v1`
  context-path (`/api/v1/swagger-ui/index.html`).
- Serve the **authored redocly-bundled spec** (`build/openapi/openapi.bundled.yaml`, the same
  bundle that drives codegen) as a **static resource** at `/api/v1/openapi/openapi.bundled.yaml`,
  and point Swagger UI at that URL. Wire the build so the bundle lands on the runtime-served
  resources reproducibly from a clean build (`processResources` depends on `bundleOpenApiSpec`);
  the bundle is never committed.
- Serve Swagger UI from the **`org.webjars:swagger-ui` webjar with NO springdoc dependency at
  all**, via a small app-owned static entry page that loads the webjar assets and points
  `SwaggerUIBundle` at the authored bundled-spec URL. With no springdoc, an annotation-generated
  spec is impossible — the only spec that exists is the authored one (the strongest possible
  contract-first / single-source-of-truth guarantee). All UI assets are served locally from the
  webjar (no external CDN — works offline).
- **Permit** the Swagger UI + spec + asset paths through Spring Security as public via a
  mechanism **separate** from the contract `PublicEndpoints.PATTERNS`, so the
  `PublicEndpoints` ↔ contract consistency guarantee is untouched.
- **Relax the Content-Security-Policy for the docs paths only** (Swagger UI needs its own
  scripts/styles/images/fonts); the strict global CSP on the API stays intact.
- Add **browserless verification** and update docs (README, CLAUDE.md/standards/openapi.md).

Non-breaking. No change to the authored OpenAPI operations/schemas or REST behaviour.

## Capabilities

### New Capabilities
- `platform/api-docs`: Serving an interactive API documentation UI (Swagger UI) that renders
  the authored bundled OpenAPI spec, exposes no second (annotation-generated) spec, makes the
  docs surface public without widening the contract's public surface, and scopes any CSP
  relaxation to the docs paths only.

### Modified Capabilities
<!-- None. This is additive tooling; no existing promoted requirement changes. In particular
     the authored contract, the api-codegen pipeline, and runtime-datasource behaviour are all
     unchanged. -->

## Impact

- **Non-goals:** No change to authored OpenAPI operations/schemas or REST behaviour (D4). No
  auth on the docs (the API is public). No springdoc dependency and therefore no
  annotation-generated spec at all (D1 honored by construction). No ReDoc or other renderer. No
  new "try it out" write affordances (the API is read-only).
- **Breaking changes:** None (no API contract or DB schema change).
- **Affected code/build:** `build.gradle.kts` (swap dependency to the swagger-ui webjar; wire
  bundle → served static resources), a small app-owned Swagger UI entry page under
  `src/main/resources/static/swagger-ui/`, `SecurityConfig` + a small tooling permit-list
  constant, `application.yml` (remove the now-obsolete `springdoc.*` config), tests (extend
  `H2DefaultRuntimeSmokeTest`), and docs. Domain and application layers are untouched
  (platform/representation-tooling concern only).
- **Guardrails to keep green:** `GeneratedApiCodegenTest` and `PublicEndpointsConsistencyTest`
  must stay passing; the authored split spec and the redocly-bundle → openapi-generator
  pipeline are unchanged.
