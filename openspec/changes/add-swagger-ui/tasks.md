<!-- No OpenAPI/domain/application/migration work: D4 forbids any contract change and this is
platform tooling only. Tasks are ordered by dependency for this change's actual surface:
dependency + build wiring → runtime config → security wiring → tests → docs → verify. -->

## 1. Dependency and build wiring

- [x] 1.1 In `build.gradle.kts`, REMOVE `org.springdoc:springdoc-openapi-starter-webmvc-ui` and ADD `org.webjars:swagger-ui:5.18.2` (pin exactly; no `webjars-locator-core` — the entry page uses pinned exact-version asset paths). Confirm the version pin matches the entry page's asset paths.
- [x] 1.2 Wire `processResources` to `dependsOn(bundleOpenApiSpec)` and copy `build/openapi/openapi.bundled.yaml` into `static/openapi/` (served at `/api/v1/openapi/openapi.bundled.yaml`); confirm the bundle stays git-ignored (not committed). (Unchanged by the fix — keep as-is.)

## 2. Swagger UI entry page and runtime configuration

- [x] 2.1 Add an app-owned static entry page at `src/main/resources/static/swagger-ui/index.html` (served at `/api/v1/swagger-ui/index.html`) that loads the webjar assets by pinned exact-version, origin-absolute paths (`/api/v1/webjars/swagger-ui/5.18.2/swagger-ui.css`, `.../swagger-ui-bundle.js`, `.../swagger-ui-standalone-preset.js`) and calls `SwaggerUIBundle({ url: '/api/v1/openapi/openapi.bundled.yaml', dom_id: '#swagger-ui', presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset], layout: 'StandaloneLayout' })`. No CDN; all assets local.
- [x] 2.2 In `application.yml`, REMOVE the entire `springdoc.*` block (and its comment) — no springdoc remains.

## 3. Security wiring

- [x] 3.1 Update `com.acme.common.security.DocsUiEndpoints` patterns to `/swagger-ui/**`, `/webjars/**`, `/openapi/**` only (drop `/swagger-ui.html` and `/v3/api-docs/**` — no springdoc). Still separate from `PublicEndpoints.PATTERNS`.
- [x] 3.2 In `SecurityConfig`, higher-precedence (`@Order(1)`) `SecurityFilterChain` scoped via `securityMatcher` to `DocsUiEndpoints.PATTERNS`: `permitAll()`, no oauth2 filter, relaxed docs-scoped CSP (`default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'`). (Chain structure unchanged by the fix — keep as-is.)
- [x] 3.3 Existing chain stays `@Order(2)`, otherwise unchanged (strict global CSP, `PublicEndpoints.PATTERNS`, `anyRequest().authenticated()`, OAuth2 resource server). (Unchanged by the fix — keep as-is.)

## 4. Verification tests (browserless)

- [x] 4.1 In `H2DefaultRuntimeSmokeTest`: `GET /swagger-ui/index.html` returns `200` with no auth (app-owned Swagger UI page loads publicly).
- [x] 4.2 In `H2DefaultRuntimeSmokeTest`: `GET /webjars/swagger-ui/5.18.2/swagger-ui-bundle.js` returns `200` with no auth (the UI's local webjar assets load without auth).
- [x] 4.3 In `H2DefaultRuntimeSmokeTest`: `GET /openapi/openapi.bundled.yaml` returns `200` with no auth and the body contains a known authored token (e.g. `listPeople` and/or the `/movies` path).
- [x] 4.4 In `H2DefaultRuntimeSmokeTest`: `GET /v3/api-docs` does NOT return a `200` (no springdoc, so no annotation-generated spec endpoint exists — non-200 via default-deny).
- [x] 4.5 In `H2DefaultRuntimeSmokeTest`: an unauthenticated request to a synthetic endpoint (`/__not-an-endpoint`) still returns Problem+json `401` (docs carve-out did not widen the public surface).
- [x] 4.6 Confirm `PublicEndpointsConsistencyTest` (unchanged) and `GeneratedApiCodegenTest` (unchanged) both stay green (authored spec untouched, docs paths not in `PATTERNS`).

## 5. Documentation

- [x] 5.1 Update `README.md` with the Swagger UI URL (`/api/v1/swagger-ui/index.html`) and that it renders the authored spec, as part of the zero-setup demo run.
- [x] 5.2 Add a note to `CLAUDE.md`/`standards/openapi.md` that Swagger UI is served from a local webjar (no springdoc) and renders ONLY the authored bundled spec, preserving contract-first (single source of truth).

## 6. Full verification

- [x] 6.1 Run `./gradlew build -x spotlessCheck` and confirm the full suite (including the new assertions, `PublicEndpointsConsistencyTest`, and `GeneratedApiCodegenTest`) is green.
