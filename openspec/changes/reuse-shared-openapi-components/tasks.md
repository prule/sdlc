<!-- Settled: Node/npx (pinned @redocly/cli) as a hard dependency of the core build is
     accepted — the dev container bakes in redocly. No gate; proceed. -->


## 1. Provision the bundler (deterministic)

- [x] 1.1 Add a committed `package.json` at repo root pinning `@redocly/cli` to a fixed stable version as a `devDependency`; add `package-lock.json`.
- [x] 1.2 Align the CI spec-lint step in `.github/workflows/ci.yml` to run the pinned `@redocly/cli` (via `npm ci` + local binary) instead of `@redocly/cli@latest`; add a Node setup step and cache `node_modules`. Note: the lint target REMAINS the authored spec (`src/main/resources/openapi/openapi.yaml`) — do not repoint lint at the bundled `build/openapi/...` artifact.
- [x] 1.3 Confirm the bundled output under `build/` (`build/openapi/openapi.bundled.yaml`) is git-ignored — already covered by the existing `build/` rule in `.gitignore`; do not add a duplicate entry.

## 2. Wire bundle → generate into the Gradle build

- [x] 2.1 Add a `bundleOpenApiSpec` `Exec` task in `build.gradle.kts` that runs `redocly bundle src/main/resources/openapi/openapi.yaml` and writes `build/openapi/openapi.bundled.yaml`; declare its inputs (the authored spec tree) and output for up-to-date checks.
- [x] 2.2 Point `openApiGenerate.inputSpec` at the bundled spec and add `dependsOn("bundleOpenApiSpec")`; confirm `compileJava → openApiGenerate → bundleOpenApiSpec` ordering so `./gradlew build` runs it with no manual pre-step.

## 3. Regenerate and confirm reuse

- [x] 3.1 Run a clean `./gradlew clean openApiGenerate`; confirm `HealthApi.ping()` returns `ResponseEntity<PingEnvelope>` and that `Ping200Response`, `Ping200ResponseData`, `Ping200ResponseMeta`, `Ping200ResponseMetaPagination`, `Ping500Response`, and `Ping500ResponseErrorsInner` are no longer generated.
- [x] 3.2 Update `PingController` (inbound web adapter) to the new generated signature: return `ResponseEntity<PingEnvelope>`; build the payload from the shared types — `PingData.StatusEnum` replaces `Ping200ResponseData.StatusEnum`, `new Meta(timestamp, correlationId)` replaces `new Ping200ResponseMeta(...)`, `new PingEnvelope(data, meta)` replaces `new Ping200Response(...)`; fix imports. Mapping logic and emitted JSON are unchanged.
- [x] 3.3 Confirm exactly one `Problem` model is generated and that error responses resolve to it; run `./gradlew build` and confirm it compiles against the updated controller and the existing web tests pass unchanged (the wire shape is identical).

## 4. Verify the fix generalizes (second endpoint)

- [x] 4.1 Temporarily add a second sample operation to the authored spec whose `200` references an envelope-shaped schema and whose `500` references the shared `InternalError`; regenerate and confirm its interface method returns the shared envelope type with no `<Op><Status>Response*` duplicate models.
- [x] 4.2 Remove the temporary sample operation and regenerate; confirm generation returns to the ping-only baseline (no leftover models).

## 5. Lock the guarantee with an automated test

- [x] 5.1 Add a JUnit codegen assertion test that inspects the generated sources and asserts: `HealthApi.ping` returns `ResponseEntity<PingEnvelope>`; no generated model name matches `^<AnyOperationId>[0-9]{3}Response`; exactly one `Problem` model exists. Ensure it runs as part of `./gradlew build`.

## 6. Documentation

- [x] 6.1 Update `standards/openapi.md` §5 to document the authored-split → `redocly bundle` → `openapi-generator` pipeline, the pinned-CLI provisioning, and the git-ignored bundled artifact, so future features inherit it.
