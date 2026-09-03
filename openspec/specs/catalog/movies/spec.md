# catalog/movies Specification

## Purpose
The `catalog/movies` capability serves the public, read-only detail of a single Movie — the core
catalog aggregate — retrieved by its stable opaque id, so API consumers can display accurate movie
data without running their own catalog.

## Requirements

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

### Requirement: Search returns a paginated collection of movie summaries

The system SHALL expose `GET /api/v1/movies` as a public, read-only collection endpoint returning a
paginated set of movie **summary** resources. For a request with no query parameters and no
`Authorization` header, the system SHALL respond `200` with `Content-Type: application/json` as the
standard Envelope (`{data, meta}`). `data._embedded.movies` SHALL be an array of summary items (empty
when there are no matches), `data._links` SHALL carry the collection navigation links, and
`meta.pagination` SHALL carry `page`, `size` (default 20), `totalElements`, and `totalPages`. Results
SHALL be ordered by the default sort (`releaseYear` descending, then `title` ascending). Internal DB
ids SHALL NOT appear.

Acceptance check: `GET /api/v1/movies` with no params and no auth returns `200`, `application/json`,
a body with `data._embedded.movies` (an array), `data._links.self`, and `meta.pagination` with
`page`, `size=20`, `totalElements`, `totalPages`; items are ordered newest-release-year first with
title-ascending tiebreak.

#### Scenario: Default search returns the first page
- **WHEN** a client sends `GET /api/v1/movies` with no query params and no auth header
- **THEN** the response is `200` with `Content-Type: application/json`
- **AND** the body root is the Envelope with `data` and `meta`
- **AND** `data._embedded.movies` is an array and `data._links.self` is present
- **AND** `meta.pagination` has `page`, `size` (=20), `totalElements`, and `totalPages`
- **AND** items are ordered by `releaseYear` descending, then `title` ascending

### Requirement: Each result item is a movie summary with its own self link

Each item in `data._embedded.movies` SHALL be a HAL resource exposing `id`, `title`, `releaseYear`,
`genres` (a non-empty array of inline labels), and a `_links.self.href` that is the absolute URI of
`GET /api/v1/movies/{id}` for that movie. `runtimeMinutes` and `rating` (an aggregate 0–5 score) SHALL
be included when present and omitted entirely (not rendered as `null`) when absent. The summary SHALL
NOT include `synopsis`, credits (cast/crew), or reviews, and SHALL NOT carry `_embedded` or
`_templates`.

Acceptance check: retrieve a page containing a movie with all optional fields and one without; assert
each item exposes `id`, `title`, `releaseYear`, non-empty `genres`, and `_links.self.href` absolute to
its detail route; that `runtimeMinutes`/`rating` are present only when set (absent, not `null`,
otherwise); and that no `synopsis`/credits/reviews and no `_embedded`/`_templates` appear on items.

#### Scenario: Summary item exposes its fields and self link
- **WHEN** a client retrieves a page of movies
- **THEN** each `data._embedded.movies` item has `id`, `title`, `releaseYear`, and a non-empty `genres`
- **AND** each item's `_links.self.href` is the absolute URI of its `GET /api/v1/movies/{id}`
- **AND** `runtimeMinutes` and `rating` appear only when set (omitted, not `null`, when absent)
- **AND** no item contains `synopsis`, credits, reviews, `_embedded`, or `_templates`

### Requirement: Title filter matches case-insensitive substring

When a `title` query parameter is supplied, the system SHALL return only movies whose title contains
that value as a case-insensitive substring, and `meta.pagination` counts SHALL reflect the matched
subset.

Acceptance check: with a fixture containing "The Matrix", `GET /api/v1/movies?title=matrix` returns it
(and other substring matches), excludes non-matches, and reports counts for the matched subset only.

#### Scenario: Title substring narrows results case-insensitively
- **WHEN** a client requests `GET /api/v1/movies?title=matrix`
- **THEN** only movies whose title contains "matrix" case-insensitively are returned
- **AND** `meta.pagination.totalElements` reflects only the matched subset

