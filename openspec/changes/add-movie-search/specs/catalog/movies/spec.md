## ADDED Requirements

### Requirement: Search and browse movies
The service SHALL offer `GET /api/v1/movies`. Every query parameter SHALL be optional: `title`, `genre` (repeatable), `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`, `page` and `size`. With no criteria, every movie in the catalog SHALL match (browsing). A search asked in an allowed way SHALL respond `200` with `Content-Type: application/json`, never `application/problem+json`, even when the request's `Accept` header names only the problem media type. The body SHALL be the paged collection form defined by `platform/uniform-responses`, with the movie summaries in `data._embedded.movies` (always present, possibly empty) and `meta.pagination` filled. Searching SHALL require no credentials. Acceptance check: a Testcontainers-backed test inserts three movies, sends `GET /api/v1/movies` with no parameters and no `Authorization` header, and asserts the status, the `Content-Type`, that `data._embedded.movies` holds all three, `meta.pagination.totalElements` is `3`, and that `meta.correlationId` equals the `X-Correlation-Id` header. The success body validates against the served interface description.

#### Scenario: Browse the whole catalog
- **WHEN** the catalog holds three movies and a client sends `GET /api/v1/movies` with no parameters and no credentials
- **THEN** the response status is `200`, the `Content-Type` is `application/json`, `data._embedded.movies` has three entries, and `meta.pagination` is `{"page":0,"size":20,"totalElements":3,"totalPages":1}`

#### Scenario: Success is never labelled as a problem
- **WHEN** a client sends `GET /api/v1/movies` with `Accept: application/problem+json`
- **THEN** the response status is `200` and the `Content-Type` is `application/json`

### Requirement: Search results are movie summaries
Each entry in `data._embedded.movies` SHALL be a movie summary containing only `id`, `title`, `releaseYear`, `genres`, `runtimeMinutes`, `rating` and `_links`. It SHALL NOT contain `synopsis`, keywords, cast, crew, credits, reviews or a vote count. `id`, `title`, `releaseYear`, `genres` and `_links` SHALL always be present. `id` SHALL be in canonical lowercase form. `genres` SHALL be the movie's genre names in alphabetical order ignoring letter case, and `[]` when the movie has none. `runtimeMinutes` and `rating` SHALL be omitted when not recorded, never shown as `null`, `0` or another default. `rating` SHALL be written in the same shortest exact decimal form as in the movie's details (`5`, `4.5`, `0`). `_links` SHALL carry only `self`, whose `href` is the absolute URI of that movie's details (`/api/v1/movies/{id}`), honouring forwarded scheme and host; requesting it SHALL return that movie's details. Acceptance check: a Testcontainers-backed test inserts a fully recorded movie and a movie with no runtime, rating or genres, searches, and asserts each summary's member set exactly, the absent keys, the literal JSON text of `rating`, and that following `_links.self.href` returns `200` with the same `id`.

#### Scenario: Fully recorded movie summary
- **WHEN** the catalog holds `Arrival` (2016, runtime `116`, synopsis recorded, rating `4.5`, genres `Sci-Fi` and `Drama`) and a client searches with `title=arrival`
- **THEN** the single summary has `title` `Arrival`, `releaseYear` `2016`, `genres` `["Drama","Sci-Fi"]`, `runtimeMinutes` `116`, `rating` `4.5`, no `synopsis` key, and `_links.self.href` ending with `/api/v1/movies/<Arrival's id>`

#### Scenario: Unrecorded details are absent
- **WHEN** a matching movie has no runtime, no rating and no genres recorded
- **THEN** its summary is still present, `genres` is `[]`, and it has no `runtimeMinutes` or `rating` key

#### Scenario: Summary links honour forwarded headers
- **WHEN** a client searches with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test`
- **THEN** each summary's `_links.self.href` starts with `https://api.example.test/api/v1/movies/`

