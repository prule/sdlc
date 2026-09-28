## ADDED Requirements

### Requirement: Search and browse movies
The service SHALL offer `GET /api/v1/movies`. Every query parameter is optional:
- `title`: a title term;
- `genre`: a genre name, which may be repeated;
- `releaseYearFrom` and `releaseYearTo`: integers;
- `minRating`: a number;
- `sort`: an ordering;
- `page` and `size`: see `platform/collection-paging`.

A valid request SHALL be answered `200` with `Content-Type: application/json`, never `application/problem+json`, even when the request's `Accept` header names only the problem media type. The body SHALL be the uniform success envelope:
- `data._embedded.movies` holds one movie summary for each movie on the requested page;
- `data._links` holds the navigation links;
- `meta` holds `timestamp`, `correlationId` and `pagination`.

The paging, page metadata, links, empty-page behaviour and parameter refusals SHALL follow `platform/collection-paging`. The recognised parameters for link preservation are exactly the eight listed above. With no criteria, every movie in the catalog SHALL match (browsing). An empty value for a numeric parameter (`minRating=`, `releaseYearFrom=`, `releaseYearTo=`, `page=`, `size=`) SHALL be treated as if the parameter were absent. An empty `sort=` or `genre=` SHALL be refused, as defined below. When `title` or `sort` is given more than once, the values SHALL be joined into one value, with a comma between them. For `title`, the joined value is used as a literal term. For `sort`, the joined value is refused as an unsupported order. Acceptance check: a Testcontainers-backed test inserts three movies and sends `GET /api/v1/movies`. It asserts:
- the status and `Content-Type`;
- that `meta.correlationId` equals `X-Correlation-Id`;
- that `meta.pagination.totalElements` is `3`;
- three summaries;
- that the body validates against the served interface description's closed schema.

A web-slice test asserts that the empty numeric parameters reach the use case as absent, and that `title=a&title=b` reaches it as the term `a,b`.

#### Scenario: Browse the whole catalog
- **WHEN** a client sends `GET /api/v1/movies` with no query parameters while the catalog holds 3 movies
- **THEN** the response status is `200`, the `Content-Type` is `application/json`, `data._embedded.movies` holds 3 summaries, and `meta.pagination.totalElements` is `3`

#### Scenario: Empty numeric parameters are ignored
- **WHEN** a client sends `GET /api/v1/movies?minRating=&releaseYearFrom=&releaseYearTo=`
- **THEN** the response status is `200` and the result is the same as for `GET /api/v1/movies`

