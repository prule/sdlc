# catalog/movies Specification

## Purpose
The `catalog/movies` capability lets a public API consumer retrieve one movie's detail by its stable, opaque identifier, so consumers can present accurate movie information sourced from the curated catalog.

## Requirements

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

### Requirement: Search and browse the catalog as an ordered page of movie summaries

The system SHALL provide a public, read-only collection operation that returns the movies matching the supplied criteria as an ordered **page of movie summaries**, together with paging metadata. The response SHALL use the standard success Envelope: `data` is a HAL collection resource carrying the summaries under `data._embedded.movies` and navigation links under `data._links`; `meta.pagination` SHALL report `page`, `size`, `totalElements`, and `totalPages`. The operation SHALL require no authentication and SHALL NOT mutate any catalog data. Success responses SHALL use `application/json`.

A **movie summary** SHALL always include the movie's identifier, title, release year, and one or more genres, and SHALL include runtime and aggregate rating only when the movie has them recorded (an absent optional detail is omitted, not rendered null/empty). A summary SHALL NOT include the synopsis. Each summary SHALL carry a HAL `self` link that resolves to that movie's full detail (the retrieve-by-identifier operation).

Acceptance check: issue the search operation with no criteria; assert HTTP `200 application/json`, `data._embedded.movies` is an array whose items each carry `id`, `title`, `releaseYear`, a non-empty `genres` array, `_links.self` resolving to that movie's detail, and no `synopsis` key; assert `meta.pagination` has `page`, `size`, `totalElements`, `totalPages`, and `meta.correlationId`/`meta.timestamp` are present; assert the catalog is unchanged.

#### Scenario: Browse with no criteria returns a page of summaries
- **WHEN** a consumer requests movies supplying no criteria
- **THEN** the system responds `200` with the standard Envelope
- **AND** `data._embedded.movies` is an array of movie summaries, each with id, title, release year, a non-empty genres list, and a `_links.self` to that movie's detail
- **AND** no summary contains a synopsis
- **AND** `meta.pagination` reports page, size, totalElements, and totalPages
- **AND** the catalog is unchanged

#### Scenario: Summary omits optional details that are not recorded
- **WHEN** the result includes a movie that has no recorded runtime or rating
- **THEN** that summary presents its required details and its recorded optional details only
- **AND** the runtime and rating keys are omitted from that summary rather than shown as null

#### Scenario: Search requires no authentication
- **WHEN** a consumer requests movies without presenting any credential
- **THEN** the system serves the request (it is not rejected as unauthenticated)

### Requirement: Optional criteria combine conjunctively to narrow the result

The system SHALL accept any combination of these optional criteria and SHALL include a movie only if it satisfies **every** supplied criterion (conjunctive; supplying more criteria can only narrow the result):
- **Title term:** a **case-insensitive substring** match — a movie matches if its title contains the term anywhere. Any LIKE wildcard characters in the term (e.g. `%`, `_`) SHALL be treated as **literal** characters, not wildcards.
- **Genre(s):** one or more genres; a movie matches only if it carries **all** supplied genres. A movie that carries the supplied genres **and additional genres beyond them** (a superset) SHALL still match — the criterion requires the supplied genres to be present, not that they be the movie's only genres.
- **Release year:** an inclusive range with an optional lower bound and/or upper bound (either may be supplied alone); a movie matches if its release year falls within the supplied bound(s).
- **Minimum rating:** an inclusive `>=` threshold on the 0–5 scale; a movie with **no** recorded rating SHALL be **excluded** when a minimum rating is supplied.

When no criteria are supplied, every movie in the catalog SHALL match.

Acceptance check: seed movies covering each dimension; assert a `title` term returns only movies whose title contains it case-insensitively; assert two genres return only movies carrying both; assert a movie carrying a superset of genres (e.g. {Drama, Crime, Action}) is still returned by a filter of {Drama, Crime}; assert a year range returns only movies within it (and each bound works alone); assert `minRating=4` returns only movies rated `>= 4` and excludes unrated movies; assert combining criteria returns only movies satisfying all of them; assert a `title` term containing `%` matches only titles containing that literal character.

