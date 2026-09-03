## 1. OpenAPI contract

- [x] 1.1 Add `MovieCollectionLinks` (`self` required; `first`/`last`/`prev`/`next` optional) and
      `MovieSummaryLinks` (`self` required) to `components/schemas/common.yaml`, mirroring the Sample
      link schemas.
- [x] 1.2 Add `MovieSummary`, `MovieCollectionData` (`_embedded.movies[]` + `_links`), and
      `MovieCollectionEnvelope` to `components/schemas/movie.yaml`; `runtimeMinutes`/`rating` optional,
      `rating` 0–5, no `synopsis`.
- [x] 1.3 Author `paths/movies-collection.yaml`: `operationId: listMovies`, `tags: [movies]`,
      `security: []`; reuse `CorrelationId`/`Page`/`Size` params; add `title`, `genre` (array, form,
      explode), `yearFrom`, `yearTo`, `minRating` (0–5), `sort` (default `releaseYear,desc`); `200`
      `MovieCollectionEnvelope` + `X-Correlation-Id`, `400` `$ref BadRequest`, `500` InternalError.
- [x] 1.4 Wire `/movies` → `paths/movies-collection.yaml` in `openapi.yaml`, register the new schemas
      under `components.schemas`, and confirm no per-operation `<Op><Status>Response*` duplicates are
      introduced.

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate` and confirm `MoviesApi.listMovies` returns
      `ResponseEntity<MovieCollectionEnvelope>` bound to the shared components (no duplicate DTOs).

## 3. Domain

- [x] 3.1 Add `MovieSortField {TITLE, RELEASE_YEAR, RATING}` and `SortDirection {ASC, DESC}` enums and
      a `MovieSort` value object with a default constant (`RELEASE_YEAR`, `DESC`) — no Spring/JPA.
- [x] 3.2 Add `MovieSearchCriteria` (`Optional<String> title`, `List<Genre> genres`,
      `Optional<Integer> yearFrom`, `Optional<Integer> yearTo`, `Optional<Rating> minRating`) with
      null-safety in its compact constructor.
- [x] 3.3 Add `MoviePage(List<Movie> items, int page, int size, long totalElements, int totalPages)`
      mirroring `SamplePage`.

## 4. Application (ports + use case)

- [x] 4.1 Add outbound port `SearchMoviesPort` in `application/port/out`:
      `MoviePage search(MovieSearchCriteria criteria, int page, int size, MovieSort sort)`.
- [x] 4.2 Add inbound port `SearchMoviesUseCase` in `application/port/in` and `SearchMoviesService`
      in `application/service` (constructor-injected `SearchMoviesPort`).

## 5. Outbound persistence adapter + migration

- [x] 5.1 Add Flyway `V3__movie_search_indexes.sql`: btree indexes on `movies(release_year)`,
      `movies(rating)`, `movie_genre(genre_id)`. Do not edit `V2`.
- [x] 5.2 Implement the two-step id-page-then-fetch adapter for `SearchMoviesPort`: step 1 queries a
      page of movie ids with title/year/minRating predicates and the genre AND-subquery restricted to
      the requested genres before grouping —
      `JOIN genres g ... WHERE g.name IN (:genres) GROUP BY movie_id HAVING COUNT(DISTINCT g.name) =
      :genreCount` (the `WHERE g.name IN (:genres)` is mandatory; without it the HAVING counts total
      genres and is silently wrong) — sorted in SQL with `title` asc tiebreak and `NULLS LAST` for
      `rating` sort in both directions; step 2 fetches those ids `LEFT JOIN FETCH` genres
      (`WHERE id IN (:ids)`, no pagination) and re-orders to the step-1 order; map via existing
      `toDomain`. Do not change `MovieJpaEntity`'s mapping. Compute `totalElements` as
      `SELECT COUNT(*) FROM (<the same filtered/grouped/HAVING id-selection>) AS sub` — the count must
      wrap the identical derived table as the id-page query so counts stay consistent when a genre
      filter is active; derive `totalPages` from that count and `size`.

## 6. Inbound web adapter

- [x] 6.1 Implement `MovieController.listMovies` (class `@Validated`): parse `sort=<field>,<dir>`
      (unknown field/dir → `IllegalArgumentException`, which `onBadRequest` maps to `400`; do NOT use
      `ValidationException`, which maps to `422`), build `MovieSearchCriteria`/`MovieSort`, call the use
      case, map `Movie` → `MovieSummary` (omit `synopsis`) with `_links.self` → `getMovieById`.
- [x] 6.2 Assemble `data._links` with the boundary logic from `SampleController` (self/first/last
      always; prev if `page>0`; next if `page<totalPages-1`; empty → first/last at page 0, no
      next/prev) and carry the active filter/sort query params into every navigation link; set
      `meta.pagination` counts.
- [x] 6.3 Confirm the global `@RestControllerAdvice` maps `ConstraintViolationException`,
      `MethodArgumentTypeMismatchException`, and the unknown-sort `IllegalArgumentException` all to
      `400 problem+json`; never `500`, no `_links`/`_embedded`. Do not re-map `ValidationException`.

## 7. Tests + docs

- [x] 7.1 Domain unit tests for `MovieSearchCriteria`, `MovieSort`, and `MoviePage` (validation,
      defaults).
- [x] 7.2 Application unit test for `SearchMoviesService` with `SearchMoviesPort` mocked (criteria/sort
      pass-through, page returned).
- [x] 7.3 `@WebMvcTest`/MockMvc tests for `listMovies`: default HAL collection shape, summary fields
      (optional omitted not null), boundary links (first/middle/last/empty/beyond-last), filters and
      sort applied, `400` on invalid `page`/`size`/`sort`/non-numeric params, public access (no
      401/403).
- [x] 7.4 Testcontainers persistence test (shared `PostgresIntegrationTest` singleton; own fixtures,
      cleaned up after): filter (title/genre-AND/year/minRating), combined-filter, sort, and
      pagination correctness against real Postgres. Include: a genre-AND fixture with a movie carrying
      EXTRA genres beyond the requested set (must match) and one carrying only a SUBSET (must not
      match); a combined-filter (genre-AND + title/year/minRating) assertion on
      `totalElements`/`totalPages` verifying the wrapped count query is correct; and a mixed
      rated/unrated fixture asserting `sort=rating` places unrated movies last in both directions.
- [x] 7.5 Testcontainers N+1 guard: assert the SQL statement count for a multi-row multi-genre page is
      bounded and does not grow with page size or genres-per-movie (Hibernate statistics/query
      counter); assert `GET /movies/{id}` query behaviour does not regress.
- [x] 7.6 Extend `GeneratedApiCodegenTest`: `listMovies` returns `ResponseEntity<MovieCollectionEnvelope>`;
      keep the no-duplicate / single-shared-`Link` / single-shared-`Problem` assertions green.
- [x] 7.7 Add the movie-summary field set to `domain/glossary.md`, and confirm the page-size and sort
      policy TODOs in `domain/business-rules.md` are already resolved/removed (that policy already lives
      there — do not duplicate it).
- [x] 7.8 Run `./gradlew build` and confirm all tests, Spotless, and the codegen guard pass.
