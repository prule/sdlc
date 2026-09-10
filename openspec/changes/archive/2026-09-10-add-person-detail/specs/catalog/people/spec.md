## Purpose

The `catalog/people` capability lets a public API consumer retrieve one person's core details (identifier and name) by their stable, opaque identifier, and navigate onward to that person's filmography, so consumers can present a person's page sourced from the curated catalog.

## ADDED Requirements

### Requirement: Retrieve a person's core details by identifier

The system SHALL provide a public, read-only operation that, given a well-formed stable person identifier that matches a person in the catalog, returns that person's core details. The details SHALL include exactly the person's identifier and name — and **no biographical fields** (no birth date, biography, or images). The response SHALL use the standard success Envelope (`data` + `meta`), with the person details in `data` carrying a HAL `self` link, SHALL require no authentication, SHALL respond `application/json`, and SHALL NOT mutate any catalog data.

Acceptance check: issue the retrieve operation for an existing person whose identifier is `P`; assert HTTP `200 application/json`, `data.id == P`, `data.name` is present and non-empty, `data` carries no biographical field, `data._links.self` resolves to the same person, and `meta.correlationId`/`meta.timestamp` are present; assert the catalog is unchanged.

#### Scenario: Existing person is returned with identifier and name
- **WHEN** a consumer requests the details of a person whose identifier matches a catalog person
- **THEN** the system responds `200` with the standard Envelope
- **AND** `data` contains the person's identifier and name and no biographical field
- **AND** `data._links.self` points at that same person
- **AND** the catalog is unchanged

#### Scenario: Retrieval requires no authentication
- **WHEN** a consumer requests a person's details without presenting any credential
- **THEN** the system serves the request (it is not rejected as unauthenticated)

### Requirement: Person details link onward to the person's filmography

Every person's details SHALL carry a HAL `credits` link under `data._links` that resolves to that person's filmography sub-resource (`GET /people/{id}/credits`) — the movies that person is credited in. The link SHALL be assembled from the person's identifier alone, without the details response enumerating any movies, and SHALL be present on every person's details regardless of how many movies that person is credited in (including a person credited in none).

Acceptance check: retrieve a person and assert `data._links.credits` is present and resolves to that person's `GET /people/{id}/credits` sub-resource; assert the details response contains no list of movies; retrieve a person credited in no movies and assert the `credits` link is still present.

#### Scenario: Person details expose a filmography link
- **WHEN** a consumer retrieves a person's details
- **THEN** `data._links.credits` is present and resolves to that person's `GET /people/{id}/credits` sub-resource
- **AND** the details response itself does not enumerate the person's movies

#### Scenario: Filmography link is present even when the person has no credits
- **WHEN** a consumer retrieves the details of a person credited in no movies
- **THEN** `data._links.credits` is still present and resolves to that person's filmography sub-resource

### Requirement: Malformed person identifier is rejected as a bad request before lookup

The system SHALL reject a request whose supplied person identifier is not a well-formed identifier with a `400 application/problem+json` response conforming to the shared `Problem` schema and carrying a stable machine `code` and a `correlationId`, WITHOUT attempting to locate any person. This outcome SHALL be distinct from the not-found outcome and SHALL leave the catalog unchanged.

Acceptance check: issue the retrieve operation with an identifier that is not a well-formed person identifier; assert HTTP `400 application/problem+json`, a stable machine `code`, and a `correlationId`; assert no catalog lookup occurred and the outcome is reported distinctly from not-found.

#### Scenario: Not a well-formed identifier
- **WHEN** a consumer requests a person using a value that is not a well-formed person identifier
- **THEN** the system responds `400` with a problem+json body
- **AND** it does not attempt to locate any person
- **AND** the outcome is reported distinctly from not-found

### Requirement: Well-formed identifier matching no person is not found

The system SHALL respond `404 application/problem+json`, conforming to the shared `Problem` schema and carrying a stable machine `code` and a `correlationId`, when a well-formed identifier matches no person in the catalog. This SHALL be reported as a distinct outcome from a malformed request and SHALL leave the catalog unchanged.

Acceptance check: issue the retrieve operation with a well-formed identifier that matches no person; assert HTTP `404 application/problem+json`, a stable machine `code`, and a `correlationId`; assert the outcome is reported distinctly from a malformed request.

#### Scenario: No person carries the identifier
- **WHEN** a consumer requests a person using a well-formed identifier that matches no catalog person
- **THEN** the system responds `404` with a problem+json body
- **AND** the outcome is reported distinctly from a malformed request

### Requirement: Person identifiers are stable and opaque

Each person SHALL be addressed by a stable identifier that is unique within the catalog and that does not expose internal storage details (such as database sequence numbers). The identifier presented in a person's details SHALL be the same value used to address them, and SHALL be the same value by which that person is named inline in a movie's credits.

Acceptance check: retrieve a person and assert `data.id` is an opaque identifier (not an incrementing integer sequence) and that requesting the person again by that same `data.id` returns the same person; assert the identifier matches the one used to name that person inline in a movie's credits.

#### Scenario: Identifier round-trips and hides internal storage
- **WHEN** a consumer retrieves a person and reuses the `data.id` from the response to request them again
- **THEN** the same person is returned
- **AND** the identifier does not reveal an internal storage sequence
