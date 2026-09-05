# platform/api-docs Specification

## Purpose
Defines how the application serves an interactive API documentation UI (Swagger UI) that
renders the authored bundled OpenAPI contract, so the public read API is discoverable from a
browser with zero setup without eroding contract-first (no second, drift-prone spec) or the
security posture (no widening of the contract's public surface, no weakening of the global CSP).

## Requirements

### Requirement: Interactive API docs render the authored bundled spec

The system SHALL serve an interactive Swagger UI page from the application at a stable URL
under the servlet context path (`/api/v1/swagger-ui/index.html`). The page SHALL be served from
the `org.webjars:swagger-ui` webjar's assets (no springdoc dependency). The UI SHALL load and
render the authored redocly-bundled OpenAPI spec — the same bundle that drives code generation —
and therefore SHALL list the real authored operations (e.g. `GET /movies`, `GET /people`). All
UI assets SHALL be served locally by the application from the webjar (no external CDN), so the
page works offline.

Acceptance check: with the app running on the default (H2) runtime, a GET of
`/api/v1/swagger-ui/index.html` returns `200` and a GET of the pinned webjar asset (e.g.
`/api/v1/webjars/swagger-ui/<version>/swagger-ui-bundle.js`) returns `200`; the served page is
configured to load the authored bundled spec URL (not an annotation-generated document).

#### Scenario: Swagger UI page is served under the context path
- **WHEN** a client requests `GET /api/v1/swagger-ui/index.html` on the running application
- **THEN** the response is `200` and returns the Swagger UI page
- **AND** the UI's local webjar assets are served by the application (no external CDN)
- **AND** the page is configured to load the authored bundled spec, listing the real authored operations

### Requirement: The served spec is the authored bundle and no second spec is exposed

The spec that Swagger UI consumes SHALL be the authored redocly-bundled OpenAPI document (the
same content used for code generation), served as a static resource at a stable URL under the
context path (`/api/v1/openapi/openapi.bundled.yaml`). Swagger UI SHALL render ONLY this
authored bundled spec. The system SHALL NOT produce or serve any annotation-generated OpenAPI
document: there is NO springdoc dependency on the classpath, so no annotation-generated spec can
exist and no annotation-generated spec endpoint (e.g. `/v3/api-docs`) is served.

Acceptance check: a GET of `/api/v1/openapi/openapi.bundled.yaml` returns `200` and its body
contains a known authored operation identifier (e.g. `listPeople` or the `/movies` path); a GET
of `/api/v1/v3/api-docs` does not return a generated OpenAPI spec (non-`200`, because no such
docs endpoint exists — the request falls through to the default-deny security chain).

#### Scenario: Authored bundled spec is served as a static resource
- **WHEN** a client requests `GET /api/v1/openapi/openapi.bundled.yaml`
- **THEN** the response is `200`
- **AND** the body is the authored bundled OpenAPI spec (it contains a known authored operation such as `listPeople` / the `/movies` path)

#### Scenario: No annotation-generated spec exists or is exposed
- **WHEN** a client requests `GET /api/v1/v3/api-docs`
- **THEN** no generated OpenAPI spec is returned (non-`200`; no annotation-generated spec endpoint exists because there is no springdoc dependency)

### Requirement: The bundled spec is placed on the runtime resources reproducibly from a clean build

The build SHALL place the authored bundled spec onto the runtime-served static resources such
that it is available at its stable URL from both `bootRun` and the boot jar, and it SHALL do so
reproducibly from a clean build: the resource-processing step SHALL depend on the bundling step
so the served spec is never stale or missing. The bundled spec SHALL remain a build artifact
and SHALL NOT be committed to source control.

Acceptance check: run a clean `./gradlew build`; assert the bundled spec is produced by the
bundling step and copied onto the served static resources before packaging, that the spec URL
resolves at runtime, and that the bundle is git-ignored (not tracked).

#### Scenario: Clean build makes the spec available at its runtime URL
- **WHEN** a developer runs `./gradlew build` on a clean checkout and starts the application
- **THEN** the resource-processing step has copied the freshly bundled spec onto the served resources
- **AND** the spec URL resolves at runtime (no `404`, not a stale bundle)
- **AND** the bundled spec is not tracked by source control

### Requirement: Docs paths are public without widening the contract's public surface

The Swagger UI page, the served spec, and the UI's asset paths SHALL be reachable with no
authentication, honouring the `/api/v1` context path. This carve-out SHALL be implemented via a
mechanism SEPARATE from the contract public-endpoints source of truth, so that source of truth
continues to agree EXACTLY with the OpenAPI operations marked `security: []` (the docs paths
SHALL NOT be added to it). Authenticated API endpoints SHALL continue to reject unauthenticated
requests with an RFC 7807 `401`.

Acceptance check: GETs of the Swagger UI URL and the spec URL each return `200` with no auth;
the contract public-endpoints consistency test still passes (docs paths absent from the
contract permit list); an unauthenticated request to an authenticated endpoint still returns a
Problem+json `401`.

#### Scenario: Docs surface is public
- **WHEN** an unauthenticated client requests the Swagger UI URL and the spec URL
- **THEN** each returns `200` (no `401`/`403`)

#### Scenario: Contract public-surface guarantee is intact
- **WHEN** the docs carve-out is in place
- **THEN** the contract public-endpoints source of truth still agrees exactly with the OpenAPI `security: []` operations (docs paths are not among them)
- **AND** an unauthenticated request to an authenticated endpoint still returns a Problem+json `401`

### Requirement: CSP relaxation is scoped to the docs paths only

Swagger UI requires its own scripts, styles, images and fonts, which the strict global
Content-Security-Policy (`default-src 'none'`) would block. The system SHALL apply a relaxed CSP
that permits the UI's own local assets to load, and that relaxation SHALL be scoped to the docs
paths only. The strict CSP on API responses and all other paths SHALL be unchanged.

Acceptance check: responses on the docs paths carry the relaxed CSP allowing the UI's local
assets; responses on API paths continue to carry the strict global CSP unchanged.

#### Scenario: Docs paths carry a relaxed CSP
- **WHEN** the Swagger UI page and its assets are served
- **THEN** their responses carry a CSP that permits the UI's own local scripts/styles/images/fonts

#### Scenario: API paths keep the strict CSP
- **WHEN** an API endpoint under the context path is requested
- **THEN** its response carries the strict global CSP (`default-src 'none'`), unchanged by this change
