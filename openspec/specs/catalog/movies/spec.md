# movies Specification

## Purpose

Lets any API consumer retrieve the curated details of one movie by its stable, opaque catalog identifier, so they can present it to their own end users (UC-001).

## Requirements

### Requirement: Retrieve a movie's details by identifier
The service SHALL offer `GET /api/v1/movies/{id}`. When `{id}` is a well-formed identifier of a movie in the catalog, the service SHALL respond `200` with `Content-Type: application/json`, never `application/problem+json`, even when the request's `Accept` header names only the problem media type. The body SHALL be the uniform success envelope. `data` SHALL hold the movie's details, and `meta` SHALL hold `timestamp` and `correlationId`. `data` SHALL always contain `id`, `title`, `releaseYear` and `genres`. `data.id` SHALL be the movie's identifier in canonical lowercase form. `data._links.self.href` SHALL be the absolute URI of the same movie's details. It SHALL honour forwarded scheme and host, and `data._links` SHALL carry no other relation. Acceptance check: a Testcontainers-backed test inserts a movie with a known identifier and asserts the status, the `Content-Type`, every required member, `self.href`, and that `meta.correlationId` equals the `X-Correlation-Id` header. A second request to `self.href` returns an identical `data`.

#### Scenario: Existing movie is returned
- **WHEN** a client sends `GET /api/v1/movies/6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b` and that movie is in the catalog with title `Arrival`, release year `2016` and genre `Drama`
- **THEN** the response status is `200`, the `Content-Type` is `application/json`, `data.id` is `6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b`, `data.title` is `Arrival`, `data.releaseYear` is `2016`, `data.genres` is `["Drama"]`, and `data._links.self.href` ends with `/api/v1/movies/6f1c2a3b-4d5e-4f60-8a7b-9c0d1e2f3a4b`

#### Scenario: Self link honours forwarded headers
- **WHEN** a client requests an existing movie with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test`
- **THEN** `data._links.self.href` is `https://api.example.test/api/v1/movies/<id>`

#### Scenario: Upper-case identifier is the same movie
- **WHEN** a client requests an existing movie using its identifier in upper-case hexadecimal
- **THEN** the response status is `200` and `data.id` and `self.href` use the canonical lowercase form

#### Scenario: Success is never labelled as a problem
- **WHEN** a client requests an existing movie with `Accept: application/problem+json`
- **THEN** the response status is `200` and the `Content-Type` is `application/json`

### Requirement: Movie details contain exactly the curated movie information
The details SHALL contain only `id`, `title`, `releaseYear`, `genres`, `runtimeMinutes`, `synopsis`, `rating` and `_links`. They SHALL NOT contain keywords, cast, crew, credits, reviews, a vote count, or any link to them. `releaseYear` SHALL be an integer. `runtimeMinutes`, when present, SHALL be a positive integer. `rating`, when present, SHALL be a JSON number from `0` to `5` inclusive: the curated aggregate score. It SHALL be written in its shortest exact decimal form, with no trailing zeros and no exponent, so a rating curated as `5.0` appears as `5` and `4.5` appears as `4.5`. Acceptance check: the success body validates against the served interface description's closed (`additionalProperties: false`) schema. A test asserts the member set exactly for a fully populated movie. Rating tests assert the literal JSON text of `rating`.

#### Scenario: Fully curated movie
- **WHEN** a client requests a movie recorded with runtime `116`, synopsis `A linguist is recruited…` and rating `4.5`
- **THEN** `data.runtimeMinutes` is `116`, `data.synopsis` is `A linguist is recruited…`, `data.rating` is `4.5`, and `data` has no member other than `id`, `title`, `releaseYear`, `genres`, `runtimeMinutes`, `synopsis`, `rating` and `_links`

#### Scenario: Rating at the boundaries of the scale
- **WHEN** a client requests one movie rated `0` and another rated `5`
- **THEN** the raw JSON text of their `data.rating` values is `0` and `5` respectively (not `0.0` or `5.0`)

### Requirement: Unrecorded optional details are absent, never invented
When a movie has no runtime, synopsis or rating recorded, the service SHALL still respond `200` with the movie. It SHALL omit each unrecorded member from `data` entirely. It SHALL NOT present such a member as `null`, `0`, an empty string, or any other default value. Acceptance check: a test inserts a movie with none of the three recorded and asserts `200` and that `runtimeMinutes`, `synopsis` and `rating` are absent keys. A second test records only the rating and asserts that exactly the other two are absent.

