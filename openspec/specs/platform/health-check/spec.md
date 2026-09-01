# platform/health-check Specification

## Purpose
Provides a public, unauthenticated liveness endpoint that confirms the service is running
and that a request traverses every architectural layer, returning the standard success
envelope with a status, a server timestamp, and the request's correlation id.

## Requirements

### Requirement: Public liveness ping returns the standard success envelope

The system SHALL expose `GET /api/v1/ping` as a public endpoint requiring no authentication.
On success it SHALL respond `200 OK` with a JSON body conforming to the standard success
envelope: a `data` object and a `meta` object. The `data` object MUST contain a `status`
field with the literal value `"ok"` and a `timestamp` field (RFC 3339 / ISO-8601 date-time).
The `meta` object MUST contain a `timestamp` (date-time) and a `correlationId` (UUID).

Acceptance check: issue `GET /api/v1/ping` with no `Authorization` header; assert HTTP 200,
`Content-Type: application/json`, `data.status` == `"ok"`, `data.timestamp` parses as a
date-time, and `meta.correlationId` is a valid UUID.

#### Scenario: Unauthenticated client receives a successful ping
- **WHEN** a client sends `GET /api/v1/ping` with no `Authorization` header
- **THEN** the system responds `200 OK` with `Content-Type: application/json`
- **AND** the body is the standard envelope with `data.status` = `"ok"` and a `data.timestamp` date-time
- **AND** `meta.correlationId` is a valid UUID and `meta.timestamp` is a date-time

#### Scenario: Ping is served with no token or with a valid token
- **WHEN** a client sends `GET /api/v1/ping` with no `Authorization` header, or with a valid bearer token
- **THEN** the system does not return `401` or `403`
- **AND** the request is served as a public endpoint

<!-- Note: a request bearing a malformed/invalid bearer token is rejected 401 by the
     resource server's bearer-token filter even on a public path; that case is outside this
     public-endpoint guarantee (see design.md D3, BLOCKER 2 decision). -->

### Requirement: Every response carries a correlation id

The system SHALL ensure every HTTP request is associated with a correlation id: it SHALL
adopt an incoming correlation-id request header when present and otherwise generate a new
UUID. The correlation id SHALL be returned to the client (in the response, including the
ping envelope's `meta.correlationId`) and SHALL be available for inclusion in server log
lines for that request.

Acceptance check: call `GET /api/v1/ping` twice — once with a supplied correlation-id header
and once without; assert the supplied value is echoed back in `meta.correlationId`, and that
the header-less call still returns a valid UUID.

#### Scenario: Correlation id is generated when absent
- **WHEN** a client sends a request with no correlation-id header
- **THEN** the system generates a UUID correlation id for the request
- **AND** the generated id is returned to the client in the response

#### Scenario: Incoming correlation id is adopted
- **WHEN** a client sends a request carrying a correlation-id header
- **THEN** the system uses that value as the request's correlation id
- **AND** returns the same value to the client

### Requirement: Errors are returned as RFC 7807 problem details

The system SHALL return all non-2xx responses as `application/problem+json` conforming to
the shared `Problem` schema (`type`, `title`, `status`, `code`, `correlationId`, optional
`detail`/`instance`/`errors`). Error responses MUST NOT contain a stack trace, SQL, or
internal class names, and MUST include the request's correlation id.

Acceptance check: trigger a not-found route (or an unhandled error) and assert the response
`Content-Type` is `application/problem+json`, the body carries a stable `code` and the
`correlationId`, and no stack trace or internal detail is present.

#### Scenario: Unknown route returns a problem detail
- **WHEN** a client requests a path that maps to no endpoint
- **THEN** the system responds with `application/problem+json`
- **AND** the body contains `status`, a stable `code`, and the request `correlationId`
- **AND** the body contains no stack trace, SQL, or internal class name
