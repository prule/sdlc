## ADDED Requirements

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