### Requirement: Genre filter is repeatable and requires all supplied genres

The `genre` query parameter SHALL be repeatable. When one or more `genre` values are supplied, the
system SHALL return only movies that carry **all** of the supplied genres (AND semantics). A movie
carrying the supplied genres **plus additional genres** SHALL still match; a movie carrying only a
**subset** of the supplied genres SHALL NOT match. An unknown or misspelled `genre` value SHALL NOT be
an error — it simply matches no movie, narrowing (or emptying) the result set, and the response stays
`200`. No genre-vocabulary validation is performed.

Acceptance check: with fixtures including a movie tagged `[Drama, Crime, Thriller]` (extra genre) and
a movie tagged only `[Drama]` (subset), `GET /api/v1/movies?genre=Drama&genre=Crime` returns the
`[Drama, Crime, Thriller]` movie and excludes the `[Drama]`-only movie;
`GET /api/v1/movies?genre=Nonexistent` returns `200` with an empty result.

#### Scenario: Multiple genres are combined with AND
- **WHEN** a client requests `GET /api/v1/movies?genre=Drama&genre=Crime`
- **THEN** only movies carrying both `Drama` and `Crime` are returned
- **AND** a movie carrying only one of the two genres is excluded

#### Scenario: Extra genres still match, subset does not
- **WHEN** a client requests `GET /api/v1/movies?genre=Drama&genre=Crime`
- **THEN** a movie tagged `[Drama, Crime, Thriller]` (carrying the requested genres plus extras) is
  returned
- **AND** a movie tagged only `[Drama]` (a subset of the requested genres) is excluded

#### Scenario: Unknown genre value is not an error
- **WHEN** a client supplies a `genre` value that no movie carries
- **THEN** the response is `200` with a narrowed (possibly empty) result set, not an error

### Requirement: Release-year range filter is inclusive with independent bounds

The system SHALL accept optional `yearFrom` and `yearTo` query parameters forming an inclusive release
-year range. Either bound MAY be supplied alone. When both are supplied, only movies with `yearFrom <=
releaseYear <= yearTo` SHALL be returned.

Acceptance check: `GET /api/v1/movies?yearFrom=1990&yearTo=1999` returns only movies with
`1990 <= releaseYear <= 1999`; `yearFrom=1990` alone returns `releaseYear >= 1990`; `yearTo=1999` alone
returns `releaseYear <= 1999`.

#### Scenario: Inclusive year range filters results
- **WHEN** a client requests `GET /api/v1/movies?yearFrom=1990&yearTo=1999`
- **THEN** only movies with `1990 <= releaseYear <= 1999` are returned

#### Scenario: Each year bound works on its own
- **WHEN** a client supplies only `yearFrom` or only `yearTo`
- **THEN** results are bounded inclusively by that single bound

### Requirement: Minimum-rating filter is inclusive and excludes unrated movies

When `minRating` is supplied, the system SHALL return only movies whose aggregate rating is greater
than or equal to `minRating`. Movies that have no rating SHALL be excluded whenever `minRating` is
supplied.

Acceptance check: with fixtures spanning rated and unrated movies, `GET /api/v1/movies?minRating=4`
returns only movies with `rating >= 4` and excludes every unrated movie.

#### Scenario: minRating keeps only sufficiently rated movies
- **WHEN** a client requests `GET /api/v1/movies?minRating=4`
- **THEN** only movies with `rating >= 4` are returned
- **AND** movies with no rating are excluded

### Requirement: Filters compose with AND

When multiple filter types (`title`, `genre`, year range, `minRating`) are supplied together, the
system SHALL return only movies satisfying **all** of them, and `meta.pagination` counts SHALL reflect
the combined result.

Acceptance check: a request combining `title`, one or more `genre` values, a year range, and
`minRating` returns only movies meeting every constraint, with counts for that combined subset.

