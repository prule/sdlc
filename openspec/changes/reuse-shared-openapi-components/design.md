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

Build tooling only: `build.gradle.kts`, `.github/workflows/ci.yml`, `.gitignore`, optional
`package.json`, and `standards/openapi.md`. No `domain`, `application`, or `adapters` source
changes. The controller already implements the generated `HealthApi`; when the generated
return type becomes `PingEnvelope`, the existing controller mapping continues to satisfy the
generated interface. Dependency direction (inward-only) is unaffected — no production Java
moves between layers.

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

Write the bundled spec under `build/` and add it to `.gitignore`. It is derived from the
authored spec on every build; committing it would create a second source of truth that can
drift. The authored split spec under `src/main/resources/openapi/` remains the single source.

## Risks / Trade-offs

- **Node/npx becomes a hard dependency of the core `./gradlew build`, not just CI.** → Provision
  Node in the dev environment (a devcontainer scaffold is available in this repo's tooling) and
  document the requirement in `standards/openapi.md` §5; pin the CLI via `package.json` (D2) so
  provisioning is one `npm ci`. This is the primary tradeoff and needs a human sign-off (see
  Open Questions) — a bare-host developer without Node can no longer build.
- **Air-gapped / offline builds.** → `npx @latest` would fetch on every run; the pinned
  `package.json` + warm npm cache (D2) lets `npm ci` run offline. First provisioning still needs
  network or a pre-seeded cache/mirror.
- **redocly `bundle` output shape changes across versions could alter generated names.** →
  Pinned version (D2) plus the codegen assertion test (D3) catch any drift at build time.
- **Generated `ping` return type changes from `Ping200Response` to `PingEnvelope`.** → The
  controller implements the generated interface and already builds the envelope payload;
  recompilation against the new signature is the intended outcome, covered by existing web tests
  plus the new codegen test. No runtime behavior change.

## Migration Plan

No DB schema change, so **no Flyway migration and no DB rollback consideration**. Rollout is a
build-config change deployed via the normal PR/CI flow:
1. Add pinned `package.json` + `bundleOpenApiSpec` task; repoint `openApiGenerate`; ignore the
   bundled artifact.
2. Regenerate; confirm `HealthApi.ping()` returns `ResponseEntity<PingEnvelope>` and no
   `Ping*Response*` models remain; the existing controller compiles unchanged.
3. Add the codegen assertion test; update `standards/openapi.md` §5; align the CI lint version.

Rollback: revert the build-config commit — `openApiGenerate` returns to the multi-file input
and the prior generated shape. No data or contract to unwind.

## Open Questions

- **Human decision required:** Is making Node/npx a hard dependency of the core `./gradlew
  build` (not just CI) acceptable, given every local build and any air-gapped build now needs
  Node provisioned? If not acceptable, the change should be held pending a substantiated
  JVM-only bundler (Option B) rather than shipped.
- Exact `@redocly/cli` version to pin — pick the current stable at implementation time and
  match CI to it (does not change the approach or task breakdown).
