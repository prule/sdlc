## ADDED Requirements

### Requirement: Retrieve a movie's credits as separated cast and crew

The system SHALL provide a public, read-only sub-resource operation that, given a well-formed movie identifier matching a movie in the catalog, returns that movie's credits separated into two groups: its **cast** (acting credits) and its **crew** (non-acting credits). The response SHALL use the standard success Envelope (`data` + `meta`) with `data` a HAL resource carrying the two groups as **two separate embedded relations** — `data._embedded.cast` and `data._embedded.crew` — never a single list distinguished by a type field. `data._links` SHALL carry a `self` link resolving to this credits sub-resource. The operation SHALL require no authentication, SHALL respond `application/json`, and SHALL NOT mutate any catalog data.

Acceptance check: request the credits of an existing movie whose identifier is `M`; assert HTTP `200 application/json`, `data._embedded.cast` and `data._embedded.crew` are both present as arrays, `data._links.self` resolves to `M`'s credits, `meta.correlationId`/`meta.timestamp` are present, and the catalog is unchanged.

#### Scenario: Existing movie returns cast and crew as two separate groups
- **WHEN** a consumer requests the credits of a movie whose identifier matches a catalog movie
- **THEN** the system responds `200` with the standard Envelope
- **AND** `data._embedded.cast` and `data._embedded.crew` are both present as arrays
- **AND** `data._links.self` points at that same movie's credits
- **AND** the catalog is unchanged

#### Scenario: Credits retrieval requires no authentication
- **WHEN** a consumer requests a movie's credits without presenting any credential
- **THEN** the system serves the request (it is not rejected as unauthenticated)

### Requirement: Cast entries carry the performer, optional character, and billing, ordered by billing

Each entry in `data._embedded.cast` SHALL name the performer (inline, `id` + `name`) and SHALL carry a `billingOrder` — a positive integer where `1` is top billing and a lower number is more prominent. Each cast entry SHALL carry the `character` the performer portrayed only when it is recorded; an unrecorded character SHALL be omitted from that entry entirely (not rendered as null or empty), and its absence SHALL NOT be treated as an error. The cast SHALL be ordered by `billingOrder` ascending (most prominent first).

Acceptance check: seed a movie with several cast credits including one with no recorded character; assert every cast entry carries `person.id`, `person.name`, and a positive-integer `billingOrder`; assert the entry with no character omits the `character` key (not null); assert the entries appear ordered by `billingOrder` ascending.

#### Scenario: Cast is ordered by billing position ascending
- **WHEN** a consumer retrieves a movie whose cast has several performers at different billing positions
- **THEN** the cast entries are ordered by `billingOrder` ascending, top billing (`1`) first

#### Scenario: A cast entry with no recorded character omits it
- **WHEN** a cast entry has no recorded character
- **THEN** that entry presents its performer and billing position
- **AND** the `character` key is omitted from that entry rather than shown as null or empty

### Requirement: Crew entries carry the contributor, department, and job, ordered by department then job

Each entry in `data._embedded.crew` SHALL name the contributor (inline, `id` + `name`) and SHALL carry a `department` (the area of work, e.g. directing, writing, music) and a `job` (the specific role, e.g. director, screenplay, composer), both free text with no controlled vocabulary. The crew SHALL be ordered by `department` ascending, then by `job` ascending, both compared case-insensitively, so contributors in the same area appear together.

Acceptance check: seed a movie with crew credits across departments and jobs differing only in case; assert every crew entry carries `person.id`, `person.name`, a `department`, and a `job`; assert the entries appear ordered by `department` then `job` ascending, case-insensitively.

#### Scenario: Crew is ordered by department then job, case-insensitively
- **WHEN** a consumer retrieves a movie whose crew spans several departments and jobs
- **THEN** the crew entries are ordered by `department` ascending, then `job` ascending
- **AND** the ordering compares department and job case-insensitively so same-area contributors are grouped together

### Requirement: Credited people are named inline only, with no onward person link

Wherever a person appears in a movie's credits (in a cast or crew entry), the system SHALL expose that person inline as their `id` and `name` only. In this capability the inline person SHALL NOT carry any onward link to a standalone person resource, because no such resource exists yet. The inline person representation is shared by cast and crew entries.

Acceptance check: retrieve a movie's credits and assert each cast and crew entry's inline person carries exactly `id` and `name`, and that no person object carries a `_links` (or any onward navigation target).

#### Scenario: Inline person carries id and name only, no onward link
- **WHEN** a consumer retrieves a movie's credits
- **THEN** each cast and crew entry names its person by `id` and `name`
- **AND** no person object carries an onward link to a person resource

### Requirement: An existing movie with no credits is an empty success, not a not-found

The system SHALL treat an existing movie that has no cast and/or no crew recorded as a normal `200` success presenting the corresponding group(s) as an empty array — NOT a `404` and NOT an error. A movie with cast but no crew (or crew but no cast) SHALL present whichever group has entries and present the other group as empty.