#### Scenario: Combined filters intersect
- **WHEN** a client supplies `title`, `genre`, `yearFrom`/`yearTo`, and `minRating` together
- **THEN** only movies satisfying all constraints are returned
- **AND** `meta.pagination` reflects the combined subset

### Requirement: Results are sortable with a validated sort field

The system SHALL accept a `sort` query parameter of the form `<field>,<dir>` where `field` is one of
`title`, `releaseYear`, or `rating` and `dir` is `asc` or `desc`. The default when `sort` is absent
SHALL be `releaseYear` descending with `title` ascending as a deterministic tiebreak. An unsupported
sort field SHALL be rejected `400` `application/problem+json` — it SHALL NOT be silently ignored and
SHALL NOT return `500`. When sorting by `rating`, movies with **no** rating SHALL sort **after** all
rated movies in **both** directions (nulls last), with the `title` ascending tiebreak — an unrated
movie SHALL NOT appear at the top of a `sort=rating,desc` result.

Acceptance check: `?sort=title,asc` orders results by title ascending; `?sort=rating,desc` orders by
rating descending with unrated movies last (not first); `?sort=rating,asc` also places unrated movies
last; `?sort=bogus,asc` returns `400` problem+json (not `200`, not `500`).

#### Scenario: Valid sort orders results
- **WHEN** a client requests `GET /api/v1/movies?sort=title,asc`
- **THEN** results are ordered by `title` ascending
- **AND** `releaseYear` and `rating` (asc/desc) are likewise honoured

#### Scenario: Rating sort places unrated movies last in both directions
- **WHEN** a client requests `GET /api/v1/movies?sort=rating,desc` against a mix of rated and unrated
  movies
- **THEN** rated movies appear ordered by rating descending, followed by unrated movies last
- **AND** `sort=rating,asc` likewise places unrated movies after all rated movies

#### Scenario: Unknown sort field is rejected
- **WHEN** a client requests a `sort` whose field is not `title`, `releaseYear`, or `rating`
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the response is not `200` and not `500`

### Requirement: Empty result set is a normal 200

A query matching no movie, or a query against an empty catalog, SHALL return `200` with an empty
`data._embedded.movies` array — never a `404` or error. On an empty collection the system SHALL emit
`meta.pagination.totalElements: 0` and `totalPages: 0`, `data._links.self`, and `data._links.first`
and `data._links.last` (both addressing page `0`), and SHALL NOT emit `next` or `prev`. This aligns
with the platform's existing HAL collection convention.

Acceptance check: issue a query with no matches; assert `200`, `data._embedded.movies` empty,
`meta.pagination.totalElements=0` and `totalPages=0`, `data._links.self`/`first`/`last` present, and
no `next`/`prev`.

#### Scenario: Zero matches is an empty success
- **WHEN** a client issues a query that matches no movie
- **THEN** the response is `200` with an empty `data._embedded.movies` array
- **AND** `meta.pagination.totalElements` is `0` and `totalPages` is `0`
- **AND** `data._links` has `self`, `first`, and `last` but no `next` or `prev`

### Requirement: Pagination boundary links follow HAL rules

The system SHALL page results using a zero-based `page` and a `size` (default 20, maximum 100),
reusing the shared `page`/`size` parameters. `data._links` SHALL carry `self`, `first`, and `last`
always, `prev` only when the current page is not the first, and `next` only when the current page is
not the last. Pagination navigation links SHALL preserve the active filter and sort query parameters.
A syntactically valid `page` index beyond the last page SHALL return an empty `200` (with no `next`),
not a `400` or `404`.

Acceptance check: on a multi-page result, the first page has `self`/`first`/`last`/`next` and no
`prev`; a middle page has all five; the last page has `self`/`first`/`last`/`prev` and no `next`; a
page index beyond the last returns an empty `200` with no `next`; each navigation link carries the
same filters/sort as the request.