#### Scenario: Repeated sort is refused
- **WHEN** a client sends `GET /api/v1/movies?sort=title&sort=rating`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` contains `'sort'`

#### Scenario: Success is never labelled as a problem
- **WHEN** a client sends `GET /api/v1/movies` with `Accept: application/problem+json`
- **THEN** the response status is `200` and the `Content-Type` is `application/json`

### Requirement: Movie summaries contain exactly the summary information
Each movie summary SHALL contain only `id`, `title`, `releaseYear`, `genres`, `runtimeMinutes`, `rating` and `_links`. It SHALL NOT contain `synopsis`, keywords, cast, crew, credits, reviews or a vote count. The members SHALL follow the same rules as the movie's details:
- `id` is the identifier in canonical lowercase form;
- `releaseYear` is an integer;
- `genres` is the movie's genre names, de-duplicated and in alphabetical order ignoring letter case, and is `[]` when the movie has no genres;
- `runtimeMinutes` and `rating` are omitted when unrecorded, never `null`, `0` or another default value;
- `rating` is written in its shortest exact decimal form.

`_links` SHALL carry only `self`, whose `href` SHALL be identical to the `data._links.self.href` that `GET /api/v1/movies/{id}` returns for the same movie, under the same forwarded scheme and host. Acceptance check: a Testcontainers-backed test inserts:
- a fully curated movie (with a synopsis);
- a movie with nothing optional recorded and no genres;
- a movie rated `5.0`.

It asserts the exact member set of each summary and the literal JSON text of the ratings. It follows each `self.href` and asserts `200` and an equal `id`.

#### Scenario: Fully curated movie summary has no synopsis
- **WHEN** a client searches and the page contains a movie recorded with runtime `116`, synopsis `A linguist is recruited…`, rating `4.5` and genres `Sci-Fi`, `Drama`
- **THEN** its summary has `runtimeMinutes` `116`, `rating` `4.5` and `genres` `["Drama", "Sci-Fi"]`, and has no `synopsis` member

#### Scenario: Unrecorded details are absent
- **WHEN** a client searches and the page contains a movie with no runtime or rating recorded and no genres
- **THEN** its summary has `genres` `[]` and has no `runtimeMinutes` or `rating` key

#### Scenario: Summary points to the movie's details
- **WHEN** a client follows a summary's `_links.self.href`
- **THEN** the response is `200` from `GET /api/v1/movies/{id}` with the same `id`

### Requirement: Title term matches anywhere in the title, ignoring case
When `title` is given and contains at least one non-whitespace character, a movie SHALL match only if the term appears as a contiguous substring anywhere in its title, compared ignoring letter case. The term SHALL be used as supplied, and is not trimmed. The characters `%`, `_` and `\` in the term SHALL be matched literally, never as wildcards. An empty or whitespace-only `title` SHALL be treated as no title criterion, and SHALL NOT be refused. Acceptance check: a Testcontainers-backed test with the movies `The Grand Heist`, `Heist Night`, `Arrival` and `100%_Real` asserts the matches for `heist`, `HEIST`, `rand h`, `%`, `_` and `title=` (blank).

#### Scenario: Case-insensitive substring
- **WHEN** a client sends `GET /api/v1/movies?title=HEIST` while the catalog holds `The Grand Heist`, `Heist Night` and `Arrival`
- **THEN** exactly `The Grand Heist` and `Heist Night` are returned and `totalElements` is `2`

#### Scenario: Wildcard characters are literal
- **WHEN** a client sends `GET /api/v1/movies?title=%25` while the catalog holds `100%_Real` and `Arrival`
- **THEN** only `100%_Real` is returned

#### Scenario: Blank title term browses
- **WHEN** a client sends `GET /api/v1/movies?title=%20%20`
- **THEN** the response status is `200` and every movie in the catalog matches

### Requirement: Genre filter requires every given genre
Each `genre` value SHALL be recognised against the curated genre vocabulary, ignoring letter case. When one or more genres are given, a movie SHALL match only if it carries every one of them. Repeating the same genre, in any letter case, SHALL have the same effect as giving it once. A `genre` value that is not in the vocabulary, including an empty value, SHALL be refused with `400` `BAD_REQUEST`, and the `detail` SHALL name `'genre'`. The refusal SHALL NOT perform the search. Acceptance check: a Testcontainers-backed test with movies tagged `{Drama}`, `{Drama, Sci-Fi}` and `{Sci-Fi}` asserts the matches for `genre=drama`, for `genre=Drama&genre=SCI-FI`, and for `genre=Drama&genre=drama`. A web-slice test, with the mocked use case throwing the unknown-genre failure, asserts that `genre=Western` and `genre=` are refused with `detail` naming `'genre'`. An application-service unit test asserts that the search itself is never invoked for an unknown genre.

#### Scenario: All given genres must be carried
- **WHEN** a client sends `GET /api/v1/movies?genre=Drama&genre=sci-fi`
- **THEN** only movies carrying both Drama and Sci-Fi are returned

#### Scenario: Genre recognised ignoring case
- **WHEN** a client sends `GET /api/v1/movies?genre=drama`
- **THEN** the response status is `200` and every returned movie's `genres` contains `Drama`

#### Scenario: Unknown genre is refused
- **WHEN** a client sends `GET /api/v1/movies?genre=Western` and `Western` is not in the curated vocabulary
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `detail` contains `'genre'` and not `Western`, and no search is performed

### Requirement: Release-year range is inclusive
`releaseYearFrom` and `releaseYearTo` SHALL each be optional integer bounds, and both are inclusive. A movie SHALL match only if its release year is at or after `releaseYearFrom` when that is given, and at or before `releaseYearTo` when that is given. Equal bounds select only that year. When both are given and `releaseYearFrom` is greater than `releaseYearTo`, the request SHALL be refused with `400` `BAD_REQUEST`, and the `detail` SHALL name `'releaseYearFrom'`. A non-integer value SHALL be refused with `400`, and the `detail` SHALL name that parameter. Acceptance check: a Testcontainers-backed test with movies from 1998, 2005 and 2016 asserts the matches for `from=2005`, `to=2005`, `from=2005&to=2005` and `from=1999&to=2016`. A web-slice test asserts the refusal of `releaseYearFrom=2010&releaseYearTo=2000` and `releaseYearTo=abc`.

#### Scenario: Both bounds count
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=1998&releaseYearTo=2005` while movies from 1998, 2005 and 2016 are in the catalog
- **THEN** exactly the 1998 and 2005 movies are returned

