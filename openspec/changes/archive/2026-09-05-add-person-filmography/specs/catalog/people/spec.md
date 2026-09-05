## ADDED Requirements

### Requirement: A person's filmography returns an enveloped HAL collection

The system SHALL expose `GET /api/v1/people/{id}/credits` where `{id}` is a Person's stable opaque UUID.
For an existing Person the system SHALL respond `200` with `Content-Type: application/json` as the
standard Envelope (`{data, meta}`). `data` SHALL be a HAL collection resource carrying the Person's
credited Movies under `data._embedded.filmography` and a `data._links` object carrying `self` and the
pagination links. `meta` SHALL carry `timestamp`, a `correlationId` (UUID), and `pagination`
(`page`, `size`, `totalElements`, `totalPages`). Internal DB ids SHALL NOT appear in the response.

Acceptance check: `GET /api/v1/people/{existing-id}/credits` with no `Authorization` header returns
`200`, `application/json`, a body with top-level `data` and `meta`; `data._embedded.filmography` is an
array; `data._links.self.href` is the absolute URI of this operation; `meta.correlationId` is a UUID,
`meta.timestamp` is present, and `meta.pagination` carries `page`/`size`/`totalElements`/`totalPages`.

#### Scenario: Existing person returns an enveloped filmography collection
- **WHEN** a client sends `GET /api/v1/people/{id}/credits` with a matching valid UUID and no auth header
- **THEN** the response is `200` with `Content-Type: application/json`
- **AND** the body root is the Envelope with `data` and `meta`
- **AND** the credited Movies appear under `data._embedded.filmography`
- **AND** `data._links.self.href` is the absolute URI of this operation
- **AND** `meta` contains a UUID `correlationId`, a `timestamp`, and `pagination`

### Requirement: Filmography is carried as a single embedded relation

The system SHALL carry the Person's credited Movies under a single embedded relation
`data._embedded.filmography` (not split into separate `cast`/`crew` relations). The `filmography`
relation SHALL always be present as an array on a `200` filmography response, empty when the page holds
no items.

Acceptance check: retrieve a Person's filmography; assert `data._embedded.filmography` is an array and
that no `cast` or `crew` embedded relation is present.

#### Scenario: Credited movies appear under a single filmography relation
- **WHEN** a client retrieves an existing Person's filmography
- **THEN** `data._embedded.filmography` is an array of filmography items
- **AND** the response carries no `data._embedded.cast` and no `data._embedded.crew` relation

### Requirement: Filmography item is a movie summary plus one typed capacity

