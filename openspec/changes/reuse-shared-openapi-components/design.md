## Context

See proposal.md (Why). The authored spec at `src/main/resources/openapi/` is already
`standards/openapi.md`-compliant: split by domain, `$ref` reuse throughout. The `ping`
operation's `200` response references `#/components/schemas/PingEnvelope`, and its `500`
references the shared `#/components/responses/InternalError`. The defect is entirely in
`openapi-generator`'s spring generator: when it walks a response schema assembled from
cross-file `$ref`s, it inlines the composition and names the result after the operation +
status, producing `Ping200Response`, `Ping200ResponseData`, `Ping200ResponseMeta`,
`Ping200ResponseMetaPagination`, `Ping500Response`, `Ping500ResponseErrorsInner`. The shared
`PingEnvelope`/`Envelope`/`Meta`/`Pagination`/`Problem` models are still emitted but nothing
binds to them. `HealthApi.ping()` currently returns `ResponseEntity<Ping200Response>`.

Constraint: this repo is contract-first (CLAUDE.md, openapi.md §5). Hand-writing DTOs to
patch the return types is explicitly disallowed. The current build is pure JVM: the org.openapi
Gradle plugin drives generation; CI already invokes `npx @redocly/cli lint` for spec linting.

### OpenAPI operations added/changed

None permanently. The HTTP contract is unchanged — `GET /api/v1/ping` keeps returning the
same JSON. During implementation a **temporary** second sample operation is added to prove
generalization and then removed (see tasks). Illustrative snippet of what generation must
yield (not a spec change):

```java
// generated com.acme.generated.api.HealthApi
ResponseEntity<PingEnvelope> ping(UUID xCorrelationId);   // was ResponseEntity<Ping200Response>
```

### Components touched (dependency direction)

Build tooling: `build.gradle.kts`, `.github/workflows/ci.yml`, optional `package.json`, and
`standards/openapi.md`. One production Java file: `PingController` (the inbound web adapter).
It currently imports and constructs the deleted `Ping200Response`/`Ping200ResponseData`/
`Ping200ResponseMeta` types and overrides `ResponseEntity<Ping200Response> ping(UUID)`; once
those models stop being generated the file will not compile, so its single mapping is
repointed to the shared `PingEnvelope`/`PingData`/`Meta` types (see D5). This edit is confined
to the inbound adapter — no `domain` or `application` change — so dependency direction
(inward-only) is unaffected and no production Java moves between layers.

## Goals / Non-Goals

**Goals:**
- Generated interfaces/DTOs reuse the shared component types; one `Problem`, one envelope per
  payload; zero per-operation `<Op><Status>Response*` classes.
- The fix is wired into the standard `./gradlew build` with no manual pre-step.
- Provisioning of the bundler is deterministic and documented so future features inherit it.

**Non-Goals:**
- Changing any HTTP contract, endpoint, or runtime behavior.
- Switching code generators or restructuring the authored split spec.
- Committing the bundled spec (it is a derived build artifact).

## Decisions

### D1. Bundle the spec with `redocly bundle`, then generate from the bundled file (Option A — chosen)

Add a Gradle `Exec` task `bundleOpenApiSpec` that runs `redocly bundle` on
`src/main/resources/openapi/openapi.yaml` and writes a single resolved spec to
`build/openapi/openapi.bundled.yaml`. Point `openApiGenerate.inputSpec` at that bundled file
and make `openApiGenerate.dependsOn("bundleOpenApiSpec")`. `compileJava` already depends on
`openApiGenerate`, so `./gradlew build` runs bundle → generate → compile in order.

`redocly bundle` (unlike a resolve-fully pass) lifts every `$ref` target into a single
document's `components` and keeps operations referencing them by name. The generator then
sees one self-contained file with named component schemas and binds operation responses to
those named types — yielding `ResponseEntity<PingEnvelope>` and a single `Problem`, with no
per-operation copies. Confirmed by the implementer's investigation.

**Why over the alternatives:**
- **Option B (avoid Node).** Rejected as unsubstantiated. The `org.openapi.generator` plugin
  and its `openapi-yaml` generator apply the same internal model-renaming; `swagger-parser`'s
  `resolveFully` inlines schemas (the very behavior that causes the duplication) rather than
  preserving named component refs; no spec-authoring shape was found that stops the inlining.
  Do not adopt a JVM-only path without a working proof that generation binds to the shared
  types — if one is later demonstrated it is strictly preferable (drops the Node dependency).
- **Option C (accept duplication + hand-written Problem/Envelope mapping).** Rejected. It
  reintroduces hand-written DTOs that duplicate the contract, violating openapi.md §5 and the
  CLAUDE.md contract-first rule, and leaves every future endpoint emitting its own copies.

### D2. Pin the bundler version; provision via committed `package.json` devDependency