#### Scenario: Movie with no optional details
- **WHEN** a client requests a movie with no runtime, synopsis or rating recorded
- **THEN** the response status is `200`, `data` contains `id`, `title`, `releaseYear`, `genres` and `_links`, and `data` has no `runtimeMinutes`, `synopsis` or `rating` key

#### Scenario: Movie with some optional details
- **WHEN** a client requests a movie recorded with a rating of `3` and no runtime or synopsis
- **THEN** `data.rating` is `3` and `data` has no `runtimeMinutes` or `synopsis` key

### Requirement: Genres are listed by name in alphabetical order
`data.genres` SHALL be an array of genre names taken from the curated genre vocabulary. It SHALL NOT contain duplicates. It SHALL be ordered alphabetically by name, ignoring letter case, so the same movie always lists its genres in the same order, whatever order they were curated in. A movie that belongs to no genres SHALL be presented with an empty array, and the response SHALL still be `200`. Acceptance check: a test inserts a movie whose genres were linked in the order `Thriller`, `Drama`, `Sci-Fi` and asserts `["Drama", "Sci-Fi", "Thriller"]` on two consecutive requests. A test with no genres asserts `genres` is `[]`.

#### Scenario: Genres presented alphabetically
- **WHEN** a client requests a movie curated with the genres `Thriller`, `Drama` and `Sci-Fi`, in that order
- **THEN** `data.genres` is `["Drama", "Sci-Fi", "Thriller"]`

#### Scenario: Movie with no genres
- **WHEN** a client requests a movie that belongs to no genres
- **THEN** the response status is `200` and `data.genres` is `[]`

### Requirement: Malformed identifier is refused before any lookup
A movie identifier SHALL be well-formed only when it is a UUID in the canonical 36-character form: 8, 4, 4, 4 and 12 hexadecimal digits separated by hyphens, in any letter case. For any other value, `GET /api/v1/movies/{id}` SHALL respond `400` `application/problem+json` with `code` `BAD_REQUEST`. It SHALL NOT look up any movie. The response SHALL be distinct from the no-such-movie outcome (a different status, `code` and `type`). The problem `detail` SHALL NOT echo the supplied value or name an internal type. Acceptance check: a web-slice test with the movie lookup mocked sends each malformed value. It asserts `400`, `BAD_REQUEST`, the problem members required by `platform/uniform-responses`, and that the lookup was never invoked.

#### Scenario: Not an identifier at all
- **WHEN** a client sends `GET /api/v1/movies/not-a-movie-id`
- **THEN** the response status is `400`, the body is `application/problem+json` with `code` `BAD_REQUEST`, and no movie lookup is performed

#### Scenario: Non-canonical UUID-like value
- **WHEN** a client sends `GET /api/v1/movies/1-1-1-1-1`, and separately `GET /api/v1/movies/6f1c2a3b4d5e4f608a7b9c0d1e2f3a4b` (no hyphens)
- **THEN** both responses have status `400` and `code` `BAD_REQUEST`, and no movie lookup is performed

#### Scenario: Numeric sequence-style value
- **WHEN** a client sends `GET /api/v1/movies/123`
- **THEN** the response status is `400` with `code` `BAD_REQUEST`

### Requirement: Unknown movie is reported as not found
When `{id}` is well-formed but no movie in the catalog has that identifier, the service SHALL respond `404` `application/problem+json` with `code` `NOT_FOUND`. It SHALL NOT respond with an empty or placeholder movie. Acceptance check: a Testcontainers-backed test requests a random well-formed UUID against a catalog that does not contain it. It asserts `404`, `NOT_FOUND`, the required problem members, no `data` member, and that `type` differs from the `400` outcome's `type`.

#### Scenario: No such movie
- **WHEN** a client sends `GET /api/v1/movies/00000000-0000-4000-8000-000000000000` and no movie has that identifier
- **THEN** the response status is `404`, the body is `application/problem+json` with `code` `NOT_FOUND`, and the body has no `data` or `_links` member

### Requirement: Internal fault is reported generically
If retrieving a movie fails because of an unexpected internal fault (for example, the catalog store is unreachable), the service SHALL respond `500` `application/problem+json` with `code` `INTERNAL_ERROR`, `detail` `An unexpected error occurred.`, and the request's `correlationId`. The body SHALL reveal no internal detail. The server log SHALL record the fault with the same correlation id. Acceptance check: a web-slice test in which the movie lookup throws an exception whose message is `secret-db-host:5432 refused`. It asserts `500`, `INTERNAL_ERROR`, the generic detail, and that `secret-db-host` does not appear in the body.