Each item in `data._embedded.filmography` SHALL expose the credited Movie as a movie summary — `id`
(the Movie's opaque UUID), `title`, `releaseYear`, `genres`, and `runtimeMinutes`/`rating` when present
(omitted, not `null`, when absent) and no synopsis — carrying its own `_links.self` addressing
`GET /api/v1/movies/{id}` for that Movie, PLUS a typed `capacity` object for the one capacity this item
represents. An acting capacity SHALL carry `character` (free-text string) and `billingOrder` (a positive
integer); a non-acting capacity SHALL carry `department` (free-text string) and `job` (free-text string).
The `capacity` object SHALL carry a discriminator so a client can tell acting from non-acting, and SHALL
be fully populated for its kind (an acting capacity SHALL NOT be missing `character`/`billingOrder`; a
non-acting capacity SHALL NOT be missing `department`/`job`), reflecting the persistence CHECK
constraint. A Person credited on one Movie in several capacities SHALL produce several items — one per
capacity. The item SHALL NOT carry its own `self` link and SHALL NOT carry `_embedded` or `_templates`.
Internal DB ids SHALL NOT appear.

Acceptance check: for a Person with a mix of acting and non-acting credits, assert each item exposes a
movie summary (`id`, `title`, `releaseYear`, `genres`; `runtimeMinutes`/`rating` present only when set;
no synopsis) with `_links.self` addressing `GET /api/v1/movies/{movie.id}`; assert each item's `capacity`
is either an acting capacity carrying `character` + `billingOrder` or a non-acting capacity carrying
`department` + `job`, distinguishable by its discriminator and never missing a field of its kind; assert
a Person credited twice on one Movie (e.g. actor and director) yields two items for that Movie.

#### Scenario: Item exposes a movie summary and an acting capacity
- **WHEN** a client retrieves a filmography item for an acting credit
- **THEN** the item exposes the Movie as a movie summary with `_links.self` addressing `GET /api/v1/movies/{id}`
- **AND** the item's `capacity` carries `character` and a positive integer `billingOrder`
- **AND** the item carries no item `self` link, no `_embedded`, no `_templates`

#### Scenario: Item exposes a movie summary and a non-acting capacity
- **WHEN** a client retrieves a filmography item for a non-acting credit
- **THEN** the item exposes the Movie as a movie summary with `_links.self` addressing `GET /api/v1/movies/{id}`
- **AND** the item's `capacity` carries `department` and `job`

#### Scenario: One item per movie and capacity
- **WHEN** a Person is credited on one Movie in several capacities
- **THEN** the filmography contains one item per capacity for that Movie, each with its own typed `capacity`

### Requirement: Filmography is returned in a total, stable order

Filmography items SHALL be ordered by `releaseYear` descending, then `title` ascending, then a unique
terminal key (the credit or movie id) so the ordering is total and identical across repeated requests.
The ordering SHALL hold across page boundaries so that paging through the whole filmography yields every
item exactly once with no cross-page skip or duplicate.

Acceptance check: seed a Person whose filmography shares release years and titles, retrieve the
filmography twice, and assert items are ordered `releaseYear` desc then `title` asc with ties resolved by
the unique terminal key and the two responses are order-identical; page through the whole filmography and
assert the concatenated pages contain every item exactly once in the same total order.

#### Scenario: Filmography is ordered newest-first, total and stable
- **WHEN** a client retrieves the same Person's filmography twice
- **THEN** items are ordered by `releaseYear` descending, then `title` ascending, then a unique terminal key
- **AND** the two orderings are identical between the two responses

#### Scenario: Ordering holds across page boundaries
- **WHEN** a client pages through a Person's entire filmography
- **THEN** every item appears exactly once across the pages with no cross-page skip or duplicate

### Requirement: Filmography is paginated

The filmography SHALL be paginated using the platform `page` (zero-based, default `0`) and `size`
(default `20`, maximum `100`) parameters. `meta.pagination` SHALL carry `page`, `size`, `totalElements`,
and `totalPages`, and `data._links` SHALL carry pagination links following the platform HAL collection
convention: `self`, `first`, and `last` on every page; `next` when a later page exists; `prev` when an
earlier page exists. A valid page beyond the last SHALL return an empty `200` (no `next`), not `404` or
`400`. Invalid pagination input — `page` < 0, `size` < 1, or `size` > 100 — SHALL return `400`
`application/problem+json`, not `500`.

Acceptance check: request the first page and assert `self`/`first`/`last`/`next` present and no `prev`;
request the last page and assert no `next`; request a page beyond the last and assert an empty `200` with
no `next`; request `page=-1`, `size=0`, and `size=101` and assert each returns `400`
`application/problem+json`; assert `meta.pagination` counts are consistent with the requested page.

#### Scenario: First page carries forward pagination links
- **WHEN** a client requests the first page of a multi-page filmography
- **THEN** `data._links` carries `self`, `first`, `last`, and `next` and no `prev`
- **AND** `meta.pagination` carries `page`, `size`, `totalElements`, and `totalPages`

#### Scenario: Last page carries no next link
- **WHEN** a client requests the last page of a filmography
- **THEN** `data._links` carries `self`, `first`, `last`, and `prev` and no `next`

#### Scenario: A page beyond the last is an empty 200
- **WHEN** a client requests a valid page number beyond the last page
- **THEN** the response is `200` with an empty `data._embedded.filmography` and no `next` link

#### Scenario: Invalid pagination input is a 400
- **WHEN** a client requests the filmography with `page` < 0, `size` < 1, or `size` > 100
- **THEN** the response is `400` with `Content-Type: application/problem+json` and is not `500`

### Requirement: A person with no credits returns an empty filmography 200

For an existing, addressable Person credited in no Movie, the system SHALL respond `200` with the
standard Envelope, an empty `data._embedded.filmography` array, `data._links.self` present, and
`meta.pagination.totalElements` equal to `0`. It SHALL NOT respond `404` and SHALL NOT return an error.

Acceptance check: retrieve an existing Person with no credits; assert `200`, empty
`data._embedded.filmography`, `data._links.self` present, `meta.pagination.totalElements` is `0`, and the
response is not `404` or an error.

#### Scenario: Person with no credits returns an empty success
- **WHEN** a client retrieves the filmography of an existing Person who has no credits
- **THEN** the response is `200` with an empty `data._embedded.filmography` array
- **AND** `data._links.self` is present, `meta.pagination.totalElements` is `0`, and the response is not `404` or an error

### Requirement: Unknown person id for filmography returns 404 problem+json

For a syntactically valid UUID with no matching Person, the filmography endpoint SHALL respond `404` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying the existing
stable `code` `PERSON_NOT_FOUND`, the `status`, and the request `correlationId`. The response SHALL NOT
be an empty `200` and SHALL NOT carry `_links` or `_embedded`.

Acceptance check: `GET /api/v1/people/{random-unused-uuid}/credits` returns `404`,
`application/problem+json`, a `Problem` body with `code` `PERSON_NOT_FOUND` and the `correlationId`, and
no `_links`/`_embedded`.

#### Scenario: No person for a valid UUID is a 404
- **WHEN** a client requests the filmography for a syntactically valid UUID with no matching Person
- **THEN** the response is `404` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the stable `code` `PERSON_NOT_FOUND` and the `correlationId`
- **AND** the response is not an empty `200` and contains no `_links` or `_embedded`

### Requirement: Malformed person id for filmography is a 400, not a 500

For a filmography request whose path `{id}` is not a valid UUID, the system SHALL respond `400` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying the
`correlationId`. Such input SHALL NOT fall through to a generic `500` and SHALL NOT reach the persistence
layer.

Acceptance check: `GET /api/v1/people/not-a-uuid/credits` returns `400` (not `500`),
`application/problem+json`, a `Problem` body with the `correlationId`.

#### Scenario: Non-UUID path id is rejected 400
- **WHEN** a client requests `GET /api/v1/people/{id}/credits` where `{id}` is not a valid UUID
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the request `correlationId`
- **AND** the response is not `500` and does not reach the persistence layer

### Requirement: HAL discipline on filmography responses

The filmography response SHALL emit navigational `_links` only — no HAL-FORMS `_templates` and no
action/write affordances. Success responses SHALL be `application/json` with HAL fields inside `data`;
error responses SHALL be `application/problem+json` and SHALL NOT carry `_links` or `_embedded`. Each
embedded Movie SHALL carry a resolvable `_links.self` addressing `GET /api/v1/movies/{id}`; no other item
link SHALL be emitted. Link and collection assembly SHALL be web-adapter-only; the domain and application
layers SHALL NOT reference `_links`/`_embedded`.

Acceptance check: assert a `200` filmography response is `application/json` with HAL `_links` inside
`data` and no `_templates`; assert each embedded Movie carries `_links.self` addressing
`GET /api/v1/movies/{id}` and no other item link; assert an error response is `application/problem+json`
with no `_links`/`_embedded`.

#### Scenario: Success is HAL-in-envelope, errors are problem+json
- **WHEN** a client retrieves a filmography successfully
- **THEN** the `Content-Type` is `application/json` with HAL `_links` inside `data` and no `_templates`
- **AND** each embedded Movie carries `_links.self` addressing `GET /api/v1/movies/{id}`
- **AND** on an error the `Content-Type` is `application/problem+json` with no `_links`/`_embedded`

### Requirement: The filmography endpoint is public

The system SHALL serve `GET /api/v1/people/{id}/credits` with no authentication (`security: []` in the
contract). It SHALL NOT return `401` or `403` for a missing or absent `Authorization` header. The path
pattern SHALL be registered in the single source of truth for public endpoints so the public-endpoint
consistency test holds.

Acceptance check: request the endpoint with no `Authorization` header and assert the response is `200`
(existing person), `404` (unknown), or `400` (malformed) — never `401`/`403`; the public-endpoint
consistency test passes with the new pattern registered.

#### Scenario: No authentication is required
- **WHEN** a client requests the filmography endpoint without an `Authorization` header
- **THEN** the response is never `401` or `403`
- **AND** an existing person returns `200` and an unknown id returns `404`

### Requirement: Loading a filmography uses a bounded, credit-count-independent query count

Loading a Person's filmography (each credit joining to its Movie and that Movie's genres) SHALL issue a
number of SQL statements that is bounded and independent of the number of credits or movies — it SHALL
NOT issue one Movie query per credit row nor one genre query per Movie (no N+1). Increasing the size of
the filmography SHALL NOT increase the statement count. The CAT-001 detail, CAT-002 search, CAT-003
credits, and CAT-004 person-detail paths SHALL NOT regress.