Acceptance check: retrieve the credits of an existing movie with no cast or crew and assert `200`, `data._embedded.cast == []`, `data._embedded.crew == []`, and `data._links.self` present; retrieve one with cast but no crew and assert its crew array is empty while its cast is populated.

#### Scenario: Movie with no cast or crew is a 200 with empty groups
- **WHEN** a consumer retrieves the credits of an existing movie that has no credits recorded
- **THEN** the system responds `200` with `data._embedded.cast` and `data._embedded.crew` both empty
- **AND** the response is not a not-found

#### Scenario: Movie with one group empty presents the other populated
- **WHEN** a consumer retrieves the credits of a movie that has cast but no crew (or crew but no cast)
- **THEN** the populated group is returned with its entries
- **AND** the other group is returned as an empty array

### Requirement: Credits are returned whole, not paginated

The system SHALL return a movie's full set of cast and crew in a single response, not one page at a time. The credits operation SHALL NOT accept or require paging parameters, and `meta` SHALL NOT carry pagination for this operation.

Acceptance check: retrieve the credits of a movie with many cast and crew and assert the full set is returned in one response with no pagination metadata and no page/size parameters honored.

#### Scenario: Full cast and crew returned in one response
- **WHEN** a consumer retrieves the credits of a movie with many cast and crew members
- **THEN** the entire cast and crew are returned in a single response
- **AND** the response carries no pagination metadata

### Requirement: Credits for a malformed identifier are rejected as a bad request before lookup

The system SHALL reject a credits request whose supplied movie identifier is not a well-formed identifier with a `400 application/problem+json` response, WITHOUT attempting to locate any movie or its credits. This outcome SHALL be distinct from the not-found outcome and SHALL leave the catalog unchanged.

Acceptance check: request credits with an identifier that is not a well-formed movie identifier; assert HTTP `400 application/problem+json`, a stable machine `code`, and a `correlationId`; assert no catalog lookup occurred.

#### Scenario: Malformed movie identifier on the credits sub-resource
- **WHEN** a consumer requests credits using a value that is not a well-formed movie identifier
- **THEN** the system responds `400` with a problem+json body
- **AND** it does not attempt to locate any movie or credits
- **AND** the outcome is reported distinctly from not-found

### Requirement: Credits for a well-formed unknown movie identifier are not found

The system SHALL respond `404 application/problem+json` when a credits request supplies a well-formed identifier that matches no movie in the catalog. This SHALL be reported as a distinct outcome from a malformed request and from an existing movie with no credits recorded, and SHALL leave the catalog unchanged.

Acceptance check: request credits with a well-formed identifier matching no movie; assert HTTP `404 application/problem+json`, a stable machine `code`, and a `correlationId`; assert the outcome is distinct from an existing movie with empty credits (which is `200`).

#### Scenario: No movie carries the identifier
- **WHEN** a consumer requests credits using a well-formed identifier that matches no catalog movie
- **THEN** the system responds `404` with a problem+json body
- **AND** the outcome is reported distinctly from a malformed request and from an existing movie with empty credits

## MODIFIED Requirements

### Requirement: Retrieve a movie's detail by identifier

The system SHALL provide a read-only operation that, given a well-formed stable movie identifier that matches a movie in the catalog, returns that movie's detail. The detail SHALL always include the movie's identifier, title, release year, and one or more genres. The response SHALL use the standard success Envelope (`data` + `meta`), with the movie detail in `data` carrying a HAL `self` link and a HAL `credits` link that resolves to that movie's credits sub-resource (`GET /movies/{id}/credits`), and SHALL NOT mutate any catalog data. The `credits` link SHALL be present on every movie detail regardless of whether that movie has any credits recorded (an existing movie with no credits still exposes a resolvable credits sub-resource that returns empty groups).

Acceptance check: issue the retrieve operation for an existing movie whose identifier is `M`; assert HTTP `200 application/json`, `data.id == M`, `data.title`, `data.releaseYear`, and `data.genres` (a non-empty array) are present, `data._links.self` resolves to the same movie, `data._links.credits` resolves to that movie's credits sub-resource, and `meta.correlationId`/`meta.timestamp` are present.

#### Scenario: Existing movie is returned with all required detail
- **WHEN** a consumer requests the detail of a movie whose identifier matches a catalog movie
- **THEN** the system responds `200` with the standard Envelope
- **AND** `data` contains the identifier, title, release year, and a non-empty list of genres
- **AND** `data._links.self` points at that same movie
- **AND** `data._links.credits` points at that movie's credits sub-resource
- **AND** the catalog is unchanged

#### Scenario: Retrieval requires no authentication
- **WHEN** a consumer requests a movie's detail without presenting any credential
- **THEN** the system serves the request (it is not rejected as unauthenticated)

#### Scenario: Movie detail links onward to its credits
- **WHEN** a consumer retrieves the detail of a movie
- **THEN** `data._links.credits` is present and resolves to that movie's `GET /movies/{id}/credits` sub-resource
- **AND** the link is present even when the movie has no credits recorded
