## Purpose

Defines the public, read-only retrieval of a single movie's detail from the catalog by its stable
opaque identifier — the required and optional fields presented, and the distinct outcomes for a
malformed identifier versus a well-formed identifier that matches no movie.

## ADDED Requirements

### Requirement: Retrieve a movie's detail by its stable identifier

The system SHALL expose a public, read-only operation that, given a movie's stable opaque identifier,
returns that movie's detail. The operation SHALL NOT require authentication, an account, or any
credential (BR-1), and SHALL NOT change any catalog data (BR-2). The identifier SHALL be a stable,
opaque value that is unique within the catalog and never exposes internal storage details (BR-3);
it is represented as a UUID.

A successful response SHALL use the standard success Envelope (`data` + `meta`), with the movie
detail carried in `data` and a HAL `data._links.self` link addressing the requested movie. The
response media type SHALL be `application/json`.

Acceptance check: `GET /api/v1/movies/{id}` for an existing movie, sent with no `Authorization`
header, returns `200` with body root `{ data, meta }`, `data._links.self.href` an absolute URI
addressing that movie, and `meta.correlationId`/`meta.timestamp` present; the stored catalog is
unchanged.

#### Scenario: Existing movie is returned to an unauthenticated consumer
- **WHEN** a consumer requests `GET /api/v1/movies/{id}` for a movie that exists, without authenticating
- **THEN** the response is `200` with the standard Envelope
- **AND** `data` carries the movie's detail and `data._links.self` addresses the requested movie
- **AND** the catalog data is unchanged

### Requirement: Movie detail presents required fields and only present optional fields

Every movie's detail SHALL always include its required fields: the identifier, the title, the release
year, and its genres — one or more controlled-vocabulary categories, always at least one (BR-4). The
detail MAY include the optional fields runtime, synopsis, and aggregate rating; each optional field
SHALL be present only when the movie has recorded it and SHALL be omitted (not null, not empty) when
absent (BR-5). The aggregate rating, when present, SHALL be a score on a 0–5 star scale (BR-5).

Acceptance check: for a movie that has all optional fields, the `data` object contains `id`, `title`,
`releaseYear`, a non-empty `genres` array, plus `runtimeMinutes`, `synopsis`, and `rating` (0 ≤
`rating` ≤ 5); for a movie that has none of the optional fields, `data` contains the required fields
and a non-empty `genres` array, and the keys `runtimeMinutes`, `synopsis`, and `rating` are absent.

#### Scenario: Movie with all optional fields present
- **WHEN** a consumer retrieves a movie that has runtime, synopsis, and an aggregate rating
- **THEN** `data` includes `id`, `title`, `releaseYear`, and a `genres` array of one or more genres
- **AND** `data` includes `runtimeMinutes`, `synopsis`, and `rating` with `rating` between 0 and 5 inclusive

#### Scenario: Movie with optional fields absent is still a success
- **WHEN** a consumer retrieves an existing movie that has no runtime, synopsis, or rating recorded
- **THEN** the response is `200` with the required fields and a non-empty `genres` array
- **AND** the `runtimeMinutes`, `synopsis`, and `rating` keys are omitted from `data`

### Requirement: A well-formed identifier that matches no movie is Not Found

When the supplied identifier is a well-formed movie identifier but no movie in the catalog carries it,
the system SHALL respond `404 Not Found` with an `application/problem+json` body conforming to the
shared `Problem` schema, carrying a stable machine `code` of `MOVIE_NOT_FOUND` and a `correlationId`.
The response SHALL NOT be an empty `200`, and nothing in the catalog changes.

Acceptance check: `GET /api/v1/movies/{id}` with a syntactically valid UUID that no movie uses returns
`404`, `Content-Type: application/problem+json`, `code = MOVIE_NOT_FOUND`, and a `correlationId`.

#### Scenario: Unknown but well-formed identifier
- **WHEN** a consumer requests a movie with a well-formed identifier that matches no movie
- **THEN** the response is `404` with `application/problem+json`
- **AND** the body carries `code = MOVIE_NOT_FOUND` and a `correlationId`

### Requirement: A malformed identifier is a Bad Request, distinct from Not Found

When the supplied identifier is not a well-formed movie identifier at all, the system SHALL reject the
request as `400 Bad Request` with an `application/problem+json` body conforming to the shared `Problem`
schema, **before** attempting to locate any movie (flow 3a). This outcome SHALL be distinct from the
not-found outcome (a well-formed identifier matching no movie): the two SHALL carry different HTTP
statuses (`400` vs `404`). Nothing in the catalog changes.

Acceptance check: `GET /api/v1/movies/{value}` where `{value}` is not a well-formed identifier (e.g.
`not-a-uuid`) returns `400`, `Content-Type: application/problem+json`, and a `correlationId`; the same
call shape with a well-formed-but-unknown identifier returns `404` — confirming the two are distinct.

#### Scenario: Malformed identifier is rejected before lookup
- **WHEN** a consumer requests `GET /api/v1/movies/{value}` where `{value}` is not a well-formed identifier
- **THEN** the response is `400` with `application/problem+json` and a `correlationId`
- **AND** no movie lookup is attempted and the catalog is unchanged

#### Scenario: Malformed and not-found are distinct outcomes
- **WHEN** one request supplies a malformed identifier and another supplies a well-formed unknown identifier
- **THEN** the malformed request returns `400` and the unknown-but-well-formed request returns `404`