Acceptance check: a Testcontainers test against real Postgres seeds a Person with many credits across
many movies and genres, counts the SQL statements issued to load one page of the filmography (e.g. via
Hibernate statistics or a query counter), and asserts the count is bounded and does not grow as the
filmography grows.

#### Scenario: Filmography load stays bounded as filmography grows
- **WHEN** the filmography adapter loads a page for a Person with many credits across many movies and genres
- **THEN** the number of SQL statements issued is bounded and independent of the filmography size
- **AND** it does not issue one Movie query per credit row nor one genre query per Movie

### Requirement: Filmography demo data is demonstrable while tests stay independent

The filmography happy path SHALL be demonstrable in a running app by reusing a Person from the existing
`@Profile("demo")` seed who is credited in one or more Movies (people and credits were seeded in CAT-003),
with that seed NOT loaded in production. The automated tests SHALL use their own fixtures against real
Postgres (Testcontainers, no H2) and SHALL pass regardless of the demo seed's contents.

Acceptance check: the demo seed (people + credits) loads only under the demo/dev profile (absent under
prod); the persistence and web tests insert their own fixture people, movies, and credits and assert
against those ids, passing with the demo seed empty or changed.

#### Scenario: Tests are independent of the demo seed
- **WHEN** the automated test suite runs against a Postgres with no demo seed
- **THEN** the filmography tests provision their own person, movie, and credit fixtures and pass

