## Context

See proposal.md — Why. Relevant current state:

- The authored OpenAPI is a split spec under `src/main/resources/openapi/`. `build.gradle.kts`
  bundles it via redocly (`bundleOpenApiSpec` → `build/openapi/openapi.bundled.yaml`, git-ignored)
  and generates server interfaces from that bundle (`openApiGenerate`). `GeneratedApiCodegenTest`
  asserts the reuse guarantees.
- `server.servlet.context-path: /api/v1` (application.yml). Spring Security matches request
  matchers against the path WITHIN the application (excluding the context path).
- `SecurityConfig` has a single `SecurityFilterChain`: strict global CSP
  (`default-src 'none'; frame-ancestors 'none'`), permits exactly `PublicEndpoints.PATTERNS`,
  `anyRequest().authenticated()`, OAuth2 resource server with Problem 401/403.
- `PublicEndpointsConsistencyTest` asserts `PublicEndpoints.PATTERNS`
  `containsExactlyInAnyOrderElementsOf` the OpenAPI operations marked `security: []`. Since D4
  forbids touching the authored spec, docs paths MUST NOT go into `PATTERNS`.
- `H2DefaultRuntimeSmokeTest` (`WebEnvironment.RANDOM_PORT`, `TestRestTemplate`) is the single
  H2 test and the only place that exercises the real filter chain over real HTTP honouring the
  context path — the natural home for the docs assertions.

**OpenAPI operations added/changed:** NONE (D4). Swagger UI is documentation tooling, not part
of the HTTP contract. The authored split spec and the redocly-bundle → openapi-generator
pipeline are untouched; `GeneratedApiCodegenTest` stays green. No contract snippet applies.

**Components touched (all platform/adapter/build; dependency direction inward-only preserved):**
`build.gradle.kts` (dependency swap + resource wiring), a small app-owned Swagger UI entry page
under `src/main/resources/static/swagger-ui/`, `com.acme.common.security.SecurityConfig`
(+ a new `com.acme.common.security` tooling permit-list constant), `application.yml` (removal of
the obsolete `springdoc.*` block), tests, and docs. The `domain` and `application` layers gain NO
knowledge of Swagger — nothing there is touched, so no inward dependency can be violated.

**DB migration plan:** None. This change touches no schema; there is no Flyway migration and no
rollback consideration beyond reverting the (schema-less) config/build changes.

## Goals / Non-Goals

**Goals:**
- Serve Swagger UI rendering the authored bundled spec, all assets local, under `/api/v1`.
- Keep contract-first intact: only the authored spec is exposed; no annotation-generated spec.
- Make the docs surface public and CSP-workable WITHOUT changing the contract's public surface
  or weakening the global CSP.
- Prove it browserlessly.

**Non-Goals (design-level):**
- No custom Swagger UI theming or "try it out" write affordances (the API is read-only anyway).
- No new controller if springdoc + static-resource serving already cover it.
- No environment gating in this change (see Decision 5).

## Decisions

### Decision 1 — Library: raw `org.webjars:swagger-ui` webjar, NO springdoc

Serve Swagger UI from the `org.webjars:swagger-ui` webjar (pinned version) with **no springdoc
dependency at all**. Replace `org.springdoc:springdoc-openapi-starter-webmvc-ui` in
`build.gradle.kts` with `org.webjars:swagger-ui:5.18.2` (pin exactly; the entry page references
this version in its asset paths). No `webjars-locator-core` — the entry page uses the pinned,
exact-version asset paths, which avoids the locator entirely (simplest, fully offline/local). No
`springdoc.*` config remains in `application.yml`.

**Why the previous springdoc plan cannot work (verified defect).** The original design used
`springdoc-openapi-starter-webmvc-ui` with `springdoc.api-docs.enabled=false` to satisfy D1 (no
annotation-generated spec) while keeping Swagger UI. In springdoc 2.8.x this does not separate
"kill generated spec" from "keep UI": `springdoc.api-docs.enabled=false` disables the ENTIRE
springdoc auto-config. `org.springdoc.core.configuration.SpringDocConfiguration` is
`@ConditionalOnProperty("springdoc.api-docs.enabled", matchIfMissing=true)`, and
`org.springdoc.webmvc.ui.SwaggerConfig` (which registers the Swagger UI controllers, incl.
`/swagger-ui.html`) is `@ConditionalOnBean(SpringDocConfiguration.class)`. So disabling api-docs
also removes the Swagger UI controllers → `GET /swagger-ui.html` returns 404. Confirmed by
decompiling the 2.8.6 jars. The property therefore cannot satisfy D1 and keep the UI at once.

