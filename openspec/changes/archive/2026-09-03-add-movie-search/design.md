## Context

See proposal.md - Why. This extends the CAT-001 `catalog/movies` capability with its first collection
endpoint. Existing building blocks in play:

- `MovieJpaEntity` maps `genres` as `@ManyToMany(fetch = EAGER)` via `movie_genre` (unchanged here).
- The single-movie path (`LoadMovieByIdPort` → `MoviePersistenceAdapter` → `MovieJpaRepository`) and
  its JPA→domain `toDomain` mapping already exist and are reused.
- The HAL collection convention is proven by `SampleController` (items in `data._embedded.<rel>`,
  boundary links in `data._links`, counts in `meta.pagination`). This change follows that proof
  exactly, including its empty-page link behaviour.
- The OpenAPI pipeline is bundle-then-generate; `GeneratedApiCodegenTest` guards that operations bind
  to the shared component schemas and that no per-operation `<Op><Status>Response*` duplicates appear.
- Integration tests share the `PostgresIntegrationTest` singleton container (no `@Testcontainers`/
  `@Container` on the base).

## Goals / Non-Goals

**Goals:**
- Deliver `GET /api/v1/movies` contract-first, reusing the shared HAL/envelope components.
- Keep domain/application free of Spring/JPA/HATEOAS; confine link assembly to the web adapter.
- Guarantee a **bounded, N-independent** query count per page (no N+1 genre loading, no Hibernate
  in-memory pagination) with a test that guards it.

**Non-Goals (design-level):**
- Changing `MovieJpaEntity`'s `EAGER` mapping or the CAT-001 detail path.
- A `pg_trgm` trigram index for substring title search (deferred — see Open Questions).

## Decisions

### D1. Contract-first OpenAPI additions
- New collection path authored as `paths/movies-collection.yaml`, wired into `openapi.yaml` at path
  key `/movies` (the existing `/movies/{id}` stays in `paths/movies.yaml`). `operationId: listMovies`,
  `tags: [movies]`, `security: []`.
- Parameters: reuse `CorrelationId`, `Page`, `Size`; add inline query params `title` (string), `genre`
  (`type: array`, `items: string`, `style: form`, `explode: true` — repeatable), `yearFrom` (integer),
  `yearTo` (integer), `minRating` (number, min 0 max 5), `sort` (string, default `releaseYear,desc`).
- New schemas in `components/schemas/movie.yaml`: `MovieSummary` (`id`, `title`, `releaseYear`,
  `genres[]`, optional `runtimeMinutes`, optional `rating` 0–5, required `_links`),
  `MovieCollectionData` (`_embedded.movies[]` + `_links`), `MovieCollectionEnvelope` (`data`, `meta`).
  New link schemas in `common.yaml` mirroring the Sample pattern: `MovieCollectionLinks` (`self`
  required; `first`/`last`/`prev`/`next` optional) and `MovieSummaryLinks` (`self` required). All
  registered in `openapi.yaml#/components/schemas`.
- Responses: `200` → `MovieCollectionEnvelope` (`application/json`) + `X-Correlation-Id` header; `400`
  → `$ref BadRequest`; `500` → `$ref InternalError`.
- Reuse shared `Envelope`/`Meta`/`Pagination`/`Link`/`Problem`, the `page`/`size` params, and the
  `400` response — **no** per-operation `<Op><Status>Response*` duplicates.
- *Alternative considered:* a generic reusable `CollectionLinks`/`PageLinks` component. Rejected for
  now — the codebase's established pattern is per-resource link schemas (`SampleCollectionLinks`,
  `PingLinks`, `MovieLinks`); matching it avoids platform drift in this change.

### D2. N+1 avoidance — two-step id-page-then-fetch (the key decision)
A naive `JOIN FETCH m.genres` with `LIMIT/OFFSET` triggers Hibernate in-memory pagination
(HHH000104); leaving genres `EAGER` and paging entities triggers one genre query per row (N+1). The
adapter avoids both with two DB round-trips:

