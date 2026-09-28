# uniform-responses Specification

## Purpose

Defines the uniform success form, failure form, correlation id and navigational links that every catalog capability inherits, and guarantees that the service refuses all writes (UC-000 BR-2, BR-3, BR-7).

## Requirements

### Requirement: Correlation id on every response
Every response SHALL carry an `X-Correlation-Id` header containing a UUID. This applies to success, failure and interface-description responses alike. If the request carries an `X-Correlation-Id` header holding a well-formed UUID, the service SHALL reuse that value. Otherwise it SHALL generate a new random UUID, and SHALL NOT reject the request. The same value SHALL appear in the JSON body (`meta.correlationId` or problem `correlationId`) and in every server log line written while handling the request. That includes log lines written while rendering a failure after the request has left normal handling. Acceptance check: web tests for supplied-valid, supplied-malformed and absent header. A log-capture test asserts that the id appears on the log line for a 500, and that a failure rendered by the service's error fallback carries the same id in the header, the body and the log.

#### Scenario: Correlation id generated when absent
- **WHEN** a client sends `GET /api/v1/ping` without `X-Correlation-Id`
- **THEN** the response `X-Correlation-Id` header is a UUID equal to `meta.correlationId`

#### Scenario: Well-formed inbound correlation id is honoured
- **WHEN** a client sends `GET /api/v1/ping` with `X-Correlation-Id: 3f2b8c1e-8d4a-4c1e-9f0a-2b7d6e5c4a31`
- **THEN** the response header and `meta.correlationId` both equal `3f2b8c1e-8d4a-4c1e-9f0a-2b7d6e5c4a31`

#### Scenario: Malformed inbound correlation id is replaced, not rejected
- **WHEN** a client sends `GET /api/v1/ping` with `X-Correlation-Id: not-a-uuid`
- **THEN** the response status is `200` and the response carries a newly generated UUID different from `not-a-uuid`

#### Scenario: Correlation id survives the error fallback
- **WHEN** a request fails outside normal request handling (for example, a fault raised in a request filter) and the failure is rendered by the error fallback
- **THEN** the problem body `correlationId`, the `X-Correlation-Id` header and the logged line for that failure all carry the same UUID

### Requirement: Uniform success envelope
Every `2xx` JSON response SHALL be labelled `Content-Type: application/json`, never `application/problem+json`, whatever the request's `Accept` header, and SHALL be an object with exactly two top-level members. `data` holds the payload. `meta` holds `timestamp` (an RFC 3339 date-time, when the answer was produced) and `correlationId`. Acceptance check: a JSON assertion on the availability response, with a fixed clock to assert `timestamp`.

#### Scenario: Success is never labelled as a problem
- **WHEN** a client sends `GET /api/v1/ping` with `Accept: application/problem+json`
- **THEN** the response status is `200`, the `Content-Type` is `application/json`, and the body is the success envelope

#### Scenario: Envelope metadata is populated
- **WHEN** a client sends `GET /api/v1/ping` while the service clock reads `2026-01-01T00:00:00Z`
- **THEN** the body has top-level keys `data` and `meta` only, and `meta.timestamp` is `2026-01-01T00:00:00Z`

### Requirement: Self-identifying, navigation-only links
Every successful resource payload SHALL carry `data._links.self.href`, an absolute URI from which the same result can be requested again with `GET`. When the service runs behind a proxy that sends standard forwarded headers, the href SHALL use the externally visible scheme and host. Links SHALL be navigational only. They SHALL NOT describe write actions or carry method or form affordances. Problem responses SHALL NOT carry `_links` or `_embedded`. Acceptance check: availability-check tests assert that `self` is present, including a test with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test` that asserts `self.href` is `https://api.example.test/api/v1/ping`. Problem tests assert that `_links` and `_embedded` are absent.

