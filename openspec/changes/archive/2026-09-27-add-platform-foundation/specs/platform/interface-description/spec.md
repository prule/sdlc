## Purpose

Publishes the service's own description of every capability it offers, both as a document tools can read and as a browsable page where a person can try capabilities. The description must always agree with actual behaviour (UC-000 BR-4, BR-5).

## ADDED Requirements

### Requirement: Tool-readable interface description
The service SHALL serve its complete interface description as an OpenAPI 3.1 YAML document at `GET /api/v1/openapi/openapi.bundled.yaml`. The document SHALL be public (no credentials) and served in every runtime mode. It SHALL be self-contained, with no external `$ref`s. Acceptance check: an HTTP test fetches the document without credentials, parses it as YAML, and asserts `openapi` starts with `3.1` and that no `$ref` value points outside the document.

#### Scenario: Tool fetches the description anonymously
- **WHEN** a client sends `GET /api/v1/openapi/openapi.bundled.yaml` with no credentials
- **THEN** the response status is `200` and the body parses as an OpenAPI 3.1 document containing the path `/ping`

#### Scenario: Description is available in persistent mode
- **WHEN** the service runs in persistent mode and a client fetches the description
- **THEN** the response status is `200` with the same document as in standalone mode

### Requirement: Browsable interface description with try-it-out
The service SHALL serve a browsable interface description at `GET /api/v1/swagger-ui/index.html`. The page SHALL be public and served in every runtime mode. It SHALL render the same tool-readable document, and it SHALL let a person execute an operation against the running service. Its servers SHALL resolve to the same origin under `/api/v1`. Acceptance check: an HTTP test asserts `200` `text/html` for the page and for every script and stylesheet it references. The description's `servers[0].url` is `/api/v1`. The page's configuration references `/api/v1/openapi/openapi.bundled.yaml`. A manual check (Gate 2) executes "try it out" on the availability check and sees a `200` result.

#### Scenario: Person opens the browsable description
- **WHEN** a client sends `GET /api/v1/swagger-ui/index.html` with no credentials
- **THEN** the response status is `200`, the `Content-Type` is `text/html`, and every asset the page references responds `200`

#### Scenario: Try-it-out targets the running service
- **WHEN** the served description is inspected
- **THEN** `servers[0].url` is `/api/v1` and the availability check path is `/ping`, so try-it-out calls `/api/v1/ping` on the same origin

### Requirement: Description matches behaviour
Every operation in the description SHALL be served by the service. Every documented response the service actually produces SHALL validate against the schema that the description declares for that operation and status. Acceptance check: a contract test loads the served description. For each documented operation, it asserts the route does not respond `404`. It then validates the real bodies of `GET /ping` (200), `GET /ping` with `Accept: application/xml` (406), `GET` and `PUT` on an unknown path (404 each) and `POST /ping` (405) against the declared schemas, using a JSON Schema validator. Declared schemas are closed (`additionalProperties: false` on `PingData`, `PingLinks`), so an extra key in a response body fails validation.

#### Scenario: Availability answer conforms to its declared schema
- **WHEN** a contract test calls `GET /api/v1/ping` and validates the body against the `200` response schema declared for `/ping` in the served description
- **THEN** validation reports no errors

#### Scenario: Failure answers conform to the shared problem schema
- **WHEN** a contract test triggers a `404`, a `405` and a `406` and validates each body against the shared `Problem` schema in the served description
- **THEN** validation reports no errors, and no body carries an `instance` member

#### Scenario: Extra key in a response body is caught
- **WHEN** the contract test validates a `/ping` body that carries a key not declared in `PingData`
- **THEN** validation reports an error

### Requirement: No undocumented HTTP capabilities
The service SHALL NOT expose any HTTP endpoint that returns a JSON or YAML payload and is absent from the interface description. The only exception is the interface description itself: the documents and assets that make up the browsable and tool-readable forms. In particular, management/actuator endpoints (including the actuator discovery root) and generated API-doc endpoints SHALL NOT be reachable. Acceptance check: `GET /api/v1/actuator`, `GET /api/v1/actuator/health` and `GET /api/v1/v3/api-docs` each return `404` problem+json.

#### Scenario: Actuator discovery root is not exposed
- **WHEN** a client sends `GET /api/v1/actuator`
- **THEN** the response status is `404` and the body is a problem with `code` `NOT_FOUND`

#### Scenario: Management endpoint is not exposed
- **WHEN** a client sends `GET /api/v1/actuator/health`
- **THEN** the response status is `404` and the body is a problem with `code` `NOT_FOUND`

### Requirement: Shared concepts defined once
Every shared concept that the served description contains SHALL appear exactly once, as a named component, and every use SHALL reference it rather than redefine it. The shared concepts are the success-envelope metadata (`Meta`), `Link`, `Problem`, each reusable error response, and the correlation-id response header. No response SHALL use an inline object schema. A shared concept that no operation references yet is not required to appear in the served description; it is added when the first operation uses it. Acceptance check: a test on the served document asserts that `Meta`, `Link`, `Problem` and the `X-Correlation-Id` header each appear exactly once under `components`. It also asserts that every response `content` schema in the document is a `$ref`, and that every `application/problem+json` response resolves to the one `Problem` schema.

#### Scenario: Availability error responses reuse the shared problem
- **WHEN** the served description is inspected
- **THEN** every non-2xx response of `/ping` uses the `application/problem+json` media type and a `$ref` to the single `Problem` schema

#### Scenario: No duplicated shared schema
- **WHEN** the served description is inspected
- **THEN** `components.schemas` contains exactly one `Problem`, one `Meta` and one `Link`, and no other schema has the same set of properties as `Problem`
