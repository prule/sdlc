## Purpose
The `catalog/credits` capability serves the public, read-only cast and crew of a single Movie as an
unpaginated HAL collection sub-resource, so API consumers can display who acted in and made a movie
without running their own catalog.

## ADDED Requirements

### Requirement: A movie's credits return an enveloped HAL collection

The system SHALL expose `GET /api/v1/movies/{id}/credits` where `{id}` is a Movie's stable opaque
UUID. For an existing Movie the system SHALL respond `200` with `Content-Type: application/json` as
the standard Envelope (`{data, meta}`). `data` SHALL be a HAL collection resource carrying cast and
crew items under `data._embedded` (see the cast/crew item requirements) and a `data._links` object
whose `self.href` is the absolute URI of this operation. `meta` SHALL carry `timestamp` and a
`correlationId` (UUID). Because the full collection is returned whole, `meta.pagination` SHALL NOT be
present and no pagination links (`next`, `prev`, `first`, `last`) SHALL be emitted. Internal DB ids
SHALL NOT appear in the response.

Acceptance check: `GET /api/v1/movies/{existing-id}/credits` with no `Authorization` header returns
`200`, `application/json`, a body with top-level `data` and `meta`; `data._links` contains exactly
`self` (absolute URI of this operation); `meta.correlationId` is a UUID and `meta.timestamp` is
present; `meta.pagination` is absent.

#### Scenario: Existing movie returns an enveloped credits collection
- **WHEN** a client sends `GET /api/v1/movies/{id}/credits` with a matching valid UUID and no auth header
- **THEN** the response is `200` with `Content-Type: application/json`
- **AND** the body root is the Envelope with `data` and `meta`
- **AND** `data._links` contains only `self` with an absolute `href` addressing this operation
- **AND** `meta` contains a UUID `correlationId` and a `timestamp`, and no `pagination`

### Requirement: Cast and crew are carried as separate embedded relations

The system SHALL carry acting credits under `data._embedded.cast` and non-acting credits under
`data._embedded.crew`, each an array of HAL item resources. When a Movie has no acting credits the
`cast` array SHALL be empty; when it has no non-acting credits the `crew` array SHALL be empty. The
two relations SHALL always both be present as arrays on a `200` credits response.

Acceptance check: for a Movie with both cast and crew, assert `data._embedded.cast` and
`data._embedded.crew` are both arrays; cast entries appear only under `cast` and crew entries only
under `crew`.

#### Scenario: Cast and crew appear under distinct relations
- **WHEN** a client retrieves the credits of a Movie that has both acting and non-acting credits
- **THEN** `data._embedded.cast` is an array of acting credits
- **AND** `data._embedded.crew` is an array of non-acting credits

### Requirement: Cast item exposes person, character, and billing order

