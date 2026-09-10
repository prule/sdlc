## ADDED Requirements

### Requirement: Retrieve a person's filmography as an ordered, paged list

The system SHALL provide a public, read-only operation that, given a well-formed person identifier matching a person in the catalog, returns that person's filmography — the movies that person is credited in, viewed from the person side — as an ordered collection presented **one page at a time**. The response SHALL use the standard success Envelope (`data` + `meta`), require no authentication, respond `application/json`, and SHALL NOT mutate any catalog data. Each entry SHALL be a **movie summary** — the movie's identifier, title, release year, genres, and (when recorded) runtime and rating — carrying a HAL `self` link that resolves to that movie's full detail. The filmography SHALL be a **single flat list**, not split into acting and non-acting groups.

Acceptance check: request the filmography of a person `P` who is credited in at least one movie; assert HTTP `200 application/json`, the standard Envelope, that `data` contains a list of entries each carrying a movie identifier, title, release year, genres, and a `self` link to that movie's detail, that `meta.correlationId`/`meta.timestamp` are present, and that the catalog is unchanged.

#### Scenario: Existing person's filmography is returned
- **WHEN** a consumer requests the filmography of a person credited in one or more movies
- **THEN** the system responds `200` with the standard Envelope
- **AND** `data` contains a flat list of entries, each a movie summary with identifier, title, release year, genres, and a `self` link to that movie's detail
- **AND** the catalog is unchanged

#### Scenario: Filmography requires no authentication
- **WHEN** a consumer requests a person's filmography without presenting any credential
- **THEN** the system serves the request (it is not rejected as unauthenticated)

### Requirement: Each entry is annotated with exactly one capacity

Each filmography entry SHALL carry exactly **one capacity** describing how the person contributed to that movie. A capacity SHALL expose a coarse **type** of either `acting` or `non-acting`, plus a display role label: for an `acting` capacity the **character** portrayed (omitted when not recorded), and for a `non-acting` capacity the **area of work and specific role** (e.g. department "Directing" and job "Director"). A person credited in several capacities on the same movie SHALL appear as **several entries**, one per capacity — never a single entry listing multiple capacities.

Acceptance check: request the filmography of a person who both acted in and directed the same movie; assert two distinct entries reference that one movie — one with capacity type `acting` (and its character when recorded), one with capacity type `non-acting` carrying its area and role — and that no single entry lists more than one capacity.

#### Scenario: Acting entry carries its character label
- **WHEN** an entry represents an acting credit
- **THEN** its capacity type is `acting`
- **AND** it carries the character portrayed when one is recorded (and omits it when not)

#### Scenario: Non-acting entry carries its area and role
- **WHEN** an entry represents a non-acting credit
- **THEN** its capacity type is `non-acting`
- **AND** it carries the area of work and the specific role

#### Scenario: Multiple capacities on one movie yield multiple entries
- **WHEN** a person is credited in more than one capacity on the same movie
- **THEN** the filmography contains one entry per capacity, each annotated with its single capacity
- **AND** no entry lists more than one capacity

### Requirement: Filmography is ordered newest-first with a stable total order

The system SHALL order the whole filmography **newest movie first** (by release year, most recent first), then by **title ascending**, then by a **stable terminal tiebreak** that makes the order strict, total, and unchanging across page boundaries — so that when several entries share the same movie and ordering values (e.g. two capacities on one movie) no entry is ever skipped or duplicated as the consumer pages through.

Acceptance check: request a person's filmography spanning several movies across two or more pages; assert entries are ordered by release year descending, then title ascending; assert that concatenating consecutive pages yields every entry exactly once with none skipped or duplicated, including entries that tie on movie and ordering values.

#### Scenario: Entries ordered newest movie first then by title
- **WHEN** a consumer requests a person's filmography
- **THEN** entries are ordered by release year descending, then title ascending

#### Scenario: Stable order across page boundaries
- **WHEN** a consumer pages through a filmography containing entries that tie on movie and ordering values
- **THEN** the terminal tiebreak keeps the order total and unchanging
- **AND** concatenating consecutive pages yields every entry exactly once, none skipped or duplicated

### Requirement: Filmography is paged with default and bounded page size

The system SHALL return the filmography one page at a time and SHALL NOT return it as one unbounded list. The **default page size SHALL be 20** and the **maximum SHALL be 100**. When no page is specified, the system SHALL return the first page at the default size. Alongside each page the system SHALL report which page it is, its size, the total number of entries (of the filtered result), the total number of pages, and HAL navigation links to reach the first, last, next, and previous pages where those exist.

Acceptance check: request a filmography without paging parameters and assert the first page of at most 20 entries is returned with `meta.pagination` reporting page, size, total elements, and total pages, plus `self`/`first`/`last` links (and `next`/`prev` when applicable); request page 1 and assert the `prev` link resolves back to page 0.

#### Scenario: Default page when none specified
- **WHEN** a consumer requests a filmography without stating a page
- **THEN** the system returns the first page at the default size of 20
- **AND** reports the page, size, total elements, and total pages

#### Scenario: Navigation links accompany a page
- **WHEN** a consumer requests a page of a filmography that has following and preceding pages
- **THEN** the response carries HAL `self`, `first`, `last`, `next`, and `prev` links that preserve any supplied filters

### Requirement: Invalid paging parameters are rejected as a bad request