#### Scenario: Self link honours forwarded headers
- **WHEN** a client sends `GET /api/v1/ping` with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test`
- **THEN** `data._links.self.href` is `https://api.example.test/api/v1/ping`

#### Scenario: Problem responses carry no links
- **WHEN** a client sends `GET /api/v1/no-such-thing`
- **THEN** the problem body has no `_links` and no `_embedded` member

### Requirement: Uniform problem form
Every non-`2xx` response SHALL use `Content-Type: application/problem+json`. Its body SHALL contain `type` (a stable URI per failure kind), `title`, `status` (equal to the HTTP status), `code` (a stable machine code), `detail` and `correlationId`. It SHALL NOT include `instance`. It SHALL NOT include stack traces, exception class names, SQL, or framework-internal messages. Acceptance check: each failure-kind scenario below asserts every required member and asserts that `instance` is absent. The same scenarios assert that the body contains none of `exception`, `trace`, `java.` or `org.springframework`.

#### Scenario: Problem body has all required members
- **WHEN** a client sends `GET /api/v1/no-such-thing`
- **THEN** the body contains `type`, `title`, `status` = `404`, `code`, `detail` and `correlationId`, has no `instance`, and `correlationId` equals the `X-Correlation-Id` response header

### Requirement: Distinct failure kinds
Every failure SHALL be classified by its HTTP status into exactly one kind, and each kind SHALL have its own status, `code` and `type`:
- A path the service does not offer, requested with any method: `404`, `NOT_FOUND`.
- A method the service does not allow on a path it offers: `405`, `METHOD_NOT_ALLOWED`, with an `Allow` header.
- A representation the service cannot produce (unsatisfiable `Accept`): `406`, `NOT_ACCEPTABLE`.
- A request body media type the service does not accept: `415`, `UNSUPPORTED_MEDIA_TYPE`.
- Any other client-side fault (a malformed request, a missing or ill-typed parameter, or any other framework-detected 4xx): `400`, `BAD_REQUEST`.
- Any server-side fault, framework-detected or unexpected: `500`, `INTERNAL_ERROR`, with the generic detail `An unexpected error occurred.`

No framework-detected client fault SHALL be reported as `500`. Each `type` SHALL be distinct. Acceptance check: one web test per kind. The 400 and 500 tests use a test-only controller: it exposes an operation with a required UUID query parameter (for 400) and an operation that throws (for 500). The 500 test asserts that the exception message is absent from the body.

#### Scenario: Nothing exists at the path
- **WHEN** a client sends `GET /api/v1/no-such-thing`
- **THEN** the response status is `404` with `code` `NOT_FOUND`

#### Scenario: Method not allowed on an offered path
- **WHEN** a client sends `DELETE /api/v1/ping`
- **THEN** the response status is `405`, the `Allow` header includes `GET`, and the `code` is `METHOD_NOT_ALLOWED`

#### Scenario: Unsatisfiable Accept is a client failure, not a server fault
- **WHEN** a client sends `GET /api/v1/ping` with `Accept: application/xml`
- **THEN** the response status is `406`, the body is `application/problem+json`, and the `code` is `NOT_ACCEPTABLE`

#### Scenario: Unsupported request media type
- **WHEN** a client sends a request with `Content-Type: application/xml` to a test-only operation that consumes JSON
- **THEN** the response status is `415` with `code` `UNSUPPORTED_MEDIA_TYPE`

#### Scenario: Malformed request parameter
- **WHEN** a client calls the test-only operation with its required UUID query parameter set to `not-a-uuid`, and calls it again with the parameter omitted
- **THEN** both responses have status `400` and `code` `BAD_REQUEST`, and neither `detail` contains the parameter's internal type name

#### Scenario: Unexpected internal fault reveals nothing
- **WHEN** handling a request throws an unexpected exception whose message is `secret-db-host:5432 refused`
- **THEN** the response status is `500`, the `code` is `INTERNAL_ERROR`, the `detail` is `An unexpected error occurred.`, the body does not contain `secret-db-host`, and the server log contains the correlation id and the stack trace

