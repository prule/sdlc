## Why

CAT-001 shipped single-movie detail (`GET /api/v1/movies/{id}`), but API consumers cannot find
movies without downloading or indexing the whole catalog. CAT-002 adds the catalog **search**
capability — the first real collection endpoint over catalog data — so consumers can search by title,
narrow by genre/year/rating, sort, and page through results.

## What Changes

- Add `GET /api/v1/movies`: a public, read-only, paginated, sorted, and filtered collection of movie
  **summary** resources, following the HAL collection convention (items in `data._embedded.movies`,
  pagination links in `data._links`, counts in `meta.pagination`).
- Summary item fields: `id`, `title`, `releaseYear`, `genres` (inline labels), `runtimeMinutes`
  (omitted when absent), `rating` 0–5 (omitted when absent), plus `_links.self` → `/movies/{id}`.
  No synopsis/credits/reviews.
- Filters (all combine with AND): `title` (case-insensitive substring), `genre` (repeatable, AND —
  movie must carry all), `yearFrom`/`yearTo` (inclusive range, each optional), `minRating`
  (inclusive; unrated excluded when supplied). Empty result set is a normal `200`.
- Sorting: `sort=<field>,<dir>` over `title|releaseYear|rating`, asc/desc; default `releaseYear` desc
  with `title` asc tiebreak; unknown sort field → `400`.
- Pagination: zero-based `page`; `size` default 20, max 100 (reuses shared `page`/`size` params);
  invalid `page`/`size` → `400` via a `@Validated` controller.
- Extend the catalog domain/application with a search use case + an outbound search port, and add a
  Postgres persistence adapter. **Performance**: a page of N movies must issue a bounded number of
  SQL queries independent of N (no N+1 genre loading); a Testcontainers test guards the query count.
- **BREAKING**: none. Additive API operation; additive Flyway migration (new indexes only); no
  change to existing tables or to `MovieJpaEntity`'s mapping.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `catalog/movies`: add the search/list collection requirements (collection endpoint, summary
  representation, filters, sorting, pagination boundary behaviour, invalid-param handling, public
  access, and the bounded-query performance guarantee). The existing single-movie detail requirements
  are unchanged.

## Impact

- **API**: new `GET /api/v1/movies` operation and new response schemas (`MovieSummary`,
  `MovieCollectionData`, `MovieCollectionEnvelope`, `MovieCollectionLinks`, `MovieSummaryLinks`) in
  the split OpenAPI spec; reuses shared `Envelope`/`Meta`/`Pagination`/`Link`/`Problem` schemas, the
  `page`/`size` parameters, and the `400` response. Generated stubs regenerated.
- **Code**: `catalog/movies` domain (search criteria/sort/page value objects), application
  (`SearchMoviesUseCase` in port, `SearchMoviesService`, `SearchMoviesPort` out port), outbound
  persistence adapter (Postgres, two-step id-page-then-fetch), inbound `MovieController.listMovies`.
- **DB**: Flyway `V3` migration adding supporting btree indexes (`movies.release_year`,
  `movies.rating`, `movie_genre.genre_id`). No table alteration.
- **Tests**: `GeneratedApiCodegenTest` extended for `listMovies`; the N+1 query-count Testcontainers
  guard added.
- **Docs**: `domain/glossary.md` and `domain/business-rules.md` updated (movie summary field set,
  page-size policy, sort policy).
