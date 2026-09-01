<!-- Gate: design.md Open Questions flags a human decision — accepting Node/npx as a hard
     dependency of the core build. Confirm that before starting task group 2. -->

## 1. Provision the bundler (deterministic)

- [ ] 1.1 Add a committed `package.json` at repo root pinning `@redocly/cli` to a fixed stable version as a `devDependency`; add `package-lock.json`.
- [ ] 1.2 Align the CI spec-lint step in `.github/workflows/ci.yml` to run the pinned `@redocly/cli` (via `npm ci` + local binary) instead of `@redocly/cli@latest`; add a Node setup step and cache `node_modules`.
- [ ] 1.3 Add the bundled-spec output path (`build/openapi/openapi.bundled.yaml`) to `.gitignore`.

## 2. Wire bundle → generate into the Gradle build

- [ ] 2.1 Add a `bundleOpenApiSpec` `Exec` task in `build.gradle.kts` that runs `redocly bundle src/main/resources/openapi/openapi.yaml` and writes `build/openapi/openapi.bundled.yaml`; declare its inputs (the authored spec tree) and output for up-to-date checks.
- [ ] 2.2 Point `openApiGenerate.inputSpec` at the bundled spec and add `dependsOn("bundleOpenApiSpec")`; confirm `compileJava → openApiGenerate → bundleOpenApiSpec` ordering so `./gradlew build` runs it with no manual pre-step.

## 3. Regenerate and confirm reuse

- [ ] 3.1 Run a clean `./gradlew clean openApiGenerate`; confirm `HealthApi.ping()` returns `ResponseEntity<PingEnvelope>` and that `Ping200Response`, `Ping200ResponseData`, `Ping200ResponseMeta`, `Ping200ResponseMetaPagination`, `Ping500Response`, and `Ping500ResponseErrorsInner` are no longer generated.
- [ ] 3.2 Confirm exactly one `Problem` model is generated and that error responses resolve to it; run `./gradlew build` and confirm the existing controller compiles against the new generated signature with no source changes and existing web tests pass.

## 4. Verify the fix generalizes (second endpoint)

- [ ] 4.1 Temporarily add a second sample operation to the authored spec whose `200` references an envelope-shaped schema and whose `500` references the shared `InternalError`; regenerate and confirm its interface method returns the shared envelope type with no `<Op><Status>Response*` duplicate models.
- [ ] 4.2 Remove the temporary sample operation and regenerate; confirm generation returns to the ping-only baseline (no leftover models).

## 5. Lock the guarantee with an automated test

- [ ] 5.1 Add a JUnit codegen assertion test that inspects the generated sources and asserts: `HealthApi.ping` returns `ResponseEntity<PingEnvelope>`; no generated model name matches `^<AnyOperationId>[0-9]{3}Response`; exactly one `Problem` model exists. Ensure it runs as part of `./gradlew build`.

## 6. Documentation

- [ ] 6.1 Update `standards/openapi.md` §5 to document the authored-split → `redocly bundle` → `openapi-generator` pipeline, the pinned-CLI provisioning, and the git-ignored bundled artifact, so future features inherit it.