#### Scenario: Lookup fails unexpectedly
- **WHEN** a client requests a well-formed identifier and the catalog lookup throws `secret-db-host:5432 refused`
- **THEN** the response status is `500`, `code` is `INTERNAL_ERROR`, `detail` is `An unexpected error occurred.`, and the body does not contain `secret-db-host`

### Requirement: Movie details are public and read-only
Retrieving a movie's details SHALL require no credentials, and no credential presented SHALL be validated. `POST`, `PUT`, `PATCH` and `DELETE` on `/api/v1/movies/{id}` SHALL respond `405` `application/problem+json` with `code` `METHOD_NOT_ALLOWED` and an `Allow` header that includes `GET`. They SHALL NOT respond `2xx`, `401`, `403` or `5xx`, and they SHALL leave the catalog unchanged. Acceptance check: a parameterised web test over the four write methods, with a JSON body, no credentials and no CSRF token, asserts `405`, the `Allow` header and `METHOD_NOT_ALLOWED`. A Testcontainers-backed test then asserts that a following `GET` of an existing movie returns the same `data`.

#### Scenario: Anonymous retrieval
- **WHEN** a client requests an existing movie with no `Authorization` header
- **THEN** the response status is `200`

#### Scenario: Attempt to change a movie is refused
- **WHEN** a client sends `PUT /api/v1/movies/<existing id>` or `DELETE /api/v1/movies/<existing id>` with no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, the `code` is `METHOD_NOT_ALLOWED`, and a following `GET` returns the movie unchanged

### Requirement: Movie details behave identically in both runtime modes
In standalone mode (no profile) and in persistent mode (`postgres` profile), the service SHALL apply the movie catalog schema at start-up and answer `GET /api/v1/movies/{id}` the same way for the same catalog contents: the same status, `Content-Type` and body structure for the not-found and malformed-identifier outcomes. The only difference between the modes is the sample movies described in "Standalone mode offers sample movies". Acceptance check: a shared movie runtime-mode assertion set, run by both the single H2 smoke test and the PostgreSQL integration test, asserts `404` `NOT_FOUND` for a random well-formed identifier and `400` `BAD_REQUEST` for `not-a-movie-id`.

#### Scenario: Standalone start serves movie lookups
- **WHEN** the service is started with no profile, and a client requests a random well-formed movie identifier
- **THEN** the response status is `404` with `code` `NOT_FOUND`, not `500`

#### Scenario: Persistent start serves movie lookups
- **WHEN** the service is started with the `postgres` profile, and a client requests a random well-formed movie identifier
- **THEN** the response status is `404` with `code` `NOT_FOUND`

### Requirement: Standalone mode offers sample movies
So that an evaluator can see successful answers without curating data, standalone mode (no profile) SHALL start with a small fixed set of sample movies. Their identifiers SHALL be fixed and published in the interface description's operation `description`. The set SHALL include at least:
- `11111111-1111-4111-8111-111111111111`: a movie with runtime, synopsis, rating and at least two genres.
- `22222222-2222-4222-8222-222222222222`: a movie with no runtime, synopsis or rating recorded and no genres.

Persistent mode (`postgres` profile) SHALL NOT contain the sample movies. Its catalog holds only curated data. They SHALL be re-created identically at every standalone start. Acceptance check, primary: a plain unit test loads the configuration without booting the service and makes two assertions. The persistent-mode configuration's schema-migration locations are exactly the schema location, with no sample-data location. The standalone configuration's locations include the sample-data location. Tests that boot against PostgreSQL override the migration locations, so they cannot prove the persistent configuration excludes the samples. Acceptance check, standalone: the single H2 smoke test requests both sample identifiers. It asserts `200`, all optional members and a non-empty `genres` for the first. It asserts `genres` `[]` and no `runtimeMinutes`, `synopsis` or `rating` for the second. Secondary check: the PostgreSQL integration test with the `postgres` profile asserts `404` `NOT_FOUND` for the first sample identifier.

#### Scenario: Fully curated sample movie in standalone mode
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies/11111111-1111-4111-8111-111111111111`
- **THEN** the response status is `200` and `data` contains `runtimeMinutes`, `synopsis`, `rating` and at least two genres in alphabetical order

#### Scenario: Minimal sample movie in standalone mode
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies/22222222-2222-4222-8222-222222222222`
- **THEN** the response status is `200`, `data.genres` is `[]`, and `data` has no `runtimeMinutes`, `synopsis` or `rating` key

