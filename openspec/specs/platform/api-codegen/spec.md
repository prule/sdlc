# platform/api-codegen Specification

## Purpose
Defines the build-time contract between the authored OpenAPI spec and the generated server
interfaces and DTOs: generated code MUST reuse the shared response components the spec
declares, rather than emitting per-operation copies, so there is a single generated
`Problem` type and one envelope type per payload across the whole API.

## Requirements

### Requirement: Generated API interfaces bind to the shared response components

The code-generation pipeline SHALL produce server interfaces whose operation return types
are the shared component schemas defined in the OpenAPI spec, not per-operation copies. For
an operation whose `200` response `$ref`s a named envelope schema, the generated interface
method SHALL return that named type (e.g. `HealthApi.ping()` returns
`ResponseEntity<PingEnvelope>`).

Acceptance check: run `./gradlew openApiGenerate` and assert the generated `HealthApi`
declares `ResponseEntity<PingEnvelope> ping(...)`. This is asserted by an automated codegen
test that inspects the generated source (or compiled signature).

#### Scenario: Ping operation returns the shared envelope type
- **WHEN** the pipeline generates server interfaces from the current OpenAPI spec
- **THEN** the generated `HealthApi.ping` operation returns the shared `PingEnvelope` type
- **AND** it does not return a per-operation response wrapper type

### Requirement: No per-operation response DTO duplicates are generated

The pipeline SHALL NOT emit per-operation response wrapper models — model classes named for
an `operationId` plus HTTP status (the `<Operation><Status>Response`/`...ResponseData`/
`...ResponseMeta`/`...ResponseErrorsInner` family). Any response payload is represented only
by the shared named schema it `$ref`s.

Acceptance check: after `./gradlew openApiGenerate`, assert that no generated model class
name matches the pattern `^<AnyOperationId>[0-9]{3}Response` (e.g. `Ping200Response`,
`Ping500Response`, `Ping200ResponseData`) exists in the generated model package.

#### Scenario: No Ping200Response / Ping500Response duplicate models exist
- **WHEN** the pipeline generates models from the current OpenAPI spec
- **THEN** no `Ping200Response`, `Ping200ResponseData`, `Ping200ResponseMeta`,
  `Ping200ResponseMetaPagination`, `Ping500Response`, or `Ping500ResponseErrorsInner`
  model class is generated

### Requirement: A single shared Problem type is generated and reused

The pipeline SHALL generate exactly one `Problem` model (the RFC 7807 shape from
`components/schemas`) and every error response across all operations SHALL resolve to that
single type. No per-operation problem/error copy SHALL be generated.

Acceptance check: after generation, assert exactly one `Problem` model class exists and that
error responses reference it; assert no `<Operation><Status>Response*` error copies exist.

#### Scenario: Error responses resolve to the shared Problem type
- **WHEN** the pipeline generates models for operations that declare non-2xx responses
- **THEN** exactly one shared `Problem` model class is generated
- **AND** no per-operation error response copy is generated

### Requirement: The fix generalizes to any endpoint, not only ping

The reuse guarantee SHALL hold for every operation authored per `standards/openapi.md`, not
just the existing `ping` operation. Adding a second operation whose `200` response `$ref`s an
envelope-shaped schema and whose error response `$ref`s the shared error response SHALL
generate an interface returning the shared envelope type with no per-operation duplicates.

Acceptance check: with a second sample operation added to the spec, generation yields an
interface method returning the shared envelope type and produces no `<Op><Status>Response*`
models; removing the sample operation returns generation to the ping-only baseline.

#### Scenario: A second envelope-returning operation reuses shared types
- **WHEN** a second operation whose success response references a shared envelope schema is
  present in the spec and the pipeline generates code
- **THEN** the generated interface method for that operation returns the shared envelope type
- **AND** no per-operation `<Operation><Status>Response*` duplicate models are generated for it

### Requirement: The build resolves the authored spec before generation

The standard build SHALL resolve the authored multi-file OpenAPI spec into a single bundled
spec and generate from that bundled spec, with the resolve step ordered before generation so
`./gradlew build` performs it automatically. The bundled spec SHALL be a build artifact and
SHALL NOT be committed to source control.

Acceptance check: run a clean `./gradlew build`; assert the bundled spec is produced under
the build output directory before generation runs, that the build succeeds without a manual
pre-step, and that the bundled spec path is git-ignored (not tracked).

#### Scenario: Clean build produces the bundled spec before generating
- **WHEN** a developer runs `./gradlew build` on a clean checkout
- **THEN** the resolve step produces a single bundled spec under the build output directory
- **AND** generation consumes that bundled spec
- **AND** the bundled spec is not tracked by source control
