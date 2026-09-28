## MODIFIED Requirements

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
