## Why

Consumers can only reach a movie today if they already know its identifier (`GET /movies/{id}`, UC-001). UC-002 requires that a consumer discover movies without knowing any id — browsing the catalog and narrowing it by criteria, one ordered, paged portion at a time — so they can build a browsable/searchable movie list in their own app. This adds the collection/search endpoint that has been missing since the catalog was rebuilt to its foundation.

## What Changes

- Add a public, read-only collection endpoint `GET /api/v1/movies` alongside the existing `GET /movies/{id}`, returning an ordered page of **movie summaries** plus paging metadata and HAL navigation links, each summary navigable to its full detail.
- Add optional, conjunctive search criteria: `title` (case-insensitive substring), `genre` (repeatable; a movie must carry **all** supplied), `releaseYearFrom`/`releaseYearTo` (inclusive year range, either bound optional), `minRating` (inclusive `>=` on 0–5, excludes unrated).
- Add sorting by `title`, `releaseYear`, or `rating` (asc/desc); default `releaseYear` desc with `title` asc tiebreak, plus a deterministic terminal id tiebreak so pages never skip or duplicate.
- Add paging: reuse the shared `page` (default 0) / `size` (default 20, max 100) params; report page, size, total count, and next/previous navigation.
- Distinguish outcomes: no criteria browses all; an empty match or a page beyond the last is a normal `200` empty page; invalid criteria (unsupported sort field, out-of-bounds page/size, out-of-range rating, unknown genre) are `400 problem+json`.
- Add new shared OpenAPI schemas (`MovieSummary`, collection envelope, collection links) reusing the existing Envelope/Meta/Pagination/Link/Genre — no per-operation duplicate DTOs.
- Extend `com.acme.catalog.movies` with a search use case, inbound/outbound ports, domain criteria/sort/page value objects, and a persistence adapter with a bounded (non-N+1) query strategy.
- No breaking API change. No DB schema change (reuses `movies` + `movie_genres` from V2).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `catalog/movies`: ADD requirements for searching/browsing the catalog — the collection endpoint, movie summary representation, conjunctive filters, sorting with default + deterministic tiebreak, paging with metadata + HAL links, empty/beyond-last as success, invalid criteria as `400`, and a bounded (no per-row) query guarantee. No existing requirement is modified.

## Impact

- **API:** new operation `getMovies` (`GET /api/v1/movies`), `security: []` (public). New shared schemas in `openapi/components/schemas/movie.yaml` + registrations in `openapi.yaml`; new `paths/movies-collection.yaml`. Regenerate stubs (`openApiGenerate`).
- **Code:** new domain value objects (`MovieSearchCriteria`, `MovieSort`, `MoviePage`), inbound `SearchMoviesUseCase` + service, outbound `SearchMoviesPort` + `MovieSearchPersistenceAdapter`, and the collection handler in `MovieController` (or a sibling controller) with web-only HAL link assembly. Register `/movies` in `PublicEndpoints.PATTERNS`.
- **Data:** none — reuses the existing `movies` and `movie_genres` tables and demo seed.
- **Tests:** per-layer happy/edge/failure, plus an N+1 query-count guard (Hibernate statistics), LIKE-escaping literal-match, and cross-page determinism; keep `GeneratedApiCodegenTest` and the public-endpoint consistency test green.