#### Scenario: First page omits prev
- **WHEN** a client requests the first page of a multi-page result
- **THEN** `data._links` has `self`, `first`, `last`, and `next` but no `prev`

#### Scenario: Last page omits next
- **WHEN** a client requests the last page of a multi-page result
- **THEN** `data._links` has `self`, `first`, `last`, and `prev` but no `next`

#### Scenario: Navigation links preserve filters and sort
- **WHEN** a client requests a filtered/sorted page
- **THEN** each emitted `next`/`prev`/`first`/`last`/`self` link carries the same filter and `sort`
  query parameters as the request

#### Scenario: Page beyond the last is an empty success
- **WHEN** a client requests a valid `page` index beyond the last page
- **THEN** the response is `200` with an empty `data._embedded.movies` array and no `next`

### Requirement: Invalid pagination or sort parameters are rejected with 400, not 500

The system SHALL validate query parameters at the boundary. Out-of-range `page`/`size` (`page < 0`,
`size < 1`, `size > 100`), a non-integer `page`/`size`/`yearFrom`/`yearTo`, a non-numeric
`minRating`, and an unsupported `sort` field SHALL be rejected `400` `application/problem+json`
conforming to the shared `Problem` schema, carrying a stable `code` and the request `correlationId`,
with no `_links`/`_embedded`. Such input SHALL NOT fall through to a generic `500`. The controller
SHALL be `@Validated` so the `page`/`size` bounds are enforced.

Acceptance check: `?page=-1`, `?size=0`, `?size=101`, `?size=abc`, and `?sort=bogus,asc` each return
`400` (not `500`), `application/problem+json`, a `Problem` body with a stable `code` and the
`correlationId`, and no `_links`/`_embedded`.

#### Scenario: Out-of-range size is rejected
- **WHEN** a client requests `GET /api/v1/movies?size=0` (or `size=101`, or `page=-1`)
- **THEN** the response is `400` with `Content-Type: application/problem+json`
- **AND** the body conforms to `Problem` with a stable `code` and the `correlationId`
- **AND** the response is not `500` and contains no `_links`/`_embedded`

#### Scenario: Non-numeric parameter is rejected
- **WHEN** a client supplies a non-integer `page`/`size`/`yearFrom`/`yearTo` or a non-numeric
  `minRating`
- **THEN** the response is `400` `application/problem+json`, not `500`

### Requirement: The search endpoint is public

The system SHALL serve `GET /api/v1/movies` with no authentication (`security: []`). It SHALL NOT
return `401` or `403` for a missing or absent `Authorization` header.

Acceptance check: request the endpoint with no `Authorization` header and assert the response is
`200` (never `401`/`403`).

#### Scenario: No authentication is required
- **WHEN** a client requests `GET /api/v1/movies` without an `Authorization` header
- **THEN** the response is never `401` or `403`
- **AND** a valid request returns `200`

### Requirement: A page of movies loads genres with a bounded, N-independent query count

Loading a page of N movies (each carrying genres) SHALL issue a number of SQL statements that is
bounded and independent of N — it SHALL NOT issue one genre query per row (no N+1). Increasing the
page size or the number of genres per movie SHALL NOT increase the statement count. The CAT-001
single-movie detail path SHALL NOT regress.

Acceptance check: a Testcontainers test against real Postgres seeds several multi-genre movies, counts
the SQL statements issued to load a multi-row page (e.g. via Hibernate statistics or a query counter),
and asserts the count is bounded and does not grow when the page size or genres-per-movie is
increased; a separate assertion confirms `GET /api/v1/movies/{id}` still issues its prior bounded
query count.

#### Scenario: Page load stays bounded as N grows
- **WHEN** the search adapter loads a page of movies each with multiple genres
- **THEN** the number of SQL statements issued is bounded and independent of the page size
- **AND** it does not issue one genre query per row

#### Scenario: Single-movie detail path does not regress
- **WHEN** `GET /api/v1/movies/{id}` is served after this change
- **THEN** its query behaviour is unchanged from CAT-001