#### Scenario: No sample movies in persistent mode
- **WHEN** the persistent-mode (`postgres` profile) configuration is loaded
- **THEN** its schema-migration locations are exactly `classpath:db/migration` and do not include `classpath:db/demo`, so a client requesting `GET /api/v1/movies/11111111-1111-4111-8111-111111111111` against a curated database without that movie gets `404` `NOT_FOUND`

### Requirement: Search and browse movies a page at a time
The service SHALL offer `GET /api/v1/movies`. Every query parameter is optional: `title`, `genre` (repeatable), `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`, `page` and `size`.

**Success response.** When the request is allowed, the service SHALL respond `200` with `Content-Type: application/json`, whatever the `Accept` header. The body SHALL be the uniform success envelope in the paged list form defined by `platform/uniform-responses`.

**Body layout.**
- `data._embedded.movies` SHALL be the array of movie summaries on the requested page, in the requested order.
- `data._links` SHALL hold the page's navigation links.
- `meta` SHALL hold `timestamp`, `correlationId` and `pagination`.

**Matching and defaults.**
- With no criteria, every movie in the catalog SHALL match.
- When several criteria are given, a movie SHALL match only when it meets all of them.
- When `page` is absent, it SHALL default to `0`, the first page.
- When `size` is absent, it SHALL default to `20`.

Acceptance check: a Testcontainers-backed test inserts 25 movies and requests `GET /api/v1/movies` with no parameters. It asserts:
- the status and the `Content-Type`;
- 20 entries in `data._embedded.movies`;
- `meta.pagination` equal to `{page: 0, size: 20, totalElements: 25, totalPages: 2}`;
- `meta.correlationId` equal to the `X-Correlation-Id` header.

#### Scenario: Browse the whole catalog
- **WHEN** the catalog holds 25 movies and a client sends `GET /api/v1/movies`
- **THEN** the response status is `200`, the `Content-Type` is `application/json`, `data._embedded.movies` has 20 entries, and `meta.pagination` is `page` `0`, `size` `20`, `totalElements` `25`, `totalPages` `2`

#### Scenario: Criteria combine to narrow
- **WHEN** the catalog holds `Arrival` (2016, Drama and Sci-Fi, rated 4.5), `Arrival Point` (1999, Drama, rated 4.5) and `Contact` (1997, Drama and Sci-Fi, rated 4), and a client sends `GET /api/v1/movies?title=arrival&genre=Sci-Fi&releaseYearFrom=2000&minRating=4`
- **THEN** `data._embedded.movies` contains exactly `Arrival` and `meta.pagination.totalElements` is `1`

#### Scenario: Search results are never labelled as a problem
- **WHEN** a client sends `GET /api/v1/movies` with `Accept: application/problem+json`
- **THEN** the response status is `200` and the `Content-Type` is `application/json`

### Requirement: Search results are movie summaries
**Members.** Each entry in `data._embedded.movies` SHALL contain only `id`, `title`, `releaseYear`, `genres`, `runtimeMinutes`, `rating` and `_links`.
- It SHALL NOT contain `synopsis`, keywords, credits, reviews or a vote count.
- `id`, `title`, `releaseYear`, `genres` and `_links` SHALL always be present.

**Optional details.** `runtimeMinutes` and `rating` SHALL follow the same rules as movie details:
- `runtimeMinutes` and `rating` SHALL be omitted entirely when not recorded. They SHALL never be `null` or `0` in place of an unrecorded value.
- `rating` SHALL be written in its shortest exact decimal form.

**Genres.** `genres` SHALL list genre names alphabetically, ignoring letter case. It SHALL be `[]` for a movie with no genres.

**Links.**
- `_links.self.href` SHALL be the absolute URI of that movie's details, `/api/v1/movies/{id}`. It SHALL honour forwarded scheme and host.
- `_links` SHALL carry no other relation.

Acceptance check:
- The success body validates against the served closed schema.
- A Testcontainers-backed test asserts the exact member set for a fully curated movie, and that a movie with no runtime, rating or genres has no `runtimeMinutes` or `rating` key and has `genres` `[]`.
- A `GET` of each entry's `self.href` returns `200` with the same `id`.

