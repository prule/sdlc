# availability-check Specification

## Purpose

Lets any API consumer confirm, without credentials, that the catalog service is running (liveness), even when the catalog holds no data (UC-000 BR-1).

## Requirements

### Requirement: Public availability check
The service SHALL expose `GET /api/v1/ping`. It SHALL require no credentials, and it SHALL return `200` with `Content-Type: application/json` whenever the service process is running and able to handle HTTP requests. The answer SHALL NOT depend on reaching catalog data (liveness only; readiness is out of scope). The success Content-Type is `application/json` even when the request's `Accept` names only `application/problem+json`. Acceptance check: an HTTP test with no `Authorization` header receives `200`, and a test with `Accept: application/problem+json` receives `200` with `Content-Type: application/json`.

#### Scenario: Anonymous consumer checks availability
- **WHEN** a client sends `GET /api/v1/ping` with no `Authorization` header
- **THEN** the response status is `200` and the `Content-Type` is `application/json`

#### Scenario: Availability confirmed on an empty catalog
- **WHEN** the service has started with no catalog content and a client sends `GET /api/v1/ping`
- **THEN** the response status is `200`

#### Scenario: Credentials are ignored, not required
- **WHEN** a client sends `GET /api/v1/ping` with an arbitrary `Authorization: Bearer x` header
- **THEN** the response status is `200` (the header is neither validated nor rejected)

### Requirement: Availability answer content
The `200` body SHALL be the standard success envelope. `data.status` SHALL equal `"UP"`. `data._links.self.href` SHALL resolve to `/api/v1/ping`. `meta` SHALL carry `timestamp` and `correlationId`. `data` SHALL NOT carry version, build, deployment or dependency details. Acceptance check: a JSON assertion on the full body; the body must validate against the `PingEnvelope` schema in the served description.

#### Scenario: Answer is self-identifying and minimal
- **WHEN** a client sends `GET /api/v1/ping`
- **THEN** `data.status` is `"UP"`, `data._links.self.href` ends with `/api/v1/ping`, and `data` has no properties other than `status` and `_links`

#### Scenario: Following the self link repeats the check
- **WHEN** a client sends `GET` to the `data._links.self.href` returned by a previous availability check
- **THEN** the response status is `200` with `data.status` equal to `"UP"`

### Requirement: Availability check is harmless
The availability check SHALL NOT change any state. Write methods on `/api/v1/ping` SHALL be refused as defined in `platform/uniform-responses` (method not allowed). Acceptance check: `POST`, `PUT`, `PATCH` and `DELETE` each return `405` problem+json. A following `GET` still returns `200` with the same body shape.

#### Scenario: Write attempt on the availability check is refused
- **WHEN** a client sends `POST /api/v1/ping`
- **THEN** the response status is `405`, the `Allow` header lists `GET`, and the body is a problem with `code` `METHOD_NOT_ALLOWED`

#### Scenario: Unsupported representation requested
- **WHEN** a client sends `GET /api/v1/ping` with `Accept: application/xml`
- **THEN** the response status is `406`, the body is `application/problem+json` with `code` `NOT_ACCEPTABLE`, and the status is not `500`