#### Scenario: Failure kinds are distinguishable
- **WHEN** the `404`, `405`, `406`, `415`, `400` and `500` problem bodies are compared
- **THEN** each has a different `type` and a different `code`

### Requirement: Service is read-only and public
No request SHALL change any data. The service SHALL NOT require or validate credentials on any endpoint. `POST`, `PUT`, `PATCH` and `DELETE` SHALL NOT succeed on any path. The refusals are:
- On `/ping`: `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET`.
- On an offered catalog resource or collection path (`/movies`, and `/movies/{id}` whatever the value of `{id}`): `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET`.
- On an existing interface-description asset (`/api/v1/openapi/openapi.bundled.yaml`, `/api/v1/swagger-ui/index.html`): `405` `METHOD_NOT_ALLOWED`, with `Allow: GET, HEAD`.
- On a path the service does not offer: `404` `NOT_FOUND`. This is the same answer as for any other method on that path, because nothing exists there.

The refusal is always an `application/problem+json` response and never a `2xx`, `401`, `403` or `5xx`. Acceptance check: a parameterised web test over the four write methods on `/ping`, on `/movies`, on `/movies/{id}`, on both description assets and on an unknown path (`/no-such-thing/123`), with no CSRF token and no credentials. It asserts the exact status and, for `405`, the `Allow` header.

#### Scenario: Write to an unknown path is not found
- **WHEN** a client sends `PUT /api/v1/no-such-thing/123` with a JSON body and no credentials
- **THEN** the response status is `404`, and the body is `application/problem+json` with `code` `NOT_FOUND`

#### Scenario: Write to an offered catalog resource is not allowed
- **WHEN** a client sends `PUT /api/v1/movies/123` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, and the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`

#### Scenario: Write to an offered catalog collection is not allowed
- **WHEN** a client sends `POST /api/v1/movies` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, and the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`

#### Scenario: Write to a description asset is not allowed
- **WHEN** a client sends `POST /api/v1/openapi/openapi.bundled.yaml` or `DELETE /api/v1/swagger-ui/index.html` with no credentials
- **THEN** the response status is `405`, the `Allow` header is `GET, HEAD`, the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`, and the asset is unchanged on a following `GET`

### Requirement: Baseline security headers
Every response SHALL carry `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and a `Content-Security-Policy` that disallows inline scripts, with which the browsable description still works. A response to a request received over HTTPS, either directly or as indicated by `X-Forwarded-Proto: https`, SHALL also carry `Strict-Transport-Security`. A response to a request over plain HTTP SHALL NOT carry it. Acceptance check: header assertions on `/ping`, a 404 and the Swagger UI page, plus HSTS present and absent tests driven by `X-Forwarded-Proto`. The Swagger UI asset test from `platform/interface-description` confirms that the page loads no inline script.

#### Scenario: Security headers present on the availability check
- **WHEN** a client sends `GET /api/v1/ping`
- **THEN** the response carries `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and a `Content-Security-Policy` whose `script-src` (or `default-src`) does not contain `'unsafe-inline'`

#### Scenario: HSTS only over HTTPS
- **WHEN** a client sends `GET /api/v1/ping` once with `X-Forwarded-Proto: https` and once without it
- **THEN** only the first response carries `Strict-Transport-Security`

### Requirement: Same-origin only for browsers
The service SHALL NOT emit CORS allow headers. Cross-origin browser calls are therefore unsupported until a later change defines an allow-list. Acceptance check: a request with `Origin: https://other.example.test` to `/ping` gets no `Access-Control-Allow-Origin` header.

#### Scenario: No CORS grant for a foreign origin
- **WHEN** a client sends `GET /api/v1/ping` with `Origin: https://other.example.test`
- **THEN** the response carries no `Access-Control-Allow-Origin` header
