## Why

The contract-first pipeline emits per-operation DTO copies instead of reusing the shared
response components the OpenAPI spec defines. `HealthApi.ping()` returns
`ResponseEntity<Ping200Response>`, and `Ping200Response`, `Ping200ResponseData`,
`Ping200ResponseMeta`, `Ping200ResponseMetaPagination`, `Ping500Response`, and
`Ping500ResponseErrorsInner` are generated, while the intended shared types
(`PingEnvelope`, `Envelope`, `Meta`, `Pagination`, `Problem`) are generated but orphaned.
As the API grows, every endpoint will emit its own `Xxx200Response`/`Xxx500Response*`
classes and there will be no single shared generated `Problem`/`Envelope` type — defeating
the reuse that `standards/openapi.md` §2–3 mandates (code-review Finding 3).

## What Changes

- Insert a `bundleOpenApiSpec` Gradle task that resolves the authored multi-file spec into a
  single bundled spec (via `redocly bundle`), and point `openApiGenerate` at that bundled
  file so the generator stops inlining/renaming cross-file response schemas per operation.
- Wire the ordering so `./gradlew build` (via `compileJava` → `openApiGenerate`) runs the
  bundle step first. The bundled spec is a build artifact under `build/`, never committed.
- Provision the bundler deterministically (pinned `@redocly/cli` version) and align the CI
  spec-lint step to the same pinned version.
- Add a codegen assertion test proving generated interfaces bind to the shared component
  types and that no per-operation `XxxNNNResponse*` duplicates are produced — verified
  against a second sample endpoint so the fix is shown to generalize beyond `ping`.
- Update `standards/openapi.md` §5 to document the authored-split → bundle → generate
  pipeline so future features inherit it.
- **No change to the HTTP contract, endpoints, or runtime behavior.** This is build tooling.

## Capabilities

### New Capabilities
- `platform/api-codegen`: The build-time contract between the authored OpenAPI spec and the
  generated server interfaces/DTOs — that generated code reuses the shared response
  components (single `Problem`, single envelope per payload) rather than per-operation copies.

### Modified Capabilities
<!-- None. The HTTP contract and health-check runtime behavior are unchanged. -->

## Impact

- **Build**: `build.gradle.kts` gains a `bundleOpenApiSpec` `Exec` task; `openApiGenerate`
  consumes the bundled spec and `dependsOn` it. Node/npx becomes a hard dependency of the
  core `./gradlew build`, not just CI (see design.md for the tradeoff and mitigations).
- **CI/tooling**: `.github/workflows/ci.yml` lint step pinned to the same `@redocly/cli`
  version; a `package.json` may pin the CLI as a devDependency for deterministic/offline
  provisioning. `.gitignore` covers the bundled spec output.
- **Generated code**: `HealthApi.ping()` returns `ResponseEntity<PingEnvelope>`; the
  `PingNNNResponse*` model classes are no longer generated. No hand-written source changes.
- **Docs**: `standards/openapi.md` §5 updated.
- **No API, DB schema, or runtime behavior change. No breaking change.**