### Requirement: Title criterion matches any part of the title ignoring case
When `title` is given and is not blank, a movie SHALL match only if the term appears anywhere in its title, ignoring letter case. Characters in the term SHALL be matched literally; `%`, `_` and `\` SHALL have no wildcard or escape meaning. An empty or whitespace-only `title` SHALL count as no title criterion (browsing) and SHALL NOT be refused. Acceptance check: a Testcontainers-backed test with titles `The Grand Heist`, `Heist Night`, `Arrival` and `100% Love` asserts the matches for `heist`, `HEIST`, `rand h`, `%`, `_` and a blank term.

#### Scenario: Case-insensitive substring match
- **WHEN** the catalog holds `The Grand Heist`, `Heist Night` and `Arrival` and a client sends `GET /api/v1/movies?title=HEIST`
- **THEN** `meta.pagination.totalElements` is `2` and the results are `The Grand Heist` and `Heist Night`

#### Scenario: Wildcard characters are literal
- **WHEN** the catalog holds `100% Love` and `Arrival` and a client searches with `title=%`
- **THEN** only `100% Love` matches

#### Scenario: Blank title term browses
- **WHEN** a client sends `GET /api/v1/movies?title=%20%20`
- **THEN** the response status is `200` and every movie in the catalog matches

### Requirement: Genre criterion requires all given genres
`genre` MAY be given several times. A movie SHALL match only if it carries every given genre. A genre value SHALL be recognised ignoring letter case (`drama` means `Drama`); giving the same genre more than once SHALL have the same effect as giving it once. Every given value SHALL name a genre in the curated genre vocabulary (the genres the catalog holds); otherwise the search is refused as described in "Disallowed search is refused before searching". Acceptance check: a Testcontainers-backed test with movies tagged `{Drama}`, `{Drama, Sci-Fi}` and `{Sci-Fi}` asserts the matches for `genre=drama`, `genre=Drama&genre=sci-fi`, and `genre=DRAMA&genre=drama`.

#### Scenario: Several genres mean all of them
- **WHEN** the catalog holds `A` (Drama), `B` (Drama, Sci-Fi) and `C` (Sci-Fi) and a client sends `GET /api/v1/movies?genre=Drama&genre=sci-fi`
- **THEN** only `B` matches

#### Scenario: Genre recognised ignoring case
- **WHEN** a client sends `GET /api/v1/movies?genre=drama`
- **THEN** the response status is `200` and the results are exactly the movies carrying `Drama`

### Requirement: Release-year criterion is an inclusive range
`releaseYearFrom` and `releaseYearTo` SHALL each be optional integers. A movie SHALL match only if its release year is at least `releaseYearFrom` (when given) and at most `releaseYearTo` (when given); both bounds are inclusive. Giving the same year for both SHALL mean only that year. A lower bound after the upper bound SHALL be refused. Acceptance check: a Testcontainers-backed test with movies from 1989, 1990, 1999 and 2000 asserts the matches for `releaseYearFrom=1990&releaseYearTo=1999`, `releaseYearFrom=1999`, `releaseYearTo=1990` and `releaseYearFrom=1990&releaseYearTo=1990`.

#### Scenario: Both bounds count
- **WHEN** the catalog holds movies from 1989, 1990, 1999 and 2000 and a client sends `GET /api/v1/movies?releaseYearFrom=1990&releaseYearTo=1999`
- **THEN** exactly the 1990 and 1999 movies match

#### Scenario: A single year
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=1999&releaseYearTo=1999`
- **THEN** only movies released in 1999 match

