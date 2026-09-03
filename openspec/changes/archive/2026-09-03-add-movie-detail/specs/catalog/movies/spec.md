## Purpose

The `catalog/movies` capability serves the public, read-only detail of a single Movie — the core
catalog aggregate — retrieved by its stable opaque id, so API consumers can display accurate movie
data without running their own catalog.

## ADDED Requirements

### Requirement: Retrieve movie detail by id returns the enveloped resource

The system SHALL expose `GET /api/v1/movies/{id}` where `{id}` is a Movie's stable opaque UUID, and
for an existing Movie SHALL respond `200` with `Content-Type: application/json` as the standard
Envelope (`{data, meta}`). `data` SHALL expose the Movie's `id` (the opaque UUID, never an internal
DB id), `title`, release `year`, and `genres` (a non-empty array of genre labels). `data` SHALL
carry a HAL `_links` object whose `self.href` is the absolute URI of this operation. `meta` SHALL
carry `timestamp` and a `correlationId` (UUID). Internal DB ids SHALL NOT appear in the response.

Acceptance check: `GET /api/v1/movies/{existing-id}` with no `Authorization` header returns `200`,
`application/json`, a body with top-level `data` and `meta`; `data.id`/`data.title`/`data.year` and
a non-empty `data.genres`; `data._links.self.href` absolute and addressing the movie;
`meta.correlationId` a UUID and `meta.timestamp` present.

#### Scenario: Existing movie returns enveloped detail
- **WHEN** a client sends `GET /api/v1/movies/{id}` with a matching valid UUID and no auth header
- **THEN** the response is `200` with `Content-Type: application/json`
- **AND** the body root is the Envelope with `data` and `meta`
- **AND** `data` contains `id`, `title`, `year`, and a non-empty `genres` array of labels
- **AND** `data._links.self.href` is the absolute URI of this operation
- **AND** `meta` contains a UUID `correlationId` and a `timestamp`

### Requirement: Optional fields are present when set and omitted when absent

Runtime, synopsis, and the aggregate 0–5 star rating are OPTIONAL on a Movie. When set, the system
SHALL include `runtimeMinutes`, `synopsis`, and `rating` in `data`. The `rating` SHALL be an
aggregate score on a 0–5 star scale. When any optional field is absent, the system SHALL omit that
field entirely (per the platform non-null convention) — it SHALL NOT render it as `null`. A Movie
missing all optional fields is still valid and SHALL return `200`, never `404` or `500`. Required
fields (`id`, `title`, `year`, at least one genre) SHALL always be present.

Acceptance check: retrieve a movie with no runtime, synopsis, or rating; assert `200`, a well-formed
Envelope, that `runtimeMinutes`/`synopsis`/`rating` keys are absent (not `null`), and that `id`,
`title`, `year`, and a non-empty `genres` are present.

#### Scenario: Movie with all optional fields returns them
- **WHEN** a client retrieves a Movie that has a runtime, synopsis, and rating
- **THEN** `data` includes `runtimeMinutes`, `synopsis`, and a `rating` on a 0–5 star scale
- **AND** the response is `200`

#### Scenario: Movie missing optional fields still returns 200 with them omitted
- **WHEN** a client retrieves a Movie with no synopsis, no runtime, and no rating
- **THEN** the response is `200` with a well-formed Envelope
- **AND** `runtimeMinutes`, `synopsis`, and `rating` are absent from `data` (omitted, not `null`)
- **AND** `data` still contains `id`, `title`, `year`, and a non-empty `genres`

### Requirement: Movie detail carries only a self link and no embedded resources

The `data` resource SHALL carry a `_links` object containing only the `self` relation. Since no
`/people`, `/genres`, or `/ratings-reviews` endpoints exist yet, no related `_links` SHALL be
emitted, no `_embedded` object SHALL be present, and no HAL-FORMS `_templates` or write/action
affordance SHALL be emitted. Genres SHALL be exposed inline as plain labels, not as links. Credits
(cast/crew) and individual reviews SHALL NOT appear.

Acceptance check: assert `data._links` has exactly the `self` relation, `data` has no `_embedded`
and no `_templates`, and there is no cast/crew or reviews content.

#### Scenario: Only the self link is present
- **WHEN** a client retrieves a Movie successfully
- **THEN** `data._links` contains only `self`
- **AND** `data` has no `_embedded` and no `_templates`
- **AND** the response contains no cast, crew, credits, or reviews fields

### Requirement: Unknown id returns 404 problem+json

For a syntactically valid UUID with no matching Movie, the system SHALL respond `404` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying a
stable `code`, the `status`, and the request `correlationId`. The response SHALL NOT be an empty
`200` and SHALL NOT carry `_links` or `_embedded`.

Acceptance check: `GET /api/v1/movies/{random-unused-uuid}` returns `404`,
`application/problem+json`, a `Problem` body with a stable `code` and the `correlationId`, and no
`_links`/`_embedded`.

#### Scenario: No movie for a valid UUID is a 404
- **WHEN** a client requests a syntactically valid UUID with no matching Movie
- **THEN** the response is `404` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with a stable `code`, `status`, and the `correlationId`
- **AND** the response is not an empty `200` and contains no `_links` or `_embedded`

### Requirement: Malformed id is a 400, not a 500

For a path `{id}` that is not a valid UUID, the system SHALL respond `400` with
`Content-Type: application/problem+json` conforming to the shared `Problem` schema, carrying the
`correlationId`. Such input SHALL NOT fall through to a generic `500` and SHALL NOT reach the
persistence layer.

Acceptance check: `GET /api/v1/movies/not-a-uuid` returns `400` (not `500`),
`application/problem+json`, a `Problem` body with the `correlationId`.

#### Scenario: Non-UUID path id is rejected 400
- **WHEN** a client requests `GET /api/v1/movies/{id}` where `{id}` is not a valid UUID
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with the request `correlationId`
- **AND** the response is not `500`

### Requirement: The endpoint is public

The system SHALL serve `GET /api/v1/movies/{id}` with no authentication (`security: []` in the
contract). It SHALL NOT return `401` or `403` for a missing or absent `Authorization` header. No
secrets are required to call it and none are returned.

Acceptance check: request the endpoint with no `Authorization` header and assert the response is
`200` (existing movie) or `404` (unknown) — never `401`/`403`.

#### Scenario: No authentication is required
- **WHEN** a client requests the endpoint without an `Authorization` header
- **THEN** the response is never `401` or `403`
- **AND** an existing movie returns `200` and an unknown id returns `404`

### Requirement: Demo data is demonstrable while tests stay independent

A small demo dataset SHALL make the happy path demonstrable in a running app, delivered so it is NOT
loaded in production (scoped to a non-production profile). The automated tests SHALL use their own
fixtures against real Postgres (Testcontainers, no H2) and SHALL pass regardless of the demo seed's
contents. Test outcomes SHALL NOT depend on any row present in the demo seed.

Acceptance check: the demo seed loads only under the demo/dev profile (absent under prod); the
persistence and web tests insert their own fixture movies and assert against those ids, passing with
the demo seed empty or changed.

#### Scenario: Demo seed is not loaded in production
- **WHEN** the application runs under the production profile
- **THEN** the demo dataset is not loaded

#### Scenario: Tests are independent of the demo seed
- **WHEN** the automated test suite runs against a Postgres with no demo seed
- **THEN** the tests provision their own fixtures and pass