#### Scenario: A single year
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=2005&releaseYearTo=2005`
- **THEN** only movies released in 2005 are returned

#### Scenario: Reversed range is refused
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=2010&releaseYearTo=2000`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `detail` contains `'releaseYearFrom'`, and no search is performed

### Requirement: Minimum rating is inclusive and excludes unrated movies
`minRating` SHALL be a number from `0` to `5` inclusive. When it is given, a movie SHALL match only if it has a recorded rating greater than or equal to `minRating`. Movies with no recorded rating SHALL NOT match, even when `minRating` is `0`. A value below `0`, above `5`, or not a number SHALL be refused with `400` `BAD_REQUEST`, and the `detail` SHALL name `'minRating'`. Acceptance check: a Testcontainers-backed test with movies rated `3.0` and `4.5` and one unrated movie asserts the matches for `minRating=4.5`, `minRating=3` and `minRating=0`. A web-slice test asserts the refusal of `-0.1`, `5.1` and `abc`.

#### Scenario: Inclusive minimum
- **WHEN** a client sends `GET /api/v1/movies?minRating=4.5` while movies rated `3.0` and `4.5` are in the catalog
- **THEN** only the movie rated `4.5` is returned

#### Scenario: Unrated movies are left out
- **WHEN** a client sends `GET /api/v1/movies?minRating=0` while the catalog holds one rated and one unrated movie
- **THEN** only the rated movie is returned

