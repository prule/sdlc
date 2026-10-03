## ADDED Requirements

### Requirement: Paged collection form
Every operation that returns a list a page at a time SHALL use the uniform success envelope with this shape. `data._embedded.<rel>` SHALL be the array of entries on the page (always present, `[]` when the page is empty), each entry carrying its own `_links.self`. `meta.pagination` SHALL hold `page` (the requested page, counted from `0`), `size` (the effective page size), `totalElements` (matches across all pages) and `totalPages` (`totalElements` divided by `size`, rounded up). `data._links` SHALL carry `self`, `first` (page `0`) and `last` (page `max(totalPages − 1, 0)`), plus `prev` when `1 ≤ page ≤ last + 1` (pointing to `page − 1`) and `next` when `page < last` (pointing to `page + 1`). Every link SHALL keep exactly the query parameters present on the request, other than `page`, with their values as sent, and SHALL NOT add parameters the client omitted (such as a defaulted `size` or order). `meta.pagination` SHALL be absent on responses that are not paged lists. A page after the last SHALL be a `200` with an empty entry array. Acceptance check: the first paged operation's web tests assert every member and link for the first, a middle, the last, an after-last and an empty-result page, and the interface-description contract test validates a paged body against the served schema.

#### Scenario: Middle page links
- **WHEN** a paged list has `totalPages` `3` and a client requests `page=1`
- **THEN** `data._links` has `self` (`page=1`), `first` (`page=0`), `prev` (`page=0`), `next` (`page=2`) and `last` (`page=2`)

#### Scenario: Non-paged responses carry no pagination
- **WHEN** a client sends `GET /api/v1/ping`
- **THEN** `meta` has no `pagination` member

### Requirement: Client faults name the offending request parameters
When a `400` `BAD_REQUEST` failure is caused by one or more named query or path parameters (a value of the wrong type, a missing required parameter, a value outside its declared bounds or allowed values, or a combination of values a capability does not allow), the problem body SHALL include `errors`: a non-empty array whose items each have `field` (the parameter name exactly as in the interface description) and `message` (a short human-readable reason). It SHALL list at least one offending parameter and SHALL NOT list a parameter whose value was acceptable. Neither `message` nor `detail` SHALL echo the supplied value or name an internal type. Failures of any other kind, and `400` failures not attributable to a named parameter, SHALL NOT carry `errors`. `errors` SHALL be described once in the shared `Problem` schema as an optional member. Acceptance check: the platform failure-kinds test asserts `errors[0].field` for the test-only operation's malformed and missing UUID parameter, asserts that the supplied value is absent from the body, and asserts that the `404`, `405`, `406`, `415` and `500` bodies have no `errors`.

#### Scenario: Malformed parameter is named
- **WHEN** a client calls the test-only operation with its required UUID query parameter set to `not-a-uuid`
- **THEN** the response status is `400`, `errors[0].field` is the parameter's name, and the body does not contain `not-a-uuid`

#### Scenario: Other failure kinds have no errors member
- **WHEN** a client sends `GET /api/v1/no-such-thing`
- **THEN** the problem body has no `errors` member

## MODIFIED Requirements

### Requirement: Service is read-only and public
No request SHALL change any data. The service SHALL NOT require or validate credentials on any endpoint. `POST`, `PUT`, `PATCH` and `DELETE` SHALL NOT succeed on any path. The refusals are:
- On `/ping`: `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET`.
- On an offered catalog resource path (`/movies/{id}`, whatever the value of `{id}`) or catalog collection path (`/movies`): `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET`.
- On an existing interface-description asset (`/api/v1/openapi/openapi.bundled.yaml`, `/api/v1/swagger-ui/index.html`): `405` `METHOD_NOT_ALLOWED`, with `Allow: GET, HEAD`.
- On a path the service does not offer: `404` `NOT_FOUND`. This is the same answer as for any other method on that path, because nothing exists there.

The refusal is always an `application/problem+json` response and never a `2xx`, `401`, `403` or `5xx`. Acceptance check: a parameterised web test over the four write methods on `/ping`, on `/movies/{id}`, on `/movies`, on both description assets and on an unknown path (`/no-such-thing/123`), with no CSRF token and no credentials. It asserts the exact status and, for `405`, the `Allow` header.

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