#### Scenario: Open-ended range
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=1999`
- **THEN** exactly the 1999 and 2000 movies match

### Requirement: Minimum-rating criterion is inclusive and excludes unrated movies
`minRating` SHALL be an optional number from `0` to `5` inclusive. When given, a movie SHALL match only if it has a recorded rating greater than or equal to `minRating`. Movies with no recorded rating SHALL NOT match, including when `minRating` is `0`. Acceptance check: a Testcontainers-backed test with movies rated `3.5`, `4`, `4.5` and one unrated asserts the matches for `minRating=4`, `minRating=0` and `minRating=4.25`.

#### Scenario: Inclusive minimum
- **WHEN** the catalog holds movies rated `3.5`, `4` and `4.5` and one unrated movie, and a client sends `GET /api/v1/movies?minRating=4`
- **THEN** exactly the movies rated `4` and `4.5` match

#### Scenario: Unrated movies are left out even at zero
- **WHEN** a client sends `GET /api/v1/movies?minRating=0`
- **THEN** every rated movie matches and the unrated movie does not

### Requirement: All criteria combine to narrow the results
When several criteria are given, a movie SHALL match only if it meets every one of them. Acceptance check: a Testcontainers-backed test asserts that a search with `title`, two `genre` values, a year range and `minRating` returns exactly the one movie meeting all five, while a movie failing only one criterion is absent.

#### Scenario: Every criterion must hold
- **WHEN** the catalog holds `Heist Night` (2001, Drama + Thriller, rating 4) and `Heist Day` (2001, Drama + Thriller, rating 3), and a client sends `GET /api/v1/movies?title=heist&genre=drama&genre=thriller&releaseYearFrom=2000&releaseYearTo=2005&minRating=3.5`
- **THEN** only `Heist Night` matches

### Requirement: Results are ordered completely and stably
`sort` SHALL accept exactly `title`, `-title`, `releaseYear`, `-releaseYear`, `rating` and `-rating`; a leading `-` means descending. When `sort` is absent the order SHALL be release year newest first, then title A–Z. Title order SHALL ignore letter case. When the chosen key ties, movies SHALL be ordered by title A–Z (for the non-title orders) and then by a final tiebreak that makes the order complete. The final tiebreak is stable but not part of the contract. When ordering by `rating` or `-rating`, movies with no recorded rating SHALL come after all rated movies. The same search SHALL always list the same movies in the same order, and walking the pages SHALL return every matching movie exactly once. Acceptance check: a Testcontainers-backed test asserts the exact order for each `sort` value and for the default over a fixture with tied years, tied ratings, tied titles differing only in case, and unrated movies; a second test walks every page with `size=2` and asserts that the concatenation equals the single-page (`size=100`) result with no duplicates or gaps.

#### Scenario: Default order
- **WHEN** the catalog holds `Zodiac` (2007), `Arrival` (2016), `Atonement` (2007) and a client sends `GET /api/v1/movies`
- **THEN** the order is `Arrival`, `Atonement`, `Zodiac`

#### Scenario: Unrated movies last in either direction
- **WHEN** the catalog holds movies rated `3` and `5` and one unrated movie, and a client sends `sort=rating`, then `sort=-rating`
- **THEN** the orders are rated-3, rated-5, unrated and rated-5, rated-3, unrated

#### Scenario: Title order ignores case
- **WHEN** the catalog holds `alien`, `Arrival` and `Brazil` and a client sends `sort=-title`
- **THEN** the order is `Brazil`, `Arrival`, `alien`

#### Scenario: Pages neither repeat nor skip
- **WHEN** five movies share the same release year and title prefix, and a client walks every page of `GET /api/v1/movies?size=2`
- **THEN** each of the five movies appears exactly once across the three pages, in the same order as `GET /api/v1/movies?size=100`

### Requirement: Results are paged with totals
Results SHALL be returned one page at a time. `page` SHALL be counted from `0` and default to `0`. `size` SHALL default to `20` and allow `1` to `100`. `meta.pagination` SHALL hold the requested `page`, the effective `size`, `totalElements` (the number of matches across all pages) and `totalPages` (`totalElements` divided by `size`, rounded up; `0` when nothing matches). A page after the last SHALL be a `200` with an empty `data._embedded.movies` and the true totals, never a refusal or `404`. Acceptance check: a Testcontainers-backed test with 45 movies asserts the entry counts and `meta.pagination` for the default request, `page=2`, `size=100`, and `page=7`.

#### Scenario: Default page size
- **WHEN** the catalog holds 45 movies and a client sends `GET /api/v1/movies`
- **THEN** `data._embedded.movies` has 20 entries and `meta.pagination` is `{"page":0,"size":20,"totalElements":45,"totalPages":3}`

#### Scenario: Last partial page
- **WHEN** the catalog holds 45 movies and a client sends `GET /api/v1/movies?page=2`
- **THEN** `data._embedded.movies` has 5 entries

#### Scenario: Page after the last
- **WHEN** the catalog holds 45 movies and a client sends `GET /api/v1/movies?page=7`
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, and `meta.pagination.totalElements` is `45` and `totalPages` is `3`

### Requirement: Pages link to themselves and their neighbours with the same criteria
`data._links` SHALL carry `self`, `first` and `last`, plus `prev` and `next` where those pages exist, as defined by the paged collection form in `platform/uniform-responses`. Every link SHALL be an absolute URI to `GET /api/v1/movies` that keeps exactly the criteria, `sort` and `size` parameters present on the request, with their values as sent, and differs from the request only in `page`. A parameter the client did not send SHALL NOT appear on any link. The links SHALL be navigation only. Acceptance check: a web test with the search use case stubbed asserts every link for the first, a middle, the last, an after-last and an empty-result page of a request carrying `title`, two `genre` values, `minRating`, `sort` and `size`, and asserts that a request with no parameters produces links with only `page`.

#### Scenario: Middle page keeps the criteria
- **WHEN** 45 movies match and a client sends `GET /api/v1/movies?genre=drama&sort=-rating&size=20&page=1`
- **THEN** `prev` has `page=0`, `next` has `page=2`, `first` has `page=0`, `last` has `page=2`, and every link also carries `genre=drama`, `sort=-rating` and `size=20`

#### Scenario: Defaults are not added to links
- **WHEN** a client sends `GET /api/v1/movies` with no parameters and 45 movies match
- **THEN** `next.href` ends with `/api/v1/movies?page=1` and no link carries `size`, `sort` or any criterion

#### Scenario: First and last page boundaries
- **WHEN** a client requests the first page of a multi-page result, then the last page
- **THEN** the first page has no `prev` and the last page has no `next`

#### Scenario: Page after the last still points home
- **WHEN** 45 movies match and a client sends `GET /api/v1/movies?page=7`
- **THEN** `data._links` has `self` (`page=7`), `first` (`page=0`) and `last` (`page=2`), and no `next`

### Requirement: A search that matches nothing is a success
A search asked in an allowed way that matches no movie, including any search of an empty catalog, SHALL respond `200` with `data._embedded.movies` `[]`, `meta.pagination.totalElements` `0`, `totalPages` `0`, and `self`, `first` and `last` links (`first` and `last` both at `page=0`). It SHALL NOT respond `404`. Acceptance check: a Testcontainers-backed test asserts this for a title that matches nothing and for an empty catalog.

#### Scenario: No movie matches
- **WHEN** a client sends `GET /api/v1/movies?title=zzzz-no-such-title`
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, `meta.pagination.totalElements` is `0`, and `data._links.self.href` ends with `/api/v1/movies?title=zzzz-no-such-title`

#### Scenario: Empty catalog
- **WHEN** the catalog holds no movies and a client sends `GET /api/v1/movies`
- **THEN** the response status is `200` and `data._embedded.movies` is `[]`

### Requirement: Disallowed search is refused before searching
The service SHALL respond `400` `application/problem+json` with `code` `BAD_REQUEST`, and SHALL NOT search the catalog, when any of these holds: `sort` is not one of the supported values; `page` is below `0`; `size` is below `1` or above `100`; a `genre` value (including an empty one) is not in the curated genre vocabulary; `releaseYearFrom` is after `releaseYearTo`; `minRating` is below `0` or above `5`; or any parameter value is not of its declared type (for example `page=abc`, `minRating=high`, or an integer outside the 32-bit range). The problem SHALL list the offending parameter name(s) in `errors[].field` as defined in `platform/uniform-responses`; for a reversed year range it SHALL list both `releaseYearFrom` and `releaseYearTo`. The body SHALL NOT echo the supplied value. This outcome SHALL be distinct from a search that matches nothing (`200`) and from an internal fault (`500`). Acceptance check: a parameterised web test with the search use case mocked covers every listed case and asserts `400`, `BAD_REQUEST`, the named `errors[].field`, that the supplied value is absent from the body, and that the search was never invoked; a Testcontainers-backed test covers the unknown-genre case against the real vocabulary.

#### Scenario: Unsupported order
- **WHEN** a client sends `GET /api/v1/movies?sort=popularity`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `errors[0].field` is `sort`, and the body does not contain `popularity`

#### Scenario: Page size out of bounds
- **WHEN** a client sends `GET /api/v1/movies?size=101`, and separately `size=0`
- **THEN** both responses have status `400` with `errors[0].field` `size`

#### Scenario: Page before the first
- **WHEN** a client sends `GET /api/v1/movies?page=-1`
- **THEN** the response status is `400` with `errors[0].field` `page`

#### Scenario: Genre not in the vocabulary
- **WHEN** the vocabulary holds `Drama` and `Sci-Fi` and a client sends `GET /api/v1/movies?genre=drama&genre=Telenovela`
- **THEN** the response status is `400`, `errors[0].field` is `genre`, and the body does not contain `Telenovela`

#### Scenario: Reversed release-year range
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=2000&releaseYearTo=1990`
- **THEN** the response status is `400` and `errors[].field` contains `releaseYearFrom` and `releaseYearTo`

