## Context

See proposal.md — Why. UC-001 established the `catalog/movies` capability on this branch: the `Movie` aggregate (`domain/model/{Movie,Genre,Rating}`), the `movies` + `movie_genres` schema (Flyway `V2__movies.sql`, genres as an EAGER `@ElementCollection`), `MovieController implements MoviesApi` for `GET /movies/{id}`, the shared success `Envelope`/`Meta`/`Pagination`/`Problem`/`Link` components, the HAL convention (`platform/hypermedia-links`), reusable `Page`/`Size` query params (`page>=0 default 0`; `size 1..100 default 20`), and the `listSamples` collection as the HAL-pagination exemplar. `GlobalExceptionHandler` already maps `ConstraintViolationException` (query-param `@Min`/`@Max`), `MethodArgumentTypeMismatchException`, and `IllegalArgumentException` to `400 problem+json`. `PublicEndpoints.PATTERNS` is string-matched against OpenAPI `security: []` operations by a consistency test. `GeneratedApiCodegenTest` fails the build if per-operation `<Operation><Status>Response*` DTOs reappear or shared `Link`/`Problem` models are duplicated.

## Goals / Non-Goals

**Goals:**
- Add the collection/search endpoint as a sibling of `GET /movies/{id}` within the same capability, reusing UC-001's aggregate, schema, and shared platform components.
- Keep the domain and application layers free of Spring/JPA/HAL; push filtering, sorting, paging semantics through typed value objects.
- Guarantee a bounded, page-size-independent query count and deterministic cross-page ordering.

**Non-Goals:**
- No new domain summary type — the summary is a web-layer projection of `Movie` (omit synopsis).
- No DB schema change and no new migration.
- No caching, rate limiting, or full-text search (substring LIKE only).

## Decisions

### API — one operation, shared schemas
Add `GET /api/v1/movies` (`operationId: getMovies`, `security: []`) in a new `paths/movies-collection.yaml`; register `/movies` in `openapi.yaml#/paths` and in `PublicEndpoints.PATTERNS` (verbatim `"/movies"`). Query params: reuse shared `Page`/`Size`; add `title` (string), `genre` (array of the existing `Genre` enum, `style=form explode=true`, repeatable), `releaseYearFrom`/`releaseYearTo` (integer), `minRating` (number, `minimum:0 maximum:5`), `sort` (string, `default: releaseYear,desc`), plus `X-Correlation-Id`. `page`/`size` bounds are declared on the shared params so the generator emits `@Min`/`@Max` → `ConstraintViolationException` → 400.

New shared named schemas in `components/schemas/movie.yaml` (registered in `openapi.yaml#/components/schemas`), reusing `Envelope`/`Meta`/`Pagination`/`Link`/`Genre` — no per-operation duplicates, so `GeneratedApiCodegenTest` stays green:

```yaml
MovieSummary:
  type: object
  required: [id, title, releaseYear, genres, _links]
  properties:
    id: { type: string, format: uuid }
    title: { type: string }
    releaseYear: { type: integer }
    genres: { type: array, minItems: 1, items: { $ref: '#/Genre' } }
    runtimeMinutes: { type: integer, minimum: 1 }   # optional, omitted when absent
    rating: { type: number, minimum: 0, maximum: 5 } # optional, omitted when absent
    _links: { $ref: '../../openapi.yaml#/components/schemas/MovieLinks' }  # self -> detail
MovieSummaryCollectionData:
  type: object
  required: [_embedded, _links]
  properties:
    _embedded:
      type: object
      required: [movies]
      properties:
        movies: { type: array, items: { $ref: '#/MovieSummary' } }
    _links: { $ref: '../../openapi.yaml#/components/schemas/MovieCollectionLinks' }
MovieSummaryCollectionEnvelope:
  type: object
  required: [data, meta]
  properties:
    data: { $ref: '#/MovieSummaryCollectionData' }
    meta: { $ref: '../../openapi.yaml#/components/schemas/Meta' }
```

Add `MovieCollectionLinks` (self/first/last/prev/next, `self` required) in `components/schemas/common.yaml` alongside `SampleCollectionLinks`. The item reuses the existing `MovieLinks` (self only); its `self` resolves to the movie's detail. Success `application/json`; errors `application/problem+json` (`400`, `500`). `MovieSummary` deliberately has no `synopsis` property.

**Alternative considered:** a generic paged envelope reused across capabilities — rejected as premature; the codebase names collection schemas per capability (`SampleCollection*`), so we follow that convention.

### Clean architecture — components touched (dependencies inward only)
- **domain/model** (new, no Spring/JPA): `MovieSearchCriteria` (record: `Optional<String> title`, `Set<Genre> genres`, `Optional<Integer> yearFrom`, `Optional<Integer> yearTo`, `Optional<BigDecimal> minRating`), `MovieSort` (record: `Field {TITLE, RELEASE_YEAR, RATING}` enum + `Direction {ASC, DESC}` enum; a parse factory that throws `IllegalArgumentException` on an unsupported field), `MoviePageRequest` (record: `int page`, `int size`), `MoviePage` (record: `List<Movie> content`, `int page`, `int size`, `long totalElements`; derives `totalPages`). Reuse the existing `Movie`/`Genre`/`Rating`.
- **application/port/in** (new): `SearchMoviesUseCase.search(MovieSearchCriteria, MoviePageRequest, MovieSort) -> MoviePage`. **application/service** (new): `SearchMoviesService` implements it, delegates to the outbound port. **application/port/out** (new): `SearchMoviesPort.search(...) -> MoviePage`.
- **adapters/out/persistence** (new): `MovieSearchPersistenceAdapter implements SearchMoviesPort`, using a Spring Data JPA repository (extend the existing `MovieJpaRepository` or add a search repository). **adapters/in/web**: add the `getMovies` handler to `MovieController` (already implements `MoviesApi` — the collection operation lands on the same generated interface), mapping `Movie -> MovieSummary` (omit synopsis) and assembling HAL links via `WebMvcLinkBuilder`, item `self` pointing at `getMovieById`. Link assembly stays web-only. Dependency direction: web → application → domain; persistence → application ports + domain. No inward layer imports Spring/JPA/HAL.

