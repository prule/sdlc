## MODIFIED Requirements

### Requirement: Uniform success envelope
Every `2xx` JSON response SHALL be labelled `Content-Type: application/json`, never `application/problem+json`, whatever the request's `Accept` header. It SHALL be an object with exactly two top-level members:
- `data` holds the payload.
- `meta` holds `timestamp` (an RFC 3339 date-time, when the answer was produced) and `correlationId`.

On a paged list, `meta` SHALL also hold `pagination`, as defined in "Paged lists follow one navigable convention". On any other response, `meta` SHALL NOT carry `pagination`.

Acceptance check: a JSON assertion on the availability response, using a fixed clock to assert `timestamp`, and asserting that `meta` has no `pagination` key.

#### Scenario: Success is never labelled as a problem
- **WHEN** a client sends `GET /api/v1/ping` with `Accept: application/problem+json`
- **THEN** the response status is `200`, the `Content-Type` is `application/json`, and the body is the success envelope

#### Scenario: Envelope metadata is populated
- **WHEN** a client sends `GET /api/v1/ping` while the service clock reads `2026-01-01T00:00:00Z`
- **THEN** the body has top-level keys `data` and `meta` only, `meta.timestamp` is `2026-01-01T00:00:00Z`, and `meta` has no `pagination` key

### Requirement: Service is read-only and public
No request SHALL change any data. The service SHALL NOT require or validate credentials on any endpoint. `POST`, `PUT`, `PATCH` and `DELETE` SHALL NOT succeed on any path.

The refusals are:

| Path | Refusal |
|---|---|
| `/ping` | `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET` |
| An offered catalog path: the movie collection `/movies`, or a movie resource `/movies/{id}` whatever the value of `{id}` | `405` `METHOD_NOT_ALLOWED`, with an `Allow` header that includes `GET` |
| An existing interface-description asset (`/api/v1/openapi/openapi.bundled.yaml`, `/api/v1/swagger-ui/index.html`) | `405` `METHOD_NOT_ALLOWED`, with `Allow: GET, HEAD` |
| A path the service does not offer | `404` `NOT_FOUND`, the same answer as for any other method on that path, because nothing exists there |

The refusal is always an `application/problem+json` response. It is never a `2xx`, `401`, `403` or `5xx`.

Acceptance check: a parameterised web test sends the four write methods, with no CSRF token and no credentials, to:
- `/ping`;
- `/movies`;
- `/movies/{id}`;
- both description assets;
- an unknown path (`/no-such-thing/123`).

It asserts the exact status and, for `405`, the `Allow` header.

#### Scenario: Write to an unknown path is not found
- **WHEN** a client sends `PUT /api/v1/no-such-thing/123` with a JSON body and no credentials
- **THEN** the response status is `404`, and the body is `application/problem+json` with `code` `NOT_FOUND`

#### Scenario: Write to an offered catalog resource is not allowed
- **WHEN** a client sends `PUT /api/v1/movies/123` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, and the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`

#### Scenario: Write to the movie collection is not allowed
- **WHEN** a client sends `POST /api/v1/movies` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, and the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`

#### Scenario: Write to a description asset is not allowed
- **WHEN** a client sends `POST /api/v1/openapi/openapi.bundled.yaml` or `DELETE /api/v1/swagger-ui/index.html` with no credentials
- **THEN** the response status is `405`, the `Allow` header is `GET, HEAD`, the body is `application/problem+json` with `code` `METHOD_NOT_ALLOWED`, and the asset is unchanged on a following `GET`

## ADDED Requirements

### Requirement: Paged lists follow one navigable convention
Every operation that returns a list a page at a time SHALL use this form. Movie search is the first such operation.

**Paging parameters.**
- `page` is a zero-based page index of at least `0`, and defaults to `0`.
- `size` is from `1` to `100`, and defaults to `20`.

**Response form.**
- `data._embedded.<rel>` SHALL be the array of entries, each carrying its own `_links.self`.
- `meta.pagination` SHALL hold `page`, `size`, `totalElements` and `totalPages`. Counts SHALL appear only in `meta.pagination`, and link URLs only in `data._links`.
- `data._links` SHALL hold `self`, `first` and `last`, plus `prev` and `next` where they exist.

**Link hrefs.** Every pagination link SHALL repeat the request's recognised filter, order and `size` parameters exactly as given, and SHALL differ from the request only in `page`. Parameters the request omitted SHALL stay omitted.

**Page after the last.** A page after the last SHALL be a `200` with an empty entry array.

**Refusal.** A `page` or `size` outside its bounds SHALL be `400` `BAD_REQUEST`.

Acceptance check: the movie search tests in `catalog/movies` demonstrate each rule. The served description defines `page`, `size` and `Pagination` once, as asserted by `platform/interface-description`.

#### Scenario: Paged list carries counts in meta and links in data
- **WHEN** a client requests the first page of a paged list that has more than one page
- **THEN** `meta.pagination` has `page`, `size`, `totalElements` and `totalPages`, and `data._links` has `self`, `first`, `next` and `last` but no `prev`

#### Scenario: Paging bounds are client faults
- **WHEN** a client requests a paged list with `size=0`, and separately with `page=-1`
- **THEN** both responses are `400` with `code` `BAD_REQUEST`

### Requirement: Refused parameter is named, never echoed
When a `400` `BAD_REQUEST` is caused by a single, named request parameter, the problem `detail` SHALL be exactly `The request parameter '<name>' is not valid.`. `<name>` is the parameter's name as published in the interface description.

This applies to any of these causes:
- an ill-typed value;
- a missing required value;
- a value outside its declared bounds;
- a value refused by a business rule.

**What the detail never contains.** The `detail` SHALL NOT contain the supplied value, an internal type name, or a validation-framework message.

**Bounds violations.** A request parameter that violates a constraint declared in the interface description SHALL be `400`, never `500`.

**Other 400s.** Any other `400` SHALL keep the generic detail `The request could not be understood.`.

**Server-side validation faults stay 500.** A validation failure that is not about a request parameter, for example an invariant check inside the service, is a server fault. It SHALL remain `500` `INTERNAL_ERROR` with the generic detail.

Acceptance check:
- `FailureKindsTest` asserts the named detail for the test-only UUID parameter, both malformed and omitted.
- A test-only operation with a bounded integer parameter asserts that an out-of-bounds value gets `400` and the named detail.
- A test in which a non-controller component raises a constraint violation asserts `500` `INTERNAL_ERROR` with the generic detail.
- The movie tests assert the named detail for `GET /api/v1/movies/not-a-movie-id` (`id`) and for each search refusal.

#### Scenario: Ill-typed parameter is named
- **WHEN** a client calls the test-only operation with its required UUID query parameter `id` set to `not-a-uuid`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` is `The request parameter 'id' is not valid.`, which does not contain `not-a-uuid`

#### Scenario: Out-of-bounds parameter is a client fault
- **WHEN** a client sends a value above a parameter's declared maximum
- **THEN** the response status is `400`, not `500`, and `detail` names that parameter

#### Scenario: Validation fault outside request handling is a server fault
- **WHEN** handling a request fails because a component other than the request handler rejects a value it validated
- **THEN** the response status is `500`, `code` is `INTERNAL_ERROR`, and `detail` is `An unexpected error occurred.`
