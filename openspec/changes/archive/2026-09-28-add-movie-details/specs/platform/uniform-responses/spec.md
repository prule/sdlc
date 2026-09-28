## MODIFIED Requirements

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
- On an offered catalog resource path (`/movies/{id}`, whatever the value of `{id}`): `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET`.
- On an existing interface-description asset (`/api/v1/openapi/openapi.bundled.yaml`, `/api/v1/swagger-ui/index.html`): `405` `METHOD_NOT_ALLOWED`, with `Allow: GET, HEAD`.
- On a path the service does not offer: `404` `NOT_FOUND`. This is the same answer as for any other method on that path, because nothing exists there.

The refusal is always an `application/problem+json` response and never a `2xx`, `401`, `403` or `5xx`. Acceptance check: a parameterised web test over the four write methods on `/ping`, on `/movies/{id}`, on both description assets and on an unknown path (`/no-such-thing/123`), with no CSRF token and no credentials. It asserts the exact status and, for `405`, the `Allow` header.

#### Scenario: Write to an unknown path is not found
- **WHEN** a client sends `PUT /api/v1/no-such-thing/123` with a JSON body and no credentials
- **THEN** the response status is `404`, and the body is `application/problem+json` with `code` `NOT_FOUND`

#### Scenario: Write to an offered catalog resource is not allowed
- **WHEN** a client sends `PUT /api/v1/movies/123` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, and the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`

#### Scenario: Write to a description asset is not allowed
- **WHEN** a client sends `POST /api/v1/openapi/openapi.bundled.yaml` or `DELETE /api/v1/swagger-ui/index.html` with no credentials
- **THEN** the response status is `405`, the `Allow` header is `GET, HEAD`, the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`, and the asset is unchanged on a following `GET`