**Why the raw webjar (the chosen fix).** With no springdoc on the classpath, an
annotation-generated spec is *impossible* — D1 is honored by construction, not by a config flag
that also has to be proven off. Swagger UI renders ONLY the authored redocly-bundled spec. The
cost is a small app-owned entry page (Decision 1a); there is no annotation scan and no risk of a
second spec drifting from the contract. Every asset is served locally from the webjar, so the
demo works offline (no CDN).

### Decision 1a — Serve Swagger UI via an app-owned static entry page

The swagger-ui webjar ships an `index.html` + `swagger-initializer.js` that default to the
petstore demo spec. Rather than override the webjar's initializer, serve a small **app-owned**
entry page as a static resource at `src/main/resources/static/swagger-ui/index.html`, served
(context path prepended by the servlet) at **`/api/v1/swagger-ui/index.html`** — the final
Swagger UI URL. That page:

- loads the webjar's CSS/JS by pinned exact-version, origin-absolute paths (the page is
  same-origin under the context path, so absolute URLs include `/api/v1`):
  - `/api/v1/webjars/swagger-ui/5.18.2/swagger-ui.css`
  - `/api/v1/webjars/swagger-ui/5.18.2/swagger-ui-bundle.js`
  - `/api/v1/webjars/swagger-ui/5.18.2/swagger-ui-standalone-preset.js`
- calls `SwaggerUIBundle` pointed at the authored bundled spec:

```html
<script>
  window.ui = SwaggerUIBundle({
    url: '/api/v1/openapi/openapi.bundled.yaml',
    dom_id: '#swagger-ui',
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: 'StandaloneLayout'
  });
</script>
```

Spring Boot serves `/webjars/**` from `classpath:/META-INF/resources/webjars/` (the webjar's
resource root) out of the box, so no resource handler is added. The pinned version in the asset
paths MUST match the `org.webjars:swagger-ui` dependency version exactly.

**Why an app-owned `index.html` over overriding the webjar's `swagger-initializer.js`:** a
single self-contained static page is the least moving parts — it hard-codes the authored spec
URL and the exact asset versions in one file, with nothing depending on the webjar's own page
structure (which can change across versions). It reads plainly, is trivially testable
browserlessly (the page 200s and its assets 200 without auth), and keeps all UI wiring in one
app-owned artifact.

### Decision 2 — Serve the bundle via a build-time copy into processResources output

Wire `build.gradle.kts` so `processResources` copies `build/openapi/openapi.bundled.yaml` into
the served static resources at `static/openapi/openapi.bundled.yaml`, and depends on
`bundleOpenApiSpec`:

```kotlin
tasks.named<ProcessResources>("processResources") {
    dependsOn(bundleOpenApiSpec)
    from(openApiBundledSpec) { into("static/openapi") }
}
```

Served URL (context-path prepended by the servlet): `/api/v1/openapi/openapi.bundled.yaml`.

**Why over committing the bundle into `src/main/resources/static/` or a streaming controller:**
committing the bundle would duplicate the source of truth and risk a stale copy (the authored
split spec must remain the only committed source; the bundle stays a build artifact). A
streaming controller is extra Java for what static-resource serving already does. The
`dependsOn(bundleOpenApiSpec)` edge guarantees the served copy is regenerated on every build —
never stale, never missing — and it is included in both `bootRun` (from `build/resources/main`)
and the boot jar (static resources are packaged). This mirrors the existing
`platform/api-codegen` guarantee that the build resolves the bundle before consuming it.

### Decision 3 — Second, path-scoped SecurityFilterChain for the docs (resolves BOTH load-bearing risks)

Add a SECOND `SecurityFilterChain` bean with higher precedence (`@Order(1)`; the existing chain
becomes `@Order(2)` / default) whose `securityMatcher(...)` matches ONLY the docs paths. That
chain: `permitAll()` for its matched paths AND writes the relaxed CSP. The existing chain is
otherwise unchanged (strict CSP, `PublicEndpoints.PATTERNS`, `anyRequest().authenticated()`,
OAuth2 resource server).

This single mechanism resolves both risks at once:

- **Risk 1 (PublicEndpointsConsistencyTest):** docs paths are permitted by the SECOND chain's
  matcher, NOT by adding them to `PublicEndpoints.PATTERNS`. `PATTERNS` therefore stays in exact
  agreement with the contract `security: []` operations and the consistency test is untouched.
  The docs permit patterns live in a NEW dedicated constant
  (`com.acme.common.security.DocsUiEndpoints.PATTERNS`) — a controlled, separate source of truth
  that the consistency test deliberately does not consider.