Pin `@redocly/cli` to a fixed version and provision it through a committed `package.json`
(`devDependencies`) resolved with `npm ci`, rather than `npx @redocly/cli@latest`. This makes
bundling reproducible (no silent CLI drift changing generated output), enables offline builds
via a warm npm cache, and lets CI cache `node_modules`. Align the CI lint step
(`.github/workflows/ci.yml`, currently `@redocly/cli@latest`) to the same pinned version so
lint and bundle use one CLI. Alternative — `npx @latest` each run — is simpler but
non-deterministic and network-dependent; rejected for a core-build tool.

### D3. Verify with a temporary second endpoint, then assert via a codegen test

Prove generalization by adding a throwaway second operation (its `200` references an
envelope-shaped schema, its `500` references the shared `InternalError`), regenerating, and
asserting the generated interface returns the shared envelope type with no
`<Op><Status>Response*` models — then remove the sample operation. Lock the guarantee in with
an automated codegen assertion test (JUnit) that inspects generated sources for the `ping`
operation, so a future regression (e.g. someone reverts to the multi-file input) fails the
build. Alternative — one-off manual check — leaves no regression guard; rejected.

### D4. Bundled spec is a build artifact, git-ignored

Write the bundled spec under `build/`, which the existing `.gitignore` `build/` rule already
covers. It is derived from the authored spec on every build; committing it would create a
second source of truth that can drift. The authored split spec under
`src/main/resources/openapi/` remains the single source.

### D5. Repoint `PingController` to the shared generated types (required, not optional)

Removing the `Ping200Response*` family breaks `PingController`, which imports and constructs
them. The controller's `ping` mapping is updated in lockstep with regeneration: return type
`ResponseEntity<PingEnvelope>`; `PingData.StatusEnum` replaces `Ping200ResponseData.StatusEnum`;
`new Meta(timestamp, correlationId)` replaces `new Ping200ResponseMeta(...)`; `new
PingEnvelope(data, meta)` replaces `new Ping200Response(...)`; imports fixed. The mapping logic
and the emitted JSON are unchanged — the shared schemas are field-for-field identical to the
per-operation copies — so this is a mechanical rename within the inbound adapter, not a
behavior change. Alternative — leaving the controller untouched — does not compile; there is no
zero-edit path.

## Risks / Trade-offs

- **Node/npx becomes a hard dependency of the core `./gradlew build`, not just CI.** → Node is
  provisioned in the dev environment (the dev container bakes in redocly) and the requirement is
  documented in `standards/openapi.md` §5; the CLI is pinned via `package.json` (D2) so
  provisioning is one `npm ci`. This is the primary tradeoff and has been accepted (see Settled
  Decisions) — a bare-host developer without Node can no longer build.
- **Air-gapped / offline builds.** → `npx @latest` would fetch on every run; the pinned
  `package.json` + warm npm cache (D2) lets `npm ci` run offline. First provisioning still needs
  network or a pre-seeded cache/mirror.
- **redocly `bundle` output shape changes across versions could alter generated names.** →
  Pinned version (D2) plus the codegen assertion test (D3) catch any drift at build time.
- **Generated `ping` return type changes from `Ping200Response` to `PingEnvelope`, and the
  `Ping200Response*` models are deleted, so `PingController` stops compiling.** → `PingController`
  is edited in the same change to build the payload from the shared `PingEnvelope`/`PingData`/
  `Meta` types (D5); the edit is a mechanical rename confined to the inbound adapter. The wire
  shape is identical, so existing web tests stay valid and there is no runtime behavior change;
  the new codegen test guards against regression.

## Migration Plan

No DB schema change, so **no Flyway migration and no DB rollback consideration**. Rollout is a
build-config change deployed via the normal PR/CI flow:
1. Add pinned `package.json` + `bundleOpenApiSpec` task; repoint `openApiGenerate`; ignore the
   bundled artifact.
2. Regenerate; confirm `HealthApi.ping()` returns `ResponseEntity<PingEnvelope>` and no
   `Ping*Response*` models remain; update `PingController` to the shared types (D5) so the build compiles.
3. Add the codegen assertion test; update `standards/openapi.md` §5; align the CI lint version.

Rollback: revert the build-config commit — `openApiGenerate` returns to the multi-file input
and the prior generated shape. No data or contract to unwind.

## Settled Decisions

- **Node/redocly as a core-build dependency — accepted.** Making Node/npx (via the pinned
  `@redocly/cli`) a hard dependency of the core `./gradlew build`, not just CI, is accepted: the
  dev container bakes in redocly, so local builds have it provisioned, and Option A proceeds
  rather than being held for a JVM-only bundler.

## Open Questions

- Exact `@redocly/cli` version to pin — pick the current stable at implementation time and
  match CI to it (a minor detail; does not change the approach or task breakdown).