#### Scenario: Title term is a case-insensitive substring
- **WHEN** a consumer searches with a title term
- **THEN** the result contains exactly the movies whose title contains that term ignoring case, anywhere in the title

#### Scenario: Title wildcard characters match literally
- **WHEN** a consumer searches with a title term that contains a `%` or `_` character
- **THEN** those characters are matched literally
- **AND** the result does not treat them as wildcards matching arbitrary text

#### Scenario: Multiple genres require all to be present
- **WHEN** a consumer supplies more than one genre
- **THEN** the result contains only movies that carry every supplied genre

#### Scenario: A movie with extra genres still matches (superset)
- **WHEN** a consumer supplies a set of genres and a movie carries all of those genres plus additional ones (e.g. the movie is {Drama, Crime, Action} and the filter is {Drama, Crime})
- **THEN** that movie is included in the result
- **AND** having genres beyond the supplied set does not exclude it

#### Scenario: Release-year range with either or both bounds
- **WHEN** a consumer supplies a release-year lower bound, upper bound, or both
- **THEN** the result contains only movies whose release year falls within the supplied bound(s), inclusive

#### Scenario: Minimum rating is inclusive and excludes unrated movies
- **WHEN** a consumer supplies a minimum rating
- **THEN** the result contains only movies whose recorded rating is greater than or equal to that minimum
- **AND** movies with no recorded rating are excluded

#### Scenario: Criteria combine to narrow the result
- **WHEN** a consumer supplies several criteria together
- **THEN** the result contains only movies that satisfy all of the supplied criteria

### Requirement: Results are sorted by a supported field with a deterministic order

The system SHALL order results by exactly one consumer-chosen field — `title`, `releaseYear`, or `rating` — in ascending or descending order. When the consumer requests no ordering, the default SHALL be `releaseYear` descending with `title` ascending as a tiebreak. The system SHALL always apply a **terminal unique tiebreak on the movie identifier** after the requested/default ordering, so that the total order is deterministic and paging across the full result set never skips or duplicates a movie.

Acceptance check: request a sort by each supported field in each direction and assert the returned order matches; request the default and assert `releaseYear` desc then `title` asc; construct movies that tie on the sort field(s), page through the whole result at a small page size, and assert the concatenation of pages contains every movie exactly once with no gaps or repeats.

#### Scenario: Sort by a supported field and direction
- **WHEN** a consumer requests ordering by a supported field in a given direction
- **THEN** the results are ordered by that field in that direction

#### Scenario: Default ordering when none is requested
- **WHEN** a consumer requests no ordering
- **THEN** the results are ordered by release year descending, with title ascending as a tiebreak

#### Scenario: Paging is stable across the full ordered result
- **WHEN** a consumer pages through the entire result set at a small page size, including movies that tie on the sort field
- **THEN** every matching movie appears exactly once across the pages, with none skipped or duplicated

### Requirement: Results are paged with metadata and HAL navigation links

The system SHALL return results one page at a time. The page size SHALL default to `20` and be capped at `100`; the page index SHALL be zero-based and default to `0`. Alongside each page the system SHALL report, in `meta.pagination`, which page it is, its size, the total number of matching movies, and the total number of pages. The reported `totalElements` SHALL equal the actual number of **distinct movies** matching the request under the identical criteria used to select the page rows — including the genre "all-of" filter and any literal-treated title wildcards — so that `totalElements`, `totalPages`, and the `next` link never disagree with the rows actually returned. The collection's `data._links` SHALL carry a `self` link plus page-navigation links (`first`, `last`, `prev`, `next`) using page/size semantics, subject to boundary rules: `prev` SHALL be absent on the first page and `next` SHALL be absent on the last page. Pagination counts live in `meta.pagination`; pagination link URLs live in `data._links` (no duplication).

Acceptance check: with more matches than one page, request a middle page and assert `meta.pagination` reports the correct page/size/totalElements/totalPages and `data._links` contains `self`, `first`, `last`, `prev`, `next` with correct `page` query params; request the first page and assert no `prev`; request the last page and assert no `next`; request without `size` and assert size `20`.