#### Scenario: Summary omits the synopsis
- **WHEN** a movie with runtime `116`, synopsis `A linguist is recruited…`, rating `4.5` and genres `Sci-Fi`, `Drama` matches a search
- **THEN** its entry has `runtimeMinutes` `116`, `rating` `4.5`, `genres` `["Drama", "Sci-Fi"]`, no `synopsis` key, and `_links.self.href` ending with `/api/v1/movies/<its id>`

#### Scenario: Summary of a minimally curated movie
- **WHEN** a movie with no runtime, rating or genres matches a search
- **THEN** its entry is present, has `genres` `[]`, and has no `runtimeMinutes` or `rating` key

### Requirement: Title term matches any part of the title, ignoring case
When `title` is given and is not blank, a movie SHALL match only if the term appears as a contiguous substring of its title, ignoring letter case.

Matching rules:
- The characters `%`, `_` and `\` in the term SHALL match themselves literally. They are not wildcards.
- The term SHALL be used as given, without trimming.
- An empty or whitespace-only `title` SHALL be treated as no title criterion. It SHALL NOT be refused.

Acceptance check: a Testcontainers-backed parameterised test over the scenarios below.

#### Scenario: Case-insensitive substring match
- **WHEN** the catalog holds `Arrival`, `The Arrival of a Train` and `Contact`, and a client sends `GET /api/v1/movies?title=ARRIV`
- **THEN** exactly `Arrival` and `The Arrival of a Train` are returned

#### Scenario: Wildcard characters are literal
- **WHEN** the catalog holds `100% Wolf` and `1000 Wolves`, and a client sends `GET /api/v1/movies?title=0%25%20W` (the term `0% W`)
- **THEN** exactly `100% Wolf` is returned

#### Scenario: Blank title term browses
- **WHEN** a client sends `GET /api/v1/movies?title=%20%20`
- **THEN** the response status is `200` and `meta.pagination.totalElements` equals the number of movies in the catalog

### Requirement: Genre filter requires every given genre
**Matching.** Each `genre` value SHALL name a genre in the curated vocabulary, matched ignoring letter case.
- When one or more genres are given, a movie SHALL match only if it carries every one of them.
- Repeating the same genre, in any letter case, SHALL be the same as giving it once.
- A comma-separated value, for example `genre=Drama,Sci-Fi`, SHALL be treated as the same as repeating the parameter.
- Empty or whitespace-only `genre` values SHALL be ignored.

**Unknown genre.** A non-blank value that names no curated genre SHALL be refused as described in "Search asked in a way that isn't allowed is refused before searching".

Acceptance check: a Testcontainers-backed test over the scenarios below.

#### Scenario: Several genres mean all of them
- **WHEN** `Arrival` carries Drama and Sci-Fi, and `Contact` carries Sci-Fi only, and a client sends `GET /api/v1/movies?genre=Drama&genre=Sci-Fi`
- **THEN** exactly `Arrival` is returned

#### Scenario: Genre recognised ignoring case
- **WHEN** a client sends `GET /api/v1/movies?genre=drama`
- **THEN** the response is the same set of movies as for `genre=Drama`

#### Scenario: Movie with no genres never matches a genre filter
- **WHEN** a movie with no genres exists and a client sends `GET /api/v1/movies?genre=Drama`
- **THEN** that movie is not returned

### Requirement: Release-year range filter is inclusive
`releaseYearFrom` and `releaseYearTo` SHALL each be an integer.

Matching rules:
- When `releaseYearFrom` is given, a movie SHALL match only if its release year is at least that year.
- When `releaseYearTo` is given, a movie SHALL match only if its release year is at most that year.
- Either bound may be given alone.
- Giving both bounds equal SHALL select exactly that year.
- A lower bound after the upper bound SHALL be refused.

Acceptance check: a Testcontainers-backed test with movies from 1997, 1999, 2000 and 2016 over the scenarios below.

#### Scenario: Closed range includes both bounds
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=1999&releaseYearTo=2000`
- **THEN** exactly the 1999 and 2000 movies are returned