#### Scenario: Rating outside the scale is refused
- **WHEN** a client sends `GET /api/v1/movies?minRating=5.1`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` contains `'minRating'`

### Requirement: All criteria combine
When several criteria are given (title, genres, release-year range, minimum rating), a movie SHALL match only if it meets every one of them. Acceptance check: a Testcontainers-backed test in which each of four movies fails exactly one criterion of a combined search, and a fifth meets them all, asserts that only the fifth is returned.

#### Scenario: Every criterion must hold
- **WHEN** a client sends `GET /api/v1/movies?title=night&genre=Thriller&releaseYearFrom=2000&minRating=3` and only one movie meets all four criteria
- **THEN** exactly that movie is returned and `totalElements` is `1`

### Requirement: Results are in a chosen, complete and stable order
`sort` SHALL accept exactly one of these values, which are case-sensitive: `title`, `-title`, `releaseYear`, `-releaseYear`, `rating` or `-rating`. A leading `-` means descending, and no prefix means ascending. Titles SHALL be compared ignoring letter case. When `sort` is absent, the order SHALL be release year descending, then title ascending ignoring case. When ordering by `rating`, movies with no recorded rating SHALL come after every rated movie, in both directions. After the chosen order, the service SHALL always apply a final, fixed tiebreak, so that:
- the same request against the same catalog always returns the same movies in the same order;
- walking every page returns each matching movie exactly once.

The tiebreak is not a consumer-selectable or documented ordering. Any other `sort` value, including an empty one, SHALL be refused with `400` `BAD_REQUEST`, and the `detail` SHALL name `'sort'`. Acceptance check: Testcontainers-backed tests assert:
- the exact order for each of the six values and for the default;
- unrated movies last for `rating` and for `-rating`;
- that ten movies sharing one title and year, walked with `size=3` over four pages, return all ten exactly once and in the same order on a repeat walk.

A web-slice test asserts the refusal of `sort=Title`, `sort=synopsis` and `sort=`.

#### Scenario: Default order
- **WHEN** a client sends `GET /api/v1/movies` while the catalog holds `Arrival` (2016), `Laugh Track` (1998), `Zebra` (2016) and `alpha` (2016)
- **THEN** the order is `alpha`, `Arrival`, `Zebra`, `Laugh Track`

#### Scenario: Rating descending with unrated last
- **WHEN** a client sends `GET /api/v1/movies?sort=-rating` while movies rated `3.0`, `4.5` and one unrated movie are in the catalog
- **THEN** the order is the `4.5` movie, the `3.0` movie, then the unrated movie

#### Scenario: Rating ascending with unrated last
- **WHEN** a client sends `GET /api/v1/movies?sort=rating` with the same catalog
- **THEN** the order is the `3.0` movie, the `4.5` movie, then the unrated movie

#### Scenario: Ties never duplicate or skip across pages
- **WHEN** a client walks `GET /api/v1/movies?sort=title&size=3` over every page while ten movies share the same title
- **THEN** each of the ten movies appears exactly once across the pages, and a second walk returns the same sequence

#### Scenario: Unsupported order is refused
- **WHEN** a client sends `GET /api/v1/movies?sort=synopsis`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `detail` contains `'sort'`, and no search is performed

### Requirement: A search that matches nothing is a success
A valid search that matches no movie, including any search against an empty catalog, SHALL be answered `200` with an empty `data._embedded.movies`, `totalElements` `0`, and the links defined by `platform/collection-paging`. It SHALL NOT be answered `404`. It SHALL be distinguishable from a refused search by status, `Content-Type` and body shape. Acceptance check: a Testcontainers-backed test against an empty catalog, and a no-match `title` search against a non-empty catalog.

#### Scenario: Empty catalog
- **WHEN** a client sends `GET /api/v1/movies` while the catalog holds no movies
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, `meta.pagination.totalElements` is `0`, and `data._links.self` is present

### Requirement: Search internal fault is reported generically
If a search fails because of an unexpected internal fault (for example, the catalog store is unreachable), the service SHALL respond `500` `application/problem+json` with `code` `INTERNAL_ERROR`, `detail` `An unexpected error occurred.`, and the request's `correlationId`. The body SHALL reveal no internal detail. Acceptance check: a web-slice test in which the search throws an exception whose message is `secret-db-host:5432 refused`. It asserts `500`, `INTERNAL_ERROR`, the generic detail, and that `secret-db-host` does not appear in the body.

#### Scenario: Search fails unexpectedly
- **WHEN** a client sends `GET /api/v1/movies` and the search throws `secret-db-host:5432 refused`
- **THEN** the response status is `500`, `code` is `INTERNAL_ERROR`, `detail` is `An unexpected error occurred.`, and the body does not contain `secret-db-host`

### Requirement: Movie search is public and read-only
Searching SHALL require no credentials, and no credential presented SHALL be validated. Searching SHALL NOT change catalog data. `POST`, `PUT`, `PATCH` and `DELETE` on `/api/v1/movies` SHALL respond `405` `application/problem+json` with `code` `METHOD_NOT_ALLOWED` and an `Allow` header that includes `GET`. They SHALL NOT respond `2xx`, `401`, `403` or `5xx`. Acceptance check: a web-slice test sends `GET /api/v1/movies` with `Authorization: Bearer garbage` and asserts `200`. A parameterised web test covers the four write methods, with a JSON body, no credentials and no CSRF token. A Testcontainers-backed test then asserts that `totalElements` is unchanged on a following `GET`.

#### Scenario: Anonymous search
- **WHEN** a client sends `GET /api/v1/movies` with no `Authorization` header
- **THEN** the response status is `200`

#### Scenario: A presented credential is ignored
- **WHEN** a client sends `GET /api/v1/movies` with `Authorization: Bearer garbage`
- **THEN** the response status is `200`, not `401`

#### Scenario: Attempt to add a movie is refused
- **WHEN** a client sends `POST /api/v1/movies` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, `code` is `METHOD_NOT_ALLOWED`, and a following `GET /api/v1/movies` reports the same `totalElements`

### Requirement: Movie search responds in both runtime modes
In standalone mode (no profile) and in persistent mode (`postgres` profile), the service SHALL answer `GET /api/v1/movies`. It SHALL refuse invalid parameters in both modes with the same status, `code` and parameter-naming `detail`. In standalone mode, browsing SHALL return the seeded sample movies. Correctness of matching and ordering is specified by the requirements above, and is verified against PostgreSQL only. Acceptance check: a shared movie-search runtime-mode assertion set, run by both the H2 smoke test and the PostgreSQL integration test, asserts:
- `200` for `GET /api/v1/movies`;
- `400` `BAD_REQUEST` naming `'size'` for `size=0`;
- `400` naming `'genre'` for `genre=Western`.

The H2 smoke test also asserts that browsing returns `200` and that the returned ids include the sample identifiers `11111111-1111-4111-8111-111111111111` and `22222222-2222-4222-8222-222222222222`. It asserts no order and no filter result.

#### Scenario: Standalone browse returns the sample movies
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies`
- **THEN** the response status is `200`, and `data._embedded.movies` includes the sample movies `11111111-1111-4111-8111-111111111111` and `22222222-2222-4222-8222-222222222222`

#### Scenario: Persistent mode refuses invalid paging the same way
- **WHEN** the service is started with the `postgres` profile and a client sends `GET /api/v1/movies?size=0`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` contains `'size'`

#### Scenario: Standalone mode refuses invalid paging the same way
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies?size=0`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, and `detail` contains `'size'`
