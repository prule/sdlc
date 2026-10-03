## Why

UC-002: an API consumer needs to *find* movies, by title, genre, release year or rating, or by browsing the whole catalog. UC-001 only helps a consumer who already holds a movie's identifier. Search is also the first paged list, so it sets the paging convention that later lists (filmography, people search) reuse (BR-7).

## What Changes

- Add a public, read-only operation, `GET /api/v1/movies`. It returns one page of **movie summaries** in the success envelope. A summary has the identifier, title, release year, genres (A–Z), runtime and rating when recorded, and a `self` link to the movie's details. It has **no synopsis**.
- Optional criteria, combined with AND: `title` (case-insensitive "contains"; blank means no criterion), `genre` (repeatable, the movie must carry all of them, case-insensitive, curated vocabulary only), `releaseYearFrom` / `releaseYearTo` (inclusive range), and `minRating` (inclusive, 0–5; leaves out unrated movies).
- Ordering by `sort` (`title`, `releaseYear` or `rating`, with a `-` prefix for descending). The default is release year newest first, then title A–Z. Every order ends with a stable tiebreak. Unrated movies always sort last.
- Paging by `page` (0-based) and `size` (default 20, at most 100). The paging counts go in `meta.pagination`. `data._links` holds `self`, `first`, `last`, and `prev`/`next` where they exist. Every link keeps the request's criteria, order and size exactly as they were given.
- No matches, and a page after the last, are `200` with an empty list (BR-8).
- A search asked in a way that isn't allowed gets `400 BAD_REQUEST`, and nothing is searched. This covers an unknown `sort`, `page < 0`, `size` outside 1–100, an unknown genre, `releaseYearFrom > releaseYearTo`, `minRating` outside 0–5, and an ill-typed value. The problem `detail` names the parameter at fault, never its value.
- **Platform convention (new):** a paged list form (`meta.pagination` plus navigation links) that every later list reuses. Any `400` caused by a single request parameter now names that parameter in `detail`. That includes UC-001's malformed identifier: the wording changes, the status and code do not. A request parameter that violates its declared bounds is always `400`, never `500`.
- Write methods on `/movies` are refused with `405`.

No breaking API change. The operation is new. `Meta` gains an *optional* `pagination` member, which existing responses never carry. The `400` `detail` text changes, but `detail` was never a stable contract field: `code` and `type` are, and they are unchanged. No DB schema change.

## Non-goals

- Keyword, cast, crew or person filters (UC-003…UC-007). Listing the genre vocabulary (UC-007).
- Searching synopsis or free text. Fuzzy, misspelling-tolerant or relevance-ranked matching.
- "Any of these genres" matching. Localised titles.
- Search-specific indexes or full-text infrastructure (for example `pg_trgm`). The catalog is curated and modest in size.
- Rate limiting (still a TODO in `domain/business-rules.md`).
- Any change to `GET /movies/{id}` beyond the `400` detail wording.
- A maximum length for the title term (left as an open question in design.md).
- Creating, correcting or removing movies.

## Capabilities

### New Capabilities
<!-- none: search extends the existing catalog/movies capability -->

### Modified Capabilities
- `catalog/movies`: ADDED requirements for search and browse. These cover the summary content, each criterion, ordering, paging, navigation links, refusals, no-match success, internal fault, read-only access and both runtime modes.
- `platform/uniform-responses`: MODIFIED "Uniform success envelope", so that `meta` may carry `pagination` on paged lists. MODIFIED "Service is read-only and public", to add `/movies` to the offered paths that answer writes with `405`. ADDED "Paged lists follow one navigable convention" and "Refused parameter is named, never echoed".
- `platform/interface-description`: MODIFIED "Shared concepts defined once", to add `Pagination`, `PageLinks` and the shared `page`/`size` parameters to the shared concepts.

## Impact

- **API:** `paths/movies.yaml` gains `movies` (`searchMovies`). New `components/parameters/common.yaml` (`page`, `size`). `components/schemas/common.yaml` gains `Pagination` and `PageLinks` (shared by every paged list), and `Meta` gains an optional `pagination`. `components/schemas/movie.yaml` gains `MovieSearchEnvelope`, `MovieSearchPage`, `MovieSearchEmbedded` and `MovieSummary`. The shared `BadRequest` response description mentions an invalid request parameter. `MoviesApi` gains `searchMovies`.
- **Code:** a new search slice in `com.acme.catalog.movies` (search criteria and sort value objects, `SearchMoviesUseCase`, two outbound ports, a JPA Criteria search adapter, a `@Validated` controller method and movie link parameters). New shared-kernel types for every later paged list: `InvalidRequestException`, `PageSpec` and `ResultPage<T>`. Platform changes: a reusable `PageLinkBuilder`; `GlobalExceptionHandler` maps `InvalidRequestException`, controller-parameter `ConstraintViolationException` and `HandlerMethodValidationException` to a named `400` (other constraint violations stay `500`); `ProblemFactory` gains a parameter-naming `400`; `ResponseMetaFactory` gains a paged overload.
- **DB:** none (no migration). The existing standalone seed is reused.
- **Tests:** `ReadOnlyRefusalTest`, `InterfaceDescriptionContractTest`, `GeneratedApiCodegenTest`, `FailureKindsTest`, `MovieRuntimeModeAssertions` and `H2DefaultRuntimeSmokeTest` are extended.
