## ADDED Requirements

### Requirement: HAL link schemas generate as shared reused components

Introducing the HAL link shape into the OpenAPI spec SHALL NOT resurrect per-operation response
duplicates. The shared `Link` schema SHALL generate exactly one `Link` model, reused by every
resource's `_links`, and the `_links` object schemas SHALL bind to shared named components rather than
per-operation inlined copies. Adding HAL-bearing success responses SHALL NOT change the success media
type away from `application/json` in a way that emits a divergent generated model, and SHALL keep the
single shared `Problem` model intact for error responses.

Acceptance check: after `./gradlew openApiGenerate`, assert exactly one `Link` model class exists, no
generated model name matches `^<AnyOperationId>[0-9]{3}Response`, and exactly one `Problem` model
exists — asserted by the extended `GeneratedApiCodegenTest`.

#### Scenario: Link is a single shared generated model
- **WHEN** the pipeline generates models from the HAL-augmented spec
- **THEN** exactly one `Link` model class is generated
- **AND** resource `data` models reference the shared `_links`/`Link` components

#### Scenario: HAL augmentation produces no per-operation duplicates
- **WHEN** the pipeline generates models from the HAL-augmented spec
- **THEN** no `<Operation><Status>Response*` duplicate model is generated
- **AND** exactly one shared `Problem` model remains

### Requirement: The sample collection operation reuses shared envelope and link types

The trivial sample collection operation added to prove pagination links SHALL, like every other
operation, generate an interface method returning the shared envelope type for that payload and SHALL
NOT produce per-operation `<Operation><Status>Response*` duplicates. The paginated collection `data`
schema SHALL bind to shared `_links`/`_embedded` components.

Acceptance check: after generation, assert the sample collection operation's generated interface method
returns its shared envelope type and that no `<Operation><Status>Response*` model is generated for it.

#### Scenario: Sample collection operation binds to shared types
- **WHEN** the sample collection operation is present in the spec and the pipeline generates code
- **THEN** the generated interface method returns the shared envelope type for its payload
- **AND** no per-operation `<Operation><Status>Response*` duplicate model is generated for it