Each item in `data._embedded.cast` SHALL be a HAL resource exposing its Person inline as `person`
(with `id` — the Person's opaque UUID — and `name`), the `character` name (a free-text string), and
`billingOrder` (a positive integer). The cast item SHALL NOT carry a person `self` link (no
`/people/{id}` endpoint exists), SHALL NOT carry its own `self` link, and SHALL NOT carry `_embedded`
or `_templates`. Internal DB ids SHALL NOT appear.

Acceptance check: retrieve a Movie with cast; assert each cast item exposes `person.id` (UUID),
`person.name`, `character`, and a positive integer `billingOrder`, and carries no `_links`,
`_embedded`, or `_templates`. Every cast item SHALL always carry both `character` and `billingOrder`
(the per-type invariant is observable — no cast item is missing either), reflecting the persistence
CHECK constraint.

#### Scenario: Cast item shape
- **WHEN** a client retrieves a Movie's cast
- **THEN** each cast item exposes `person` (`id` + `name`), `character`, and `billingOrder`
- **AND** the item carries no person `self` link, no item `self` link, no `_embedded`, no `_templates`

### Requirement: Crew item exposes person, department, and job

Each item in `data._embedded.crew` SHALL be a HAL resource exposing its Person inline as `person`
(with `id` and `name`), the `department` (a free-text string), and the `job` (a free-text string).
The crew item SHALL NOT carry a person `self` link, SHALL NOT carry its own `self` link, and SHALL
NOT carry `_embedded` or `_templates`. Internal DB ids SHALL NOT appear.

Acceptance check: retrieve a Movie with crew; assert each crew item exposes `person.id` (UUID),
`person.name`, `department`, and `job`, and carries no `_links`, `_embedded`, or `_templates`. Every
crew item SHALL always carry both `department` and `job` (the per-type invariant is observable — no
crew item is missing either), reflecting the persistence CHECK constraint.

#### Scenario: Crew item shape
- **WHEN** a client retrieves a Movie's crew
- **THEN** each crew item exposes `person` (`id` + `name`), `department`, and `job`
- **AND** the item carries no person `self` link, no item `self` link, no `_embedded`, no `_templates`

### Requirement: Credits are returned in a total, stable order

Cast SHALL be ordered by `billingOrder` ascending (lowest number = top billing, lead first). Crew
SHALL be grouped by `department` ascending, then `job` ascending. Crew `department`/`job` grouping
SHOULD be case-insensitive so consumers see like-departments grouped regardless of input casing; the
ordering is total and stable either way because it ends in a deterministic terminal tiebreak — person
`name` ascending, then a unique key (the credit's id) — so the ordering is identical across repeated
requests.

Acceptance check: seed a Movie whose cast shares billing orders and whose crew shares
department/job, retrieve its credits twice, and assert cast is billing-order ascending, crew is
department-then-job ascending, ties resolve by person name then credit id, and the two responses are
byte-for-byte order-identical.

#### Scenario: Cast is ordered by billing order ascending
- **WHEN** a client retrieves a Movie's cast
- **THEN** cast items are ordered by `billingOrder` ascending
- **AND** items with equal `billingOrder` are ordered by person `name` then credit id

#### Scenario: Crew is grouped by department then job
- **WHEN** a client retrieves a Movie's crew
- **THEN** crew items are ordered by `department` then `job` ascending
- **AND** items with equal `department` and `job` are ordered by person `name` then credit id

#### Scenario: Ordering is stable across requests
- **WHEN** a client retrieves the same Movie's credits twice
- **THEN** the cast and crew orderings are identical between the two responses

### Requirement: A movie with no credits is a normal 200

For an existing Movie with no cast and no crew recorded, the system SHALL respond `200` with the
standard Envelope, empty `data._embedded.cast` and `data._embedded.crew` arrays, and `data._links.self`
present. It SHALL NOT respond `404` and SHALL NOT return an error. This aligns with the platform's
existing empty-collection convention.

Acceptance check: retrieve an existing Movie with no credits; assert `200`, empty
`data._embedded.cast` and `data._embedded.crew`, `data._links.self` present, and no error.

#### Scenario: Movie with no credits returns an empty success
- **WHEN** a client retrieves the credits of an existing Movie that has no cast or crew
- **THEN** the response is `200` with empty `data._embedded.cast` and `data._embedded.crew` arrays
- **AND** `data._links.self` is present and the response is not `404` or an error

### Requirement: Unknown movie id returns 404 problem+json

For a syntactically valid UUID with no matching Movie, the system SHALL respond `404` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying a stable
`code` (the existing `MOVIE_NOT_FOUND`), the `status`, and the request `correlationId`. The response
SHALL NOT be an empty `200` and SHALL NOT carry `_links` or `_embedded`.

Acceptance check: `GET /api/v1/movies/{random-unused-uuid}/credits` returns `404`,
`application/problem+json`, a `Problem` body with `code` `MOVIE_NOT_FOUND` and the `correlationId`,
and no `_links`/`_embedded`.

#### Scenario: No movie for a valid UUID is a 404
- **WHEN** a client requests credits for a syntactically valid UUID with no matching Movie
- **THEN** the response is `404` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the stable `code` `MOVIE_NOT_FOUND` and the `correlationId`
- **AND** the response is not an empty `200` and contains no `_links` or `_embedded`

### Requirement: Malformed movie id is a 400, not a 500

For a path `{id}` that is not a valid UUID, the system SHALL respond `400` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying the
`correlationId`. Such input SHALL NOT fall through to a generic `500` and SHALL NOT reach the
persistence layer.

Acceptance check: `GET /api/v1/movies/not-a-uuid/credits` returns `400` (not `500`),
`application/problem+json`, a `Problem` body with the `correlationId`.

#### Scenario: Non-UUID path id is rejected 400
- **WHEN** a client requests `GET /api/v1/movies/{id}/credits` where `{id}` is not a valid UUID
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the request `correlationId`
- **AND** the response is not `500`

### Requirement: HAL discipline on credits responses

The credits response SHALL emit navigational `_links` only — no HAL-FORMS `_templates` and no
action/write affordances. Success responses SHALL be `application/json` with HAL fields inside `data`;
error responses SHALL be `application/problem+json` and SHALL NOT carry `_links` or `_embedded`. Since
no `/people/{id}` endpoint exists, no person `self` link SHALL be emitted on any credit item.

Acceptance check: assert a `200` credits response is `application/json` with no `_templates` and no
person links; assert an error response is `application/problem+json` with no `_links`/`_embedded`.

#### Scenario: Success is HAL-in-envelope, errors are problem+json
- **WHEN** a client retrieves credits successfully
- **THEN** the `Content-Type` is `application/json` with HAL `_links` inside `data` and no `_templates`
- **AND** on an error the `Content-Type` is `application/problem+json` with no `_links`/`_embedded`

### Requirement: The credits endpoint is public

The system SHALL serve `GET /api/v1/movies/{id}/credits` with no authentication (`security: []` in the
contract). It SHALL NOT return `401` or `403` for a missing or absent `Authorization` header. The path
pattern SHALL be registered in the single source of truth for public endpoints so the public-endpoint
consistency test holds.

Acceptance check: request the endpoint with no `Authorization` header and assert the response is `200`
(existing movie), `404` (unknown), or `400` (malformed) — never `401`/`403`; the public-endpoint
consistency test passes with the new pattern registered.

#### Scenario: No authentication is required
- **WHEN** a client requests the credits endpoint without an `Authorization` header
- **THEN** the response is never `401` or `403`
- **AND** an existing movie returns `200` and an unknown id returns `404`

### Requirement: Loading credits uses a bounded, credit-count-independent query count

Loading a Movie's credits (each credit joining to a Person) SHALL issue a number of SQL statements
that is bounded and independent of the number of credits — it SHALL NOT issue one Person query per
credit row (no N+1). Increasing the number of credits SHALL NOT increase the statement count. Adding
the movie-detail `credits` `_link` SHALL NOT add any SQL statement to `GET /api/v1/movies/{id}`, and
the CAT-001 detail path and CAT-002 search path SHALL NOT regress.

Acceptance check: a Testcontainers test against real Postgres seeds a Movie with many credits, counts
the SQL statements issued to load its credits (e.g. via Hibernate statistics or a query counter), and
asserts the count is bounded and does not grow as credit count increases; a separate assertion
confirms `GET /api/v1/movies/{id}` issues no additional query after this change.

#### Scenario: Credits load stays bounded as credit count grows
- **WHEN** the credits adapter loads a Movie with many credits each joining to a Person
- **THEN** the number of SQL statements issued is bounded and independent of the credit count
- **AND** it does not issue one Person query per credit row

#### Scenario: Movie-detail credits link adds no query
- **WHEN** `GET /api/v1/movies/{id}` is served after this change
- **THEN** its SQL statement count is unchanged from CAT-001

### Requirement: Demo data is demonstrable while tests stay independent

The `@Profile("demo")` seed SHALL gain people and credits so the credits happy path is demonstrable in
a running app, delivered so it is NOT loaded in production. The automated tests SHALL use their own
fixtures against real Postgres (Testcontainers, no H2) and SHALL pass regardless of the demo seed's
contents.

Acceptance check: the demo seed loads people + credits only under the demo/dev profile (absent under
prod); the persistence and web tests insert their own fixture people and credits and assert against
those ids, passing with the demo seed empty or changed.

#### Scenario: Demo seed is not loaded in production
- **WHEN** the application runs under the production profile
- **THEN** the people and credits demo data are not loaded

#### Scenario: Tests are independent of the demo seed
- **WHEN** the automated test suite runs against a Postgres with no demo seed
- **THEN** the credits tests provision their own people and credits fixtures and pass
