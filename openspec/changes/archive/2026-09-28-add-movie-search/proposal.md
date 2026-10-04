## Why

API consumers can retrieve a movie only if they already know its identifier (UC-001). They cannot find movies by title, narrow them by genre, year or rating, or browse the catalog. UC-002 adds search and browse. It also fixes the paging convention that every later list capability (people, genres, keywords, reviews) will reuse, so the convention must be defined once, at platform level, now.

## What Changes

- New read-only operation `GET /api/v1/movies` (`searchMovies`). Every query parameter is optional: `title`, `genre` (repeatable), `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`, `page`, `size`.
- Results are movie summaries (UC-002 BR-2): no synopsis, and each summary links to its UC-001 details. They sit in `data._embedded.movies`. Counts go in `meta.pagination`. Navigation links (`self`, `first`, `last`, `prev`, `next`) go in `data._links` and keep the criteria, order and size.
- Default order is release year newest first, then title A–Z, then movie identifier. The identifier is always the final tiebreak, which makes paging stable.
- A search asked in a way that isn't allowed (UC-002 BR-10) gets `400` `BAD_REQUEST`, and the problem `detail` names the offending query parameter. A search that matches nothing, or a page after the last, is `200` with an empty page.
- New platform capability, `collection-paging`. It holds the reusable convention: `page`/`size` parameters (0-based page, default size 20, maximum 100), `meta.pagination`, link rules, empty and beyond-last pages, and parameter-naming refusals.
- Shared contract additions, all additive: an optional `Meta.pagination`, reusable `page`/`size` parameters, and a `Pagination` schema.
- `POST`/`PUT`/`PATCH`/`DELETE` on `/movies` are refused with `405`.
- No breaking API change. The existing operations are unchanged, and `meta.pagination` never appears on them. No DB schema change and no new migration.

## Non-goals

- Keyword, cast, crew or person filters (UC-003…UC-007). Free-text, synopsis, fuzzy or relevance search.
- "Any of these genres" matching. Listing the genre vocabulary.
- Localised titles, rate limiting, and any write to the catalog.
- Cursor/keyset paging, and total-free (count-less) paging.
- Making genre names unique ignoring case at the DB level. See design's open questions.

## Capabilities

### New Capabilities
- `platform/collection-paging`: the paging convention every collection operation reuses. It covers the `page`/`size` parameters and bounds, `meta.pagination`, criteria-preserving navigation links, empty and beyond-last pages, and refusals that name the invalid parameter.

### Modified Capabilities
- `catalog/movies`: adds searching and browsing movies (`GET /movies`). This covers the summary shape, the title, genre, year and rating filters, ordering, validation, empty results, failure, read-only behaviour and runtime-mode parity.
- `platform/uniform-responses`: the read-only refusal requirement adds `/movies` to the offered catalog paths that answer writes with `405`.

## Impact

- OpenAPI: `paths/movies.yaml` (new `movies` path item), `components/schemas/movie.yaml` (collection and summary schemas), `components/schemas/common.yaml` (`Pagination`, optional `Meta.pagination`), new `components/parameters/common.yaml`, and `openapi.yaml`.
- Java: new domain types under `catalog.movies.domain` and `shared.domain`, a new use case and ports, persistence adapter queries, a new controller method, and a parameter-naming `400` in `GlobalExceptionHandler`/`ProblemFactory`.
- Tests: domain, application and web-slice tests, Testcontainers adapter and end-to-end tests, and H2 smoke plus Postgres runtime-mode assertions.