The system SHALL reject a request whose paging parameters are invalid — a negative page position, or a page size below one or above the maximum of 100 — with a `400 application/problem+json` response conforming to the shared `Problem` schema and carrying a stable machine `code` and a `correlationId`, before gathering any entries. This SHALL be distinct from a valid request that yields an empty page.

Acceptance check: request a filmography with `page = -1`, then with `size = 0`, then with `size = 101`; assert each responds `400 application/problem+json` with a stable machine `code` and a `correlationId`, and that no entries are gathered.

#### Scenario: Negative page position
- **WHEN** a consumer requests a filmography with a negative page position
- **THEN** the system responds `400` with a problem+json body and gathers no entries

#### Scenario: Page size out of bounds
- **WHEN** a consumer requests a filmography with a page size below one or above 100
- **THEN** the system responds `400` with a problem+json body and gathers no entries

### Requirement: Filmography may be narrowed by capacity and release-year range

The system SHALL accept two optional filters that combine **conjunctively** (narrow only): a **capacity** filter restricting to `acting` or to `non-acting` entries (the coarse capacity type), and a **release-year range** bounding the movie's release year by an optional inclusive lower and/or upper bound (either bound may be supplied alone; a single year is that year to itself). A person's multiple entries on one movie SHALL be filtered independently — an `acting`-only filter keeps only the acting entry. Supplying no filter SHALL return the whole filmography. An **unrecognised capacity value** SHALL be rejected as a `400 application/problem+json` bad request; a well-formed filter that matches nothing SHALL be a successful empty page, not an error.

Acceptance check: request a filmography with `capacity=acting` and assert only acting entries are returned; request with a release-year range and assert only entries whose movie's release year falls within it are returned; request with both and assert the result is their conjunction; request with an unrecognised capacity value and assert `400 application/problem+json`; request with a valid filter that matches nothing and assert `200` with an empty page and total zero.

#### Scenario: Capacity filter narrows to one type
- **WHEN** a consumer requests a filmography filtered to `acting`
- **THEN** only entries whose capacity type is `acting` are returned
- **AND** a person's non-acting entries on the same movie are excluded

#### Scenario: Release-year range filter
- **WHEN** a consumer requests a filmography with a release-year lower and/or upper bound
- **THEN** only entries whose movie's release year falls within the supplied bound(s) are returned

#### Scenario: Filters combine conjunctively
- **WHEN** a consumer supplies both a capacity filter and a release-year range
- **THEN** only entries matching both criteria are returned

#### Scenario: Unrecognised capacity value is a bad request
- **WHEN** a consumer supplies a capacity value that is not `acting` or `non-acting`
- **THEN** the system responds `400` with a problem+json body and gathers no entries

### Requirement: Empty filmography results are a successful empty page

The system SHALL treat the following as a normal successful outcome, presenting an **empty page** rather than an error: an existing person with no credits at all, a valid filter that matches nothing, and a valid page that lies beyond the last page. The reported total (of the filtered result) SHALL distinguish "nothing matches" — total zero — from "a valid but empty page beyond the last" — total greater than zero, still reporting the way back to pages that contain results.

Acceptance check: request the filmography of an existing person with no credits and assert `200` with an empty page and total zero; request a page far beyond the last of a person who has credits and assert `200` with an empty page, a total greater than zero, and navigation links back to populated pages.

#### Scenario: Existing person with no credits
- **WHEN** a consumer requests the filmography of an existing person who has no credits
- **THEN** the system responds `200` with an empty page and a total of zero

#### Scenario: Filter matches nothing
- **WHEN** a consumer requests a filmography with a valid filter that no entry matches
- **THEN** the system responds `200` with an empty page and a total of zero

#### Scenario: Page beyond the last
- **WHEN** a consumer requests a page beyond the last page of a person who has matching credits
- **THEN** the system responds `200` with an empty page
- **AND** reports the true total (greater than zero) and links back to pages that contain results

### Requirement: Malformed identifier is a bad request; unknown person is not found

The system SHALL reject a filmography request whose person identifier is not well-formed with a `400 application/problem+json` response, before any lookup, and SHALL respond `404 application/problem+json` (`code: PERSON_NOT_FOUND`) when a well-formed identifier matches no person. Both responses SHALL conform to the shared `Problem` schema and carry a stable machine `code` and a `correlationId`, SHALL leave the catalog unchanged, and SHALL be reported as distinct outcomes from each other and from an existing person who simply has no credits (which is a successful empty page).

Acceptance check: request a filmography with an identifier that is not well-formed and assert `400 application/problem+json` with no lookup performed; request one with a well-formed identifier matching no person and assert `404 application/problem+json` with `code: PERSON_NOT_FOUND`; assert the two outcomes are distinct and both distinct from an existing person with no credits.

#### Scenario: Not a well-formed identifier
- **WHEN** a consumer requests a filmography using a value that is not a well-formed person identifier
- **THEN** the system responds `400` with a problem+json body
- **AND** it does not attempt to locate any person

#### Scenario: Well-formed identifier matching no person
- **WHEN** a consumer requests a filmography using a well-formed identifier that matches no catalog person
- **THEN** the system responds `404` with a problem+json body and code `PERSON_NOT_FOUND`
- **AND** the outcome is distinct from a malformed request and from an existing person with no credits