#### Scenario: Single year
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=2016&releaseYearTo=2016`
- **THEN** exactly the 2016 movie is returned

#### Scenario: Open-ended range
- **WHEN** a client sends `GET /api/v1/movies?releaseYearTo=1999`
- **THEN** exactly the 1997 and 1999 movies are returned

### Requirement: Minimum-rating filter is inclusive and leaves out unrated movies
When `minRating` is given, a movie SHALL match only if it has a recorded rating greater than or equal to `minRating`. Movies with no recorded rating SHALL NOT match. `minRating` SHALL be a decimal number from `0` to `5` inclusive. Acceptance check: a Testcontainers-backed test with movies rated `4`, `4.5`, `3.5` and one unrated, over the scenarios below.

#### Scenario: Inclusive lower bound
- **WHEN** a client sends `GET /api/v1/movies?minRating=4`
- **THEN** exactly the movies rated `4` and `4.5` are returned

#### Scenario: Zero minimum still excludes unrated movies
- **WHEN** a client sends `GET /api/v1/movies?minRating=0`
- **THEN** every rated movie is returned and the unrated movie is not

### Requirement: Results are in a complete, stable order the client chooses
`sort` SHALL accept exactly the following six values. No other value is accepted.

| `sort` value | Order |
|---|---|
| `title` | Title A–Z, ignoring letter case |
| `-title` | Title Z–A, ignoring letter case |
| `releaseYear` | Release year, oldest first |
| `-releaseYear` | Release year, newest first |
| `rating` | Rating, lowest first |
| `-rating` | Rating, highest first |

**Default order.** When `sort` is absent, the order SHALL be the same as `-releaseYear`: release year newest first, then title A–Z.

**Ties.** For the `releaseYear` and `rating` orders, movies equal on the primary key SHALL be ordered by title A–Z. Every order SHALL end with a final tiebreak, which makes the order complete. As a result, repeating the same request lists the same movies in the same order. Walking all pages lists every match exactly once.

**Unrated movies.** When ordering by `rating` or `-rating`, movies with no recorded rating SHALL come after all rated movies.

Acceptance check: a Testcontainers-backed test covers each of the six values and asserts the full id sequence. It also walks every page at `size=2` over a catalog containing equal years, equal ratings and equal titles. It asserts that the concatenated pages equal the single-page `size=100` result, with no duplicates and none missing.

#### Scenario: Default order
- **WHEN** the catalog holds `Zodiac` (2007), `Arrival` (2016) and `Atonement` (2007), and a client sends `GET /api/v1/movies`
- **THEN** the order is `Arrival`, `Atonement`, `Zodiac`

#### Scenario: Unrated movies last in both directions
- **WHEN** the catalog holds movies rated `3`, `4.5` and one unrated, and a client sends `sort=rating` and then `sort=-rating`
- **THEN** the orders are `3`, `4.5`, unrated and `4.5`, `3`, unrated respectively

#### Scenario: Pages neither repeat nor skip movies
- **WHEN** a client walks every page of `GET /api/v1/movies?sort=releaseYear&size=2` over a catalog with several movies sharing a release year and a title
- **THEN** every movie appears exactly once across the pages, in the same order as `GET /api/v1/movies?sort=releaseYear&size=100`

### Requirement: Paging reports totals and tolerates pages after the last
**Page bounds.** `page` SHALL be a zero-based integer of at least `0`. `size` SHALL be an integer from `1` to `100`.

**Pagination member.** `meta.pagination` SHALL report:
- `page`: the requested page;
- `size`: the effective page size;
- `totalElements`: the number of matches across all pages;
- `totalPages`: `totalElements` divided by `size`, rounded up, so it is `0` when nothing matches.

**Page after the last.** A `page` at or after `totalPages` SHALL be a `200` with an empty `data._embedded.movies`. It SHALL still report `totalElements` and `totalPages`, and still point to the first and last pages.

Acceptance check: a Testcontainers-backed test with 5 matches asserts the following:

| Request | Entries | `totalPages` |
|---|---|---|
| `size=2&page=2` | 1 | 3 |
| `size=2&page=9` | 0 | 3 |
| `size=100` | 5 | 1 |

#### Scenario: Last partial page
- **WHEN** 5 movies match and a client sends `GET /api/v1/movies?size=2&page=2`
- **THEN** `data._embedded.movies` has 1 entry and `meta.pagination` is `page` `2`, `size` `2`, `totalElements` `5`, `totalPages` `3`

#### Scenario: Page after the last is empty, not refused
- **WHEN** 5 movies match and a client sends `GET /api/v1/movies?size=2&page=9`
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, `meta.pagination.totalElements` is `5`, and `data._links` has `first` and `last` but no `next` or `prev`

### Requirement: Page links keep the criteria, order and size
**Relations.** `data._links` SHALL carry `self`, `first` and `last`.
- It SHALL carry `prev` only when `page` is greater than `0` and not after the last page.
- It SHALL carry `next` only when `page` is before the last page.
- `first` SHALL be page `0`.
- `last` SHALL be page `totalPages − 1`, or page `0` when nothing matches.

**What each href carries.** Every href SHALL be absolute, honouring forwarded scheme and host. It SHALL target `/api/v1/movies` and carry every recognised parameter of the request (`title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`, `size`), with its values exactly as given.
- `self` SHALL carry `page` exactly as the request did, including omitting it when the request omitted it.
- The other links SHALL set `page` to their target page.
- A parameter absent from the request SHALL be absent from every link. A default SHALL NOT be written into a link.
- Unrecognised parameters SHALL NOT appear in any link.

**Navigation only.** The links SHALL only navigate. They SHALL NOT describe any action that changes data.

Acceptance check: a web test parses each href's query string and asserts its parameter multimap. A Testcontainers-backed test follows `next` until it is absent, and asserts that each followed page carries the same `meta.pagination.totalElements`.

#### Scenario: Links preserve criteria and order
- **WHEN** 45 movies match and a client sends `GET /api/v1/movies?title=the&genre=Drama&genre=Sci-Fi&sort=-rating&size=10&page=1`
- **THEN** `first`, `prev`, `next` and `last` carry `title=the`, both `genre` values, `sort=-rating` and `size=10`, with `page` `0`, `0`, `2` and `4` respectively, and `self` carries `page=1`

#### Scenario: Defaults are not written into links
- **WHEN** a client sends `GET /api/v1/movies?genre=Drama` and 25 movies match
- **THEN** no link carries `size` or `sort`, `self` carries no `page`, and `next` and `last` carry `page=1`

#### Scenario: Unrecognised parameters are dropped from links
- **WHEN** a client sends `GET /api/v1/movies?foo=bar&title=a`
- **THEN** the response status is `200` and no link carries `foo`

#### Scenario: Empty result still navigable
- **WHEN** nothing matches a search
- **THEN** `data._links` has `self`, `first` and `last`, `first` and `last` both carry `page=0`, and there is no `prev` or `next`

### Requirement: Search asked in a way that isn't allowed is refused before searching
The service SHALL respond `400` `application/problem+json` with `code` `BAD_REQUEST` when any of the following holds:

| Request | Refused when |
|---|---|
| `sort` | not one of the six supported values (comparison is case-sensitive) |
| `page` | below `0`, or not an integer |
| `size` | below `1`, above `100`, or not an integer |
| `genre` | a non-blank value names no curated genre |
| release-year range | `releaseYearFrom` is greater than `releaseYearTo` |
| `releaseYearFrom` or `releaseYearTo` | not an integer |
| `minRating` | below `0`, above `5`, or not a number |

When a search is refused:
- It SHALL NOT search the catalog.
- The problem `detail` SHALL name the parameter at fault, as required by `platform/uniform-responses`. It SHALL NOT echo the supplied value.
- The refusal SHALL be distinct from a search that matches nothing (`200`) and from an internal fault (`500`).

Acceptance check, web slice: a parameterised test with the search use case mocked sends each refused case. It asserts:
- `400` and `BAD_REQUEST`;
- the required problem members;
- the expected parameter name in `detail`;
- that the supplied value is absent from `detail`;
- for framework-detected cases, that the search use case was never invoked.

Acceptance check, application: a unit test asserts that the search port is never invoked for an unknown genre or a reversed range.

#### Scenario: Unknown genre
- **WHEN** a client sends `GET /api/v1/movies?genre=Spaghetti`
- **THEN** the response status is `400`, `code` is `BAD_REQUEST`, `detail` names `genre`, and `detail` does not contain `Spaghetti`

#### Scenario: Page size out of bounds
- **WHEN** a client sends `GET /api/v1/movies?size=101`, and separately `size=0` and `page=-1`
- **THEN** each response has status `400` and `code` `BAD_REQUEST` (never `500`), and `detail` names `size`, `size` and `page` respectively

#### Scenario: Unsupported order
- **WHEN** a client sends `GET /api/v1/movies?sort=popularity`, and separately `sort=TITLE`
- **THEN** both responses have status `400`, `code` `BAD_REQUEST`, and `detail` naming `sort`

#### Scenario: Reversed year range
- **WHEN** a client sends `GET /api/v1/movies?releaseYearFrom=2010&releaseYearTo=2000`
- **THEN** the response status is `400` with `code` `BAD_REQUEST` and `detail` naming `releaseYearFrom`

#### Scenario: Rating outside the scale or ill-typed
- **WHEN** a client sends `GET /api/v1/movies?minRating=5.5`, and separately `minRating=-1` and `minRating=high`
- **THEN** each response has status `400`, `code` `BAD_REQUEST`, and `detail` naming `minRating`

### Requirement: A search that matches nothing is a success
A search that is allowed but matches no movie, including a search of an empty catalog, SHALL respond `200`. The response SHALL have an empty `data._embedded.movies`, `meta.pagination.totalElements` `0`, and `totalPages` `0`. It SHALL NOT be reported as `404`. Acceptance check: a Testcontainers-backed test against an empty catalog, and another with a title matching nothing. Both assert `200`, `_embedded.movies` `[]` and the totals.

#### Scenario: Empty catalog
- **WHEN** the catalog holds no movies and a client sends `GET /api/v1/movies`
- **THEN** the response status is `200`, `data._embedded.movies` is `[]`, and `meta.pagination` has `totalElements` `0` and `totalPages` `0`

#### Scenario: No movie matches
- **WHEN** a client sends `GET /api/v1/movies?title=zzzz-no-such-title`
- **THEN** the response status is `200` and `data._embedded.movies` is `[]`

### Requirement: Search internal fault is reported generically
If a search fails because of an unexpected internal fault, the service SHALL respond `500` `application/problem+json` with:
- `code` `INTERNAL_ERROR`;
- `detail` `An unexpected error occurred.`;
- the request's `correlationId`.

The body SHALL reveal no internal detail, and the fault SHALL be logged with the same correlation id. Acceptance check: a web-slice test in which the search use case throws `secret-db-host:5432 refused`. It asserts `500`, `INTERNAL_ERROR`, the generic detail, and that the body does not contain `secret-db-host`.

#### Scenario: Search fails unexpectedly
- **WHEN** a client sends `GET /api/v1/movies` and the search throws `secret-db-host:5432 refused`
- **THEN** the response status is `500`, `code` is `INTERNAL_ERROR`, and the body does not contain `secret-db-host`

### Requirement: Search is public and read-only
Searching SHALL require no credentials, and no credential presented SHALL be validated.

`POST`, `PUT`, `PATCH` and `DELETE` on `/api/v1/movies` SHALL respond `405` `application/problem+json` with `code` `METHOD_NOT_ALLOWED` and an `Allow` header that includes `GET`. They SHALL leave the catalog unchanged.

Acceptance check:
- A parameterised web test over the four write methods, sent with a JSON body and no credentials, asserts `405`, the `Allow` header and the `code`.
- A Testcontainers-backed test asserts that a following search returns the same `totalElements`.

#### Scenario: Anonymous search
- **WHEN** a client sends `GET /api/v1/movies` with no `Authorization` header
- **THEN** the response status is `200`

#### Scenario: Attempt to add a movie is refused
- **WHEN** a client sends `POST /api/v1/movies` with a JSON body and no credentials
- **THEN** the response status is `405`, the `Allow` header includes `GET`, the `code` is `METHOD_NOT_ALLOWED`, and a following search reports the same `totalElements`

### Requirement: Search behaves identically in both runtime modes
In standalone mode and in persistent mode, `GET /api/v1/movies` SHALL answer the same way for the same catalog contents. In standalone mode, browsing SHALL return the sample movies.

Acceptance check:
- The shared `MovieRuntimeModeAssertions` adds two checks, run by both mode tests:
  - `GET /api/v1/movies?genre=Spaghetti` is `400` `BAD_REQUEST`;
  - `GET /api/v1/movies?title=zzzz-no-such-title` is `200` with an empty list.
- `H2DefaultRuntimeSmokeTest` asserts that `GET /api/v1/movies` lists the four sample movies in the default order.

#### Scenario: Standalone browse lists the sample movies
- **WHEN** the service is started with no profile and a client sends `GET /api/v1/movies`
- **THEN** the response status is `200`, `meta.pagination.totalElements` is `4`, and the titles are, in order, `Arrival` (2016), `The Grand Heist` (2005), `Laugh Track` (1998), `Untitled Reel` (1974)

#### Scenario: Persistent mode refuses an unknown genre identically
- **WHEN** the service is started with the `postgres` profile and a client sends `GET /api/v1/movies?genre=Spaghetti`
- **THEN** the response status is `400` with `code` `BAD_REQUEST`
