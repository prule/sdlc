## Purpose

The `catalog/movies` capability lets a public API consumer retrieve one movie's detail by its stable, opaque identifier, so consumers can present accurate movie information sourced from the curated catalog.

## ADDED Requirements

### Requirement: Retrieve a movie's detail by identifier

The system SHALL provide a read-only operation that, given a well-formed stable movie identifier that matches a movie in the catalog, returns that movie's detail. The detail SHALL always include the movie's identifier, title, release year, and one or more genres. The response SHALL use the standard success Envelope (`data` + `meta`), with the movie detail in `data` carrying a HAL `self` link, and SHALL NOT mutate any catalog data.

Acceptance check: issue the retrieve operation for an existing movie whose identifier is `M`; assert HTTP `200 application/json`, `data.id == M`, `data.title`, `data.releaseYear`, and `data.genres` (a non-empty array) are present, `data._links.self` resolves to the same movie, and `meta.correlationId`/`meta.timestamp` are present.

#### Scenario: Existing movie is returned with all required detail
- **WHEN** a consumer requests the detail of a movie whose identifier matches a catalog movie
- **THEN** the system responds `200` with the standard Envelope
- **AND** `data` contains the identifier, title, release year, and a non-empty list of genres
- **AND** `data._links.self` points at that same movie
- **AND** the catalog is unchanged

#### Scenario: Retrieval requires no authentication
- **WHEN** a consumer requests a movie's detail without presenting any credential
- **THEN** the system serves the request (it is not rejected as unauthenticated)

### Requirement: Optional detail fields are present only when recorded

The system SHALL include a movie's runtime, synopsis, and aggregate rating in the detail only when the movie has them recorded. An absent optional field SHALL be omitted from the response entirely (not rendered as null or empty), and its absence SHALL NOT be treated as an error. The aggregate rating, when present, SHALL be a value on a 0–5 star scale.

Acceptance check: retrieve a movie that has all optional fields and assert `data.runtimeMinutes`, `data.synopsis`, and `data.rating` are present with `0 <= rating <= 5`; retrieve a movie that has none of them and assert the response is `200` and those keys are absent from `data`.

#### Scenario: Movie with all optional fields present
- **WHEN** a consumer retrieves a movie that has a runtime, synopsis, and rating recorded
- **THEN** the response `200` includes runtime, synopsis, and a rating between 0 and 5 inclusive

#### Scenario: Movie with optional fields absent is still a success
- **WHEN** a consumer retrieves a movie that has no runtime, synopsis, or rating recorded
- **THEN** the response is `200` with the required detail present
- **AND** the runtime, synopsis, and rating fields are omitted from `data`

### Requirement: Malformed identifier is rejected as a bad request before lookup

The system SHALL reject a request whose supplied identifier is not a well-formed movie identifier with a `400` `application/problem+json` response, WITHOUT attempting to locate any movie. This outcome SHALL be distinct from the not-found outcome and SHALL leave the catalog unchanged.

Acceptance check: issue the retrieve operation with an identifier that is not a well-formed movie identifier; assert HTTP `400 application/problem+json`, a stable machine `code`, and a `correlationId`; assert no catalog lookup occurred.

#### Scenario: Not a well-formed identifier
- **WHEN** a consumer requests a movie using a value that is not a well-formed movie identifier
- **THEN** the system responds `400` with a problem+json body
- **AND** it does not attempt to locate any movie
- **AND** the outcome is reported distinctly from not-found

### Requirement: Well-formed identifier matching no movie is not found

The system SHALL respond `404` `application/problem+json` when a well-formed identifier matches no movie in the catalog. This SHALL be reported as a distinct outcome from a malformed request and SHALL leave the catalog unchanged.

Acceptance check: issue the retrieve operation with a well-formed identifier that matches no movie; assert HTTP `404 application/problem+json`, a stable machine `code`, and a `correlationId`.

#### Scenario: No movie carries the identifier
- **WHEN** a consumer requests a movie using a well-formed identifier that matches no catalog movie
- **THEN** the system responds `404` with a problem+json body
- **AND** the outcome is reported distinctly from a malformed request

### Requirement: Movie identifiers are stable and opaque

Each movie SHALL be addressed by a stable identifier that is unique within the catalog and that does not expose internal storage details (such as database sequence numbers). The identifier presented in a movie's detail SHALL be the same value used to address it.

Acceptance check: retrieve a movie and assert `data.id` is an opaque identifier (not an incrementing integer sequence) and that requesting the movie again by that same `data.id` returns the same movie.

#### Scenario: Identifier round-trips and hides internal storage
- **WHEN** a consumer retrieves a movie and reuses the `data.id` from the response to request it again
- **THEN** the same movie is returned
- **AND** the identifier does not reveal an internal storage sequence