1. **Id page + count.** A filtered, sorted query selects only a page of movie **ids** (SQL
   `LIMIT/OFFSET`, no genre fetch). Predicates: `title` case-insensitive substring
   (`LOWER(title) LIKE %:t%`), `yearFrom`/`yearTo` range, `minRating` (`rating >= :min`, which also
   excludes NULL ratings). The genre AND-filter is a subquery over `movie_genre`/`genres` that **must
   restrict to the requested genres before grouping**:
   `... JOIN genres g ... WHERE g.name IN (:genres) GROUP BY movie_id HAVING COUNT(DISTINCT g.name) =
   :genreCount`. The `WHERE g.name IN (:genres)` restriction is mandatory — without it,
   `COUNT(DISTINCT g.name)` counts every genre a movie carries, so `HAVING = :n` would wrongly select
   movies with exactly `n` *total* genres rather than movies carrying *all `n` requested* genres. Sort
   is applied in SQL with the `title` asc tiebreak (see rating NULL handling below). Paging **ids**
   means no to-many join in the paged query, so no HHH000104.
   - **Total count wraps the identical id-selection.** `totalElements` is computed as
     `SELECT COUNT(*) FROM (<the same filtered/grouped/HAVING id-selection>) AS sub`. Wrapping the
     identical derived table is required because when the genre-AND `GROUP BY ... HAVING` is present, a
     bare `SELECT COUNT(*) ... GROUP BY ... HAVING` returns one row per movie, not the total — which
     would corrupt `meta.pagination.totalElements`/`totalPages` and the boundary links exactly when a
     genre filter is active. `totalPages` derives from that count and `size`.
2. **Fetch with genres.** Load the full `MovieJpaEntity` rows for that bounded id set via `LEFT JOIN
   FETCH m.genres` (or `@EntityGraph`) with `WHERE m.id IN (:ids)` — no pagination on this query (the
   id set is already ≤ `size`), so no HHH000104 — then re-order the fetched rows to match the step-1
   id order.

**Rating NULL ordering.** A plain `sort=rating` (no `minRating`) still includes unrated (NULL-rating)
movies. Postgres defaults `NULLS FIRST` for `DESC`, which would sort unrated movies to the top. Both
`rating` sort directions therefore SHALL use `NULLS LAST`, with the `title` asc tiebreak, so unrated
movies always sort after rated ones regardless of direction.

Result: ~3 statements (count + id-page + fetch-with-genres) regardless of page size or genres per
movie. Mapping reuses the existing `toDomain`.

An unknown or misspelled `genre` value is **not** an error — it simply matches no `movie_genre` rows,
narrowing (or emptying) the result set, and still returns `200`. No genre-vocabulary validation is
performed at the boundary.

- *Alternatives considered:* (a) `@BatchSize` on the collection — bounds N+1 to `ceil(N/batch)` but
  still scales with N and needs entity-level annotation change; (b) a flat summary projection with a
  separate batched genre fetch — viable but adds a projection type and a manual stitch; (c)
  `@EntityGraph` on a `Page` query directly — reintroduces HHH000104. The two-step approach is the
  most deterministic and keeps the count truly N-independent.

### D3. Search port shape (clean architecture)
- New outbound port `SearchMoviesPort` in `application/port/out`:
  `MoviePage search(MovieSearchCriteria criteria, int page, int size, MovieSort sort)`.
- New domain value objects (no Spring/JPA):
  - `MovieSearchCriteria(Optional<String> title, List<Genre> genres, Optional<Integer> yearFrom,
    Optional<Integer> yearTo, Optional<Rating> minRating)`.
  - `MovieSort(MovieSortField field, SortDirection direction)` with enums
    `MovieSortField {TITLE, RELEASE_YEAR, RATING}` and `SortDirection {ASC, DESC}`; a default constant
    (`RELEASE_YEAR`, `DESC`). The `title` asc tiebreak, and the `NULLS LAST` handling for `RATING` sort
    in both directions (see D2), are applied by the adapter, not modelled as sort fields.
  - `MoviePage(List<Movie> items, int page, int size, long totalElements, int totalPages)` — mirrors
    the existing `SamplePage`.
- Inbound port `SearchMoviesUseCase` + `SearchMoviesService` in `application/service` (constructor
  injection of `SearchMoviesPort`).
- The persistence adapter implementing `SearchMoviesPort` (either a new `MovieSearchPersistenceAdapter`
  or an added method on `MoviePersistenceAdapter`) lives in `adapters/out/persistence`, reusing
  `toDomain`. Dependency direction stays inward-only; the adapter depends on domain/application, never
  the reverse.

