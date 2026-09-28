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