## MODIFIED Requirements

### Requirement: HAL discipline on person detail

`data._links` SHALL carry `self` and `credits` — the `credits.href` SHALL be the absolute URI of
`GET /api/v1/people/{id}/credits` for that Person (now that a cross-filmography endpoint exists). No
other related `_links` SHALL be emitted (no person-collection endpoint exists yet, so there are no
dangling links). The response SHALL emit navigational `_links` only — no HAL-FORMS `_templates` and no
action/write affordances. Success responses SHALL be `application/json` with HAL fields inside `data`;
error responses SHALL be `application/problem+json` and SHALL NOT carry `_links` or `_embedded`. Building
the `credits` link SHALL be web-adapter-only and SHALL NOT issue any additional SQL query for the
person-detail request.

Acceptance check: assert a `200` person response is `application/json`, `data._links` contains exactly
`self` and `credits`, `credits.href` is the absolute URI of `GET /api/v1/people/{id}/credits`, and
carries no `_templates`; assert an error response is `application/problem+json` with no
`_links`/`_embedded`; assert the person-detail SQL statement count is unchanged from CAT-004.

#### Scenario: Success is HAL-in-envelope with self only, errors are problem+json

- **WHEN** a client retrieves a person successfully
- **THEN** the `Content-Type` is `application/json` with HAL `_links` inside `data`
- **AND** `data._links` contains exactly `self` and `credits`, with `credits.href` the absolute URI of `GET /api/v1/people/{id}/credits`, and no `_templates`
- **AND** on an error the `Content-Type` is `application/problem+json` with no `_links`/`_embedded`