### D4. Summary is a web-adapter representation, not a new domain type
Search reuses the existing `Movie` aggregate for results — it already carries every summary field.
`synopsis` is loaded but simply not rendered. `MovieController.listMovies` maps `Movie` → the generated
`MovieSummary` DTO (omitting `synopsis`) and builds each item's `_links.self` and the collection
`_links` via `WebMvcLinkBuilder`, pointing item `self` at `getMovieById`. This keeps the domain lean
and confines all HAL/link assembly to `adapters/in/web`.

### D5. Web adapter — validation, sort parsing, link boundary logic
- `MovieController` implements the generated `listMovies` and is class-annotated `@Validated` so the
  generated `@Min`/`@Max` on `page`/`size` are enforced on the bean (the interface annotation alone is
  not honoured — same rationale as `SampleController`).
- `sort=<field>,<dir>` is parsed in the adapter: map `<field>` to `MovieSortField` and `<dir>` to
  `SortDirection`; an unknown field or direction throws `IllegalArgumentException`, which the global
  `@RestControllerAdvice`'s `onBadRequest` already maps to `400 problem+json`. Do **not** use
  `ValidationException` here — `GlobalExceptionHandler` maps it to `422`, but the spec requires unknown
  sort → `400`. Do not globally re-map `ValidationException` (that would wrongly demote legitimate
  `422` body-validation responses). The advice already maps `ConstraintViolationException` and
  `MethodArgumentTypeMismatchException` to `400`; together these cover all invalid-param paths
  (out-of-range, non-numeric, bad sort) → `400`, never `500`.
- Collection `_links` reuse the exact boundary logic proven in `SampleController`: `self`/`first`/`last`
  always; `prev` when `page > 0`; `next` when `page < totalPages - 1`; on empty (`totalPages == 0`),
  `first`/`last` address page `0` and no `next`/`prev`. Navigation links carry through the active
  `title`/`genre`/`yearFrom`/`yearTo`/`minRating`/`sort` query params.

### D6. Contract-guard test
Extend `GeneratedApiCodegenTest` with a test asserting `MoviesApi.listMovies` returns
`ResponseEntity<MovieCollectionEnvelope>`; the existing no-duplicate / single-shared-`Link` /
single-shared-`Problem` assertions remain and must stay green.

## Migration Plan

- Flyway `V3__movie_search_indexes.sql`: add btree indexes on `movies(release_year)`, `movies(rating)`,
  and `movie_genre(genre_id)` to support the sort and genre-filter access paths. New migration file;
  `V2` is never edited.
- **Rollback:** the indexes are additive and non-breaking; rollback is `DROP INDEX` (low risk, no data
  change). The API operation is additive; no existing consumer is affected.
- Deploy order: migration runs on startup via Flyway before the app serves traffic; the new operation
  becomes available once deployed.

## Risks / Trade-offs

- [Case-insensitive substring `LIKE '%term%'` cannot use a btree index → full table scan on large
  catalogs] → acceptable at current dataset size; a `pg_trgm` GIN index is the fix when it matters
  (deferred, see Open Questions).
- [Two-step fetch issues an extra round-trip vs a single query] → intentional; it trades one small
  extra query for a guaranteed N-independent statement count and correct SQL-side pagination.
- [Re-ordering fetched rows to match the id-page order is manual] → covered by the sort-correctness
  Testcontainers test; a bug would surface as mis-ordered results, which the test asserts against.
- [Testcontainers rows leaking into the shared singleton container] → each new integration test class
  inserts its own fixtures and cleans them up (delete-after), asserting only against its own ids, per
  the CAT-001 precedent and testing.md.

## Open Questions

- **pg_trgm index for substring search.** Recommend deferring a `pg_trgm` GIN index until catalog size
  warrants it; this change ships without it. Confirm acceptable for now (does not change these specs or
  the task breakdown — purely a later performance follow-up).
- **Query-param / sort naming.** Proposed `title`, `genre` (repeatable), `yearFrom`, `yearTo`,
  `minRating`, `sort=<field>,<dir>`, `page`, `size` — matches the ticket. Confirm no platform
  precedent dictates otherwise.
- **Empty-result convention.** Resolved by aligning with the `SampleController` proof (`totalPages: 0`;
  `first`/`last` emitted addressing page 0; no `next`/`prev`). Confirm no divergence is desired.
