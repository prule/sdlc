## Why

UC-000: an API consumer must be able to confirm that the catalog service is available and learn how to use it from the service itself, before any catalog data exists. The repo is at a bare bootstrap. `build.gradle.kts` bundles and generates from `src/main/resources/openapi/openapi.yaml`, but that file does not exist yet, so the build cannot pass. Every later `catalog` capability inherits the uniform result, failure and navigation conventions defined here, so they must be fixed first.

## What Changes

- Add the first OpenAPI 3.1 contract, split by domain. Shared components (`Meta`, `Link`, `Problem`, the reusable error responses `/ping` can return, and the `X-Correlation-Id` header) are defined once and referenced by `$ref`. Components no operation references yet are left out and arrive with their first user.
- Add a public, read-only **availability check** (`GET /api/v1/ping`). It answers from the running process only (liveness), succeeds on an empty catalog, uses the standard envelope and carries a `self` link.
- Publish the **interface description** in two forms: the tool-readable bundled spec (`/api/v1/openapi/openapi.bundled.yaml`) and the browsable Swagger UI with try-it-out (`/api/v1/swagger-ui/index.html`). Both are public in every runtime mode. No undocumented HTTP endpoints are exposed, so Actuator's HTTP surface (including its discovery root) is turned off.
- Add **uniform results**. Each request gets a correlation id: a well-formed inbound `X-Correlation-Id` is honoured, otherwise a new one is generated. The id is echoed in the response header, in `meta` and in `Problem`, and written to logs. All failures use `application/problem+json` and fall into distinct kinds, classified by status: not found (404), method not allowed (405), not acceptable (406), unsupported media type (415), bad request (400, any other client fault) and internal error (500, generic message, no internal detail). No framework-detected client fault is reported as 500.
- Enforce **read-only and public** access. A permit-all, stateless security config is added with no authentication. Write methods never succeed and change nothing: they get 405 on offered paths and 404 on paths that are not offered. Baseline security headers are set, including HSTS on HTTPS requests forwarded by the gateway. The service is same-origin only: no CORS is configured.
- Formalise the **runtime modes**. Standalone mode (default, in-memory H2, no data retained) and persistent mode (`postgres` profile) must behave identically for every UC-000 behaviour.

No breaking API change and no DB schema change: this is the first contract, and no Flyway migration is added.

## Non-goals

- Any catalog content (movies, people, credits, genres, reviews).
- Authentication, accounts, API keys, JWT validation.
- Rate limiting.
- Version, build or deployment details on the availability answer.
- Readiness (reaching catalog data), operational monitoring, alerting and dashboards.
- Pagination components (`Pagination`, `page`/`size` parameters). No collection exists yet, so the first collection capability adds them additively (see design.md).
- CORS for browser-based third-party consumers (same-origin only, decided at Gate 1).

## Capabilities

### New Capabilities
- `platform/availability-check`: the public, harmless "are you available?" operation (liveness only).
- `platform/interface-description`: the published, accurate interface description in browsable (try-it-out) and tool-readable form. Shared concepts are defined once. There are no undocumented endpoints.
- `platform/uniform-responses`: the success envelope, correlation id, self-identifying navigation links, the uniform problem form with distinct failure kinds, and read-only refusal of writes.
- `platform/runtime-modes`: standalone (no external infrastructure, data not retained) and persistent modes, with identical behaviour.

### Modified Capabilities
None (`openspec/specs/` is empty).

## Impact

- New: `src/main/resources/openapi/**` and `com.acme.platform.**` (availability slice plus shared web infrastructure: correlation filter, exception handler, error controller, security config, clock). The Swagger UI initializer moves out of the inline `<script>` so the CSP can be strict.
- Config: `application.yml` (`management.server.port: -1`, whitelabel error page off, `server.forward-headers-strategy: framework`).
- Test-only dependency: a JSON Schema validator, used to check real responses against the served spec.
- Docs: `standards/error-handling.md` §3 gains 405, 406 and 415 rows and the "other 4xx → 400" rule (approved at Gate 1, Q1).