### Sorting + determinism
Translate `MovieSort` to a primary order (requested field+direction, or `releaseYear` desc default), then append `title` asc as a secondary tiebreak, then `id` asc as a **terminal unique tiebreak**. The unique terminal key makes the total order strict so offset paging cannot skip or duplicate rows that tie on the sort field (BR-4/BR-6).

### Filters
- **Title:** `lower(title) LIKE lower(:pattern) escape '\'`, portable across Postgres (integration) and H2 (default runtime). A small escaping helper escapes `\`, `%`, `_` in the consumer term, then wraps it `%term%`, so wildcard characters match literally.
- **Genre ALL:** a movie must carry every supplied genre. This SHALL be expressed as the restrict-then-count construct evaluated in the id-selection query below: `... WHERE genre IN (:genres) GROUP BY movie_id HAVING COUNT(DISTINCT genre) = :suppliedDistinctCount`, where `:suppliedDistinctCount` is the size of the **de-duplicated** requested genre set — bound directly from the `Set<Genre> genres` in `MovieSearchCriteria` (`genres.size()`), so request-side duplicates cannot corrupt the count. The `WHERE genre IN (:genres)` restriction before grouping is required: it means the `HAVING` counts only the *requested* genres a movie carries, so a movie with a **superset** of genres (e.g. {Drama, Crime, Action}) still matches a {Drama, Crime} filter. A bare `HAVING count(distinct genre) = :n` over the unrestricted join counts the movie's TOTAL distinct genres and is the wrong (superset-rejecting) pattern — do not use it. This exact construct is required, not "or equivalent."
- **Release year:** inclusive `>=` / `<=` applied only for the bounds supplied. An inverted range (`releaseYearFrom > releaseYearTo`) is a **valid** request that simply matches nothing (empty `200` success per BR-7) — it SHALL NOT be rejected as a `400`.
- **Minimum rating:** `rating IS NOT NULL AND rating >= :min` (unrated excluded).

### N+1 / bounded query count
Two-phase read to keep statement count constant regardless of page size: (1) select the page of matching movie **ids** with all filters + full ordering + limit/offset, plus (2) a count query for `totalElements`, then (3) hydrate exactly those ids into `MovieJpaEntity` with genres in a single fetch — a `join fetch`/`@EntityGraph` over the id set — reordered in-adapter to preserve the phase-1 order. That is exactly 3 statements (id-page + count + hydrate) whether the page holds 1 or 100 movies. A Hibernate-statistics guard test asserts the EXACT small constant statement count and that it is identical for a 1-movie page versus a 100-movie page (not merely "constant").

**Count query (phase 2) — must agree with the page query:** `totalElements` SHALL count **distinct matching movies** (`COUNT(DISTINCT m.id)` or equivalent) under the SAME predicates as the phase-1 id query — the escaped `lower(title) LIKE :pattern escape '\'`, the year-range and `minRating` filters, AND the genre `WHERE genre IN (:genres) ... GROUP BY movie_id HAVING COUNT(DISTINCT genre) = :suppliedDistinctCount`. A naive `count(*)` over the genre join over-counts (one row per movie-genre pair); a count that drops the `ESCAPE` clause or the genre `HAVING` makes `totalElements` disagree with the page rows and corrupts `next`/`totalPages`. The count and the page selection MUST apply an identical predicate set. (Genres are currently EAGER; the explicit fetch-join over the bounded id set avoids the per-row select-genres N+1.)

### Error mapping (reuse existing advice)
- `page`/`size` out of bounds → generated `@Min`/`@Max` → `ConstraintViolationException` → 400.
- `minRating` out of 0–5 → schema `minimum`/`maximum` → 400; unknown `genre` value → enum bind failure (`MethodArgumentTypeMismatchException`) → 400.
- Unsupported `sort` field → `MovieSort.parse` throws `IllegalArgumentException` → existing handler → 400 (not 500).
- Empty match and page-beyond-last → normal `200` empty page (never 400/404).

## Risks / Trade-offs
- [Two-phase read adds a query vs a single fetch-join page] → Accepted: a single fetch-join with limit/offset over a to-many collection paginates in-memory (Hibernate `HHH000104`) and breaks correct paging; the id-first approach is the standard fix and keeps the count bounded.
- [H2 default runtime vs Postgres integration divergence in LIKE/collation] → Mitigated by `lower(...) LIKE ... escape` (portable) and by running the correctness + N+1 tests on Testcontainers Postgres; keep tests seed-independent.
- [`genre` repeated query param ergonomics] → `style=form explode=true` yields `?genre=Action&genre=Drama`, the platform's existing convention; documented in the contract.
- [Adding `getMovies` to `MoviesApi` grows one interface] → Acceptable; both operations belong to the same capability and controller, avoiding a second generated API tag.

## Migration Plan
No Flyway migration — the `movies` and `movie_genres` tables and demo seed from `V2__movies.sql` are reused unchanged. Rollback is limited to reverting the code + OpenAPI + generated stubs; there is no schema state to undo. Deploy order: author OpenAPI → `openApiGenerate` → implement → tests green via `./gradlew build -x spotlessCheck`.