#### Scenario: Middle page reports metadata and all navigation links
- **WHEN** a consumer requests a page that is neither first nor last
- **THEN** `meta.pagination` reports the current page, size, total matching count, and total pages
- **AND** `data._links` contains `self`, `first`, `last`, `prev`, and `next` with correct page query parameters

#### Scenario: First page omits prev, last page omits next
- **WHEN** a consumer requests the first page of a multi-page result
- **THEN** `data._links` contains `next` but not `prev`
- **AND** requesting the last page yields `prev` but not `next`

#### Scenario: Default page size is applied
- **WHEN** a consumer does not specify a page size
- **THEN** the first page is returned at the default size of 20

### Requirement: A valid request that matches nothing is an empty success

The system SHALL treat a valid request that matches no movies as a normal `200` success presenting an empty page, NOT an error. This includes both a request whose criteria exclude every movie (total zero) and a valid request for a page beyond the last page of a non-empty result (total greater than zero). The reported total number of matching movies SHALL distinguish these two cases, and `data._links` on such a page SHALL still carry `self` and SHALL NOT carry a `next`.

Acceptance check: issue criteria matching nothing and assert `200`, empty `data._embedded.movies`, `meta.pagination.totalElements == 0`, no `next` link; issue a valid `page` index beyond the last page of a non-empty result and assert `200`, empty `data._embedded.movies`, `meta.pagination.totalElements > 0`, and `data._links.self` present with no `next`.

#### Scenario: Criteria match no movies
- **WHEN** a consumer supplies valid criteria that match no movies
- **THEN** the system responds `200` with an empty movie list
- **AND** `meta.pagination.totalElements` is zero
- **AND** `data._links` has no `next`

#### Scenario: Page beyond the last page of a non-empty result
- **WHEN** a consumer requests a valid page index beyond the last page of a result that does contain movies
- **THEN** the system responds `200` with an empty movie list
- **AND** `meta.pagination.totalElements` reports the true (non-zero) total
- **AND** `data._links.self` is present and there is no `next`

### Requirement: Invalid search criteria are rejected as a bad request

The system SHALL reject a request whose criteria are invalid with a `400 application/problem+json` response conforming to the shared `Problem` schema and carrying the `correlationId`, WITHOUT attempting any matching. Invalid criteria include: an unsupported sort field, a negative page index, a page size below `1` or above `100`, a minimum rating outside `0–5`, and an unrecognized genre value. This outcome SHALL be distinct from a valid request that matches nothing, and SHALL NOT return `500`.

Acceptance check: issue `sort` by an unsupported field, `page=-1`, `size=0`, `size=101`, `minRating=6`, and an unknown genre; for each assert HTTP `400 application/problem+json`, a stable machine `code`, a `correlationId`, no `_links`/`_embedded`, and that the response is not `500`.

#### Scenario: Unsupported sort field is a bad request
- **WHEN** a consumer requests ordering by a field the system does not support
- **THEN** the system responds `400` with a problem+json body and does not attempt matching
- **AND** the outcome is reported distinctly from a valid request that matches nothing

#### Scenario: Out-of-bounds paging is a bad request
- **WHEN** a consumer requests a negative page index, a page size below one, or a page size above the maximum
- **THEN** the system responds `400` with a problem+json body carrying the correlationId
- **AND** the response is not `500`

#### Scenario: Out-of-range or unrecognized filter values are a bad request
- **WHEN** a consumer supplies a minimum rating outside 0–5 or an unrecognized genre value
- **THEN** the system responds `400` with a problem+json body
- **AND** no matching is attempted

### Requirement: A page of summaries is served with a bounded, page-size-independent query count

The system SHALL fetch a page of movie summaries — including each movie's genres — using a number of database statements that does not grow with the page size (no per-row genre query / no N+1). The statement count SHALL remain constant whether a page contains one movie or the maximum of one hundred.

Acceptance check: with the maximum page size and a full page of multi-genre movies, count the SQL statements issued for one search request (e.g. via Hibernate statistics) and assert the count is a small constant that is identical to the count observed for a single-movie page.

#### Scenario: Query count does not scale with page size
- **WHEN** the system serves a full page of movies that each carry multiple genres
- **THEN** the number of database statements issued is a small constant
- **AND** that count is the same as for a page containing a single movie

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