- **Risk 2 (CSP):** because the relaxed CSP is written by the docs chain, scoped by its
  `securityMatcher`, the strict global CSP on the API chain is literally never touched.

Docs permit/matcher patterns (paths within the application, i.e. after `/api/v1`):
`/swagger-ui/**` (the app-owned entry page at `/swagger-ui/index.html`), `/webjars/**` (the
swagger-ui webjar assets), and `/openapi/**` (the served bundled spec). No springdoc paths —
there is no springdoc, so `/v3/api-docs/**` and `/swagger-ui.html` are deliberately NOT
permitted; a request to `/api/v1/v3/api-docs` falls through to the default chain (default-deny →
non-200). The tests pin the observable outcome.

Scoped relaxed CSP for the docs chain:

```
default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';
img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'
```

All `'self'` (local assets); `'unsafe-inline'` for Swagger UI's inline styles/initializer;
`data:` for its inline images; `frame-ancestors 'none'` retained.

**Why over a single chain with per-path header writers / one shared permit list:** a second
chain scoped by `securityMatcher` is the idiomatic Spring Security way to give a subset of paths
a different permit AND header policy without any risk of bleeding into the API paths. It keeps
the two concerns physically separate and makes "the API's strict CSP is unchanged" true by
construction rather than by careful matcher ordering inside one chain.

### Decision 4 — Docs paths carry no OAuth2/JWT filter

The docs chain does not configure `oauth2ResourceServer`, so no bearer processing runs for docs
requests — appropriate for public static tooling and avoids any Problem-401 interaction.

### Decision 5 — Enabled in all profiles (no gating)

Swagger UI is enabled by default under all profiles (default H2 + `postgres`); discoverability
is the whole purpose of this public demo API. No profile/property gate is added now. If a future
locked-down production deployment wants docs off, the static entry page and webjar can be gated
later (e.g. via a profile-scoped resource handler) — noted as a deferred option, not built here.

### Decision 6 — Browserless verification, in H2DefaultRuntimeSmokeTest

Extend `H2DefaultRuntimeSmokeTest` (RANDOM_PORT, real `TestRestTemplate` honouring the context
path — a `@WebMvcTest` would exercise neither static-resource serving nor the real filter
chain). Assert:
1. `GET /swagger-ui/index.html` → `200`, no auth — the app-owned Swagger UI entry page loads
   publicly.
2. `GET` of a pinned webjar asset (e.g. `/webjars/swagger-ui/5.18.2/swagger-ui-bundle.js`) →
   `200`, no auth — the UI's local assets load without auth.
3. `GET /openapi/openapi.bundled.yaml` → `200`, no auth; body contains a known authored token
   (e.g. `listPeople` and/or the `/movies` path) — proving it is the authored contract.
4. `GET /v3/api-docs` → non-`200` (no springdoc, so no annotation-generated spec endpoint
   exists; it falls through to the default-deny chain) — proving no second spec is exposed.
5. A synthetic authenticated endpoint (`/__not-an-endpoint`, any non-public path) still returns
   Problem+json `401` without a token — proving the docs carve-out did not widen the public
   surface.

`PublicEndpointsConsistencyTest` is left unchanged and must stay green (docs paths not in
`PATTERNS`). `GeneratedApiCodegenTest` unchanged and green (no contract change).

Verify steps run as `./gradlew build -x spotlessCheck` (agents no longer format code).

## Risks / Trade-offs

- **A second, generated spec exposed** → eliminated by construction: no springdoc on the
  classpath, so no annotation scan can run. Test #4 still asserts `/v3/api-docs` is not a `200`
  generated document as a regression guard.
- **Stale/missing served bundle** → `processResources.dependsOn(bundleOpenApiSpec)` +
  `from(openApiBundledSpec)`; test #3 asserts the URL serves the authored content.
- **CSP still blanks the UI** (e.g. Swagger UI needs a directive not granted) → the scoped CSP
  grants script/style/img/font/connect from `'self'` + inline, which covers the entry page's
  inline `SwaggerUIBundle` init call and the webjar's `'self'` script/style/font assets and
  `data:` images; if a gap appears it is tuned only in the docs chain, never the global CSP.
  (Browserless tests confirm 200s, not visual render.)
- **Webjar asset-path drift across versions** → the entry page and the smoke-test asset
  assertion both reference the pinned exact version; bumping `org.webjars:swagger-ui` requires
  updating that one version string in both places. The pinned path avoids needing
  `webjars-locator-core`.
- **"Try it out" base URL** → the UI runs under `/api/v1`; the authored spec's `servers` +
  same-origin base make read-only calls resolve correctly. Low blast radius (read-only API).
