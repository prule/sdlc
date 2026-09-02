## MODIFIED Requirements

### Requirement: Public liveness ping returns the standard success envelope

The system SHALL expose `GET /api/v1/ping` as a public endpoint requiring no authentication.
On success it SHALL respond `200 OK` with a JSON body conforming to the standard success
envelope: a `data` object and a `meta` object. The `data` object MUST contain a `status`
field with the literal value `"ok"` and a `timestamp` field (RFC 3339 / ISO-8601 date-time),
and MUST carry a HAL `_links` object with a `self` link whose `href` addresses `GET /api/v1/ping`.
The `meta` object MUST contain a `timestamp` (date-time) and a `correlationId` (UUID). The
response media type stays `application/json` (the `self` link does not change content negotiation).

Acceptance check: issue `GET /api/v1/ping` with no `Authorization` header; assert HTTP 200,
`Content-Type: application/json`, `data.status` == `"ok"`, `data.timestamp` parses as a
date-time, `data._links.self.href` is an absolute URI ending in `/api/v1/ping`, and
`meta.correlationId` is a valid UUID.

#### Scenario: Unauthenticated client receives a successful ping
- **WHEN** a client sends `GET /api/v1/ping` with no `Authorization` header
- **THEN** the system responds `200 OK` with `Content-Type: application/json`
- **AND** the body is the standard envelope with `data.status` = `"ok"` and a `data.timestamp` date-time
- **AND** `data._links.self.href` is an absolute URI addressing `GET /api/v1/ping`
- **AND** `meta.correlationId` is a valid UUID and `meta.timestamp` is a date-time

#### Scenario: Ping is served with no token or with a valid token
- **WHEN** a client sends `GET /api/v1/ping` with no `Authorization` header, or with a valid bearer token
- **THEN** the system does not return `401` or `403`
- **AND** the request is served as a public endpoint