#### Scenario: Minimum rating out of range
- **WHEN** a client sends `GET /api/v1/movies?minRating=5.5`, and separately `minRating=-1`
- **THEN** both responses have status `400` with `errors[0].field` `minRating`

#### Scenario: Ill-typed value
- **WHEN** a client sends `GET /api/v1/movies?page=abc`
- **THEN** the response status is `400` with `errors[0].field` `page`

### Requirement: Search internal fault is reported generically
If searching fails because of an unexpected internal fault, the service SHALL respond `500` `application/problem+json` with `code` `INTERNAL_ERROR`, `detail` `An unexpected error occurred.`, the request's `correlationId`, and no `errors` member. The body SHALL reveal no internal detail. Acceptance check: a web-slice test in which the search throws an exception whose message is `secret-db-host:5432 refused` asserts `500`, `INTERNAL_ERROR`, the generic detail, and that `secret-db-host` does not appear in the body.

#### Scenario: Search fails unexpectedly
- **WHEN** a client sends `GET /api/v1/movies` and the search throws `secret-db-host:5432 refused`
- **THEN** the response status is `500`, `code` is `INTERNAL_ERROR`, and the body does not contain `secret-db-host`

### Requirement: Movie collection is public and read-only
`POST`, `PUT`, `PATCH` and `DELETE` on `/api/v1/movies` SHALL respond `405` `application/problem+json` with `code` `METHOD_NOT_ALLOWED` and an `Allow` header that includes `GET`. They SHALL NOT respond `2xx`, `401`, `403` or `5xx`, and SHALL leave the catalog unchanged. Searching SHALL never change catalog data. Acceptance check: a parameterised web test over the four write methods with a JSON body, no credentials and no CSRF token asserts `405`, the `Allow` header and `METHOD_NOT_ALLOWED`; a Testcontainers-backed test asserts that `meta.pagination.totalElements` of a following browse is unchanged.

#### Scenario: Attempt to add a movie is refused
- **WHEN** a client sends `POST /api/v1/movies` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, the `code` is `METHOD_NOT_ALLOWED`, and a following browse returns the same total

### Requirement: Movie search behaves identically in both runtime modes
In standalone mode (no profile) and persistent mode (`postgres` profile), `GET /api/v1/movies` SHALL answer the same way for the same catalog contents, including the order of results. In standalone mode, browsing SHALL list the sample movies. Acceptance check: the shared movie runtime-mode assertion set, run by both the H2 smoke test and the PostgreSQL integration test, asserts `200` with the paged form for `GET /api/v1/movies?size=1` and `400` `BAD_REQUEST` with `errors[0].field` `sort` for `sort=popularity`. The H2 smoke test additionally asserts the default-order browse of the sample movies.

#### Scenario: Standalone browse lists the samples newest first
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies`
- **THEN** the titles are `Arrival`, `The Grand Heist`, `Laugh Track`, `Untitled Reel`, in that order, and `meta.pagination.totalElements` is `4`

#### Scenario: Persistent start serves searches
- **WHEN** the service is started with the `postgres` profile and a client sends `GET /api/v1/movies?size=1`
- **THEN** the response status is `200` and the body is the paged collection form
