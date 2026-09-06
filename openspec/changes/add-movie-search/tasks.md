## 1. OpenAPI contract (contract-first)

- [ ] 1.1 Add `paths/movies-collection.yaml` with `get` `operationId: getMovies`, `tags: [movies]`, `security: []`, `X-Correlation-Id` + shared `Page`/`Size` params, and `title`, `genre` (array of `Genre`, `style=form explode=true`), `releaseYearFrom`, `releaseYearTo`, `minRating` (`minimum:0 maximum:5`), `sort` (`default: releaseYear,desc`) query params; `200` → `MovieSummaryCollectionEnvelope`, plus `400`/`500` shared responses.
- [ ] 1.2 Add `MovieSummary`, `MovieSummaryCollectionData`, `MovieSummaryCollectionEnvelope` to `components/schemas/movie.yaml` (summary reuses `Genre` + `MovieLinks`, has runtimeMinutes/rating optional, NO synopsis); add `MovieCollectionLinks` (self/first/last/prev/next) to `components/schemas/common.yaml`.
- [ ] 1.3 Register `/movies` under `openapi.yaml#/paths` and the new schemas (`MovieSummary`, `MovieSummaryCollectionData`, `MovieSummaryCollectionEnvelope`, `MovieCollectionLinks`) under `openapi.yaml#/components/schemas`; update the `movies` tag description.

## 2. Generate stubs

- [ ] 2.1 Run `./gradlew openApiGenerate` and confirm `MoviesApi` gains a `getMovies` method returning `MovieSummaryCollectionEnvelope`, and the new DTOs generate with no per-operation `<Operation><Status>Response*` duplicates.

## 3. Domain value objects (no Spring/JPA)

- [ ] 3.1 Add `MovieSearchCriteria` (record: optional title, `Set<Genre>`, optional yearFrom/yearTo, optional minRating) with null-safe/immutable construction.
- [ ] 3.2 Add `MovieSort` (record: `Field {TITLE, RELEASE_YEAR, RATING}` + `Direction {ASC, DESC}`) with a parse factory that throws `IllegalArgumentException` on an unsupported field/direction, and a default of `RELEASE_YEAR` desc.
- [ ] 3.3 Add `MoviePageRequest` (page, size) and `MoviePage` (`List<Movie>` content, page, size, totalElements; derives totalPages).

## 4. Application (ports + service)

- [ ] 4.1 Add inbound port `SearchMoviesUseCase.search(MovieSearchCriteria, MoviePageRequest, MovieSort): MoviePage` and outbound port `SearchMoviesPort` with the same signature.
- [ ] 4.2 Add `SearchMoviesService implements SearchMoviesUseCase`, delegating to `SearchMoviesPort` (depends only on domain + ports).

## 5. Outbound persistence adapter (no migration)

- [ ] 5.1 Add a LIKE-escaping helper that escapes `\`, `%`, `_` in the title term and builds a `%term%` pattern; use `lower(title) LIKE lower(:pattern) escape '\'`.
- [ ] 5.2 Add `MovieSearchPersistenceAdapter implements SearchMoviesPort`: phase-1 query selects the page of matching movie ids (all filters + full ordering [requested/default, then title asc, then id asc] + limit/offset); a count query fills totalElements; phase-2 hydrates those ids with genres via a single fetch-join/`@EntityGraph`, reordered to preserve phase-1 order; map to `MoviePage<Movie>`. Reuse `movies`/`movie_genres` — no new Flyway migration.
  - **Genre-ALL filter (required construct):** `... WHERE genre IN (:genres) GROUP BY movie_id HAVING COUNT(DISTINCT genre) = :suppliedDistinctCount`, binding `:suppliedDistinctCount` from the de-duplicated `Set<Genre> genres` size (`criteria.genres().size()`). The `WHERE genre IN (:genres)` restriction before the `HAVING` is mandatory so a movie with a superset of genres still matches. Do NOT use a bare `HAVING count(distinct genre) = :n` over the unrestricted join — that counts total distinct genres and wrongly rejects supersets.
  - **Count query:** count **distinct matching movies** (`COUNT(DISTINCT m.id)` or equivalent) under the SAME predicates as the phase-1 id query — the escaped title LIKE (`... escape '\'`), the year-range and `minRating` filters, AND the genre `WHERE-IN + HAVING`. Never `count(*)` over the genre join (over-counts), and never drop the `ESCAPE` or the genre `HAVING` from the count (makes totalElements disagree with the page rows).
  - An inverted year range (`releaseYearFrom > releaseYearTo`) is a valid request matching nothing (empty `200`), not a `400` — do not add a bounds check that rejects it.

## 6. Inbound web adapter

- [ ] 6.1 Implement `getMovies` on `MovieController`: bind params to `MovieSearchCriteria`/`MoviePageRequest`, parse `sort` via `MovieSort.parse` (unsupported field → `IllegalArgumentException` → 400), call the use case, map `Movie → MovieSummary` (omit synopsis; omit absent runtime/rating), and assemble HAL links (item `self` → `getMovieById`; collection `self`/`first`/`last`/`prev`/`next` with boundary rules) via `WebMvcLinkBuilder`. Populate `meta.pagination`.
- [ ] 6.2 Register `"/movies"` in `PublicEndpoints.PATTERNS`.

## 7. Tests (per layer)

- [ ] 7.1 Domain unit tests: `MovieSort.parse` accepts supported fields/directions and throws on unsupported; `MovieSearchCriteria`/`MoviePage` invariants and derived totalPages.
- [ ] 7.2 Application service test: delegates to the port and returns its `MoviePage`.
- [ ] 7.3 Persistence integration tests (Testcontainers Postgres, seed-independent): title case-insensitive substring; LIKE-escaping literal `%`/`_` match; genre-ALL; year range (each bound alone + both); minRating inclusive + unrated excluded; combined criteria; default + each sort field/direction; cross-page determinism across ties (every movie once, no gaps/dupes). Plus the two trap guards:
  - **Genre superset:** a movie carrying {Drama, Crime, Action} MUST be returned by a filter of {Drama, Crime} (guards against the bare-`HAVING` superset bug).
  - **Count-vs-rows agreement:** under a combined genre filter + a wildcard-containing title term, assert `totalElements` equals the actual number of distinct matching movies (guards against a count query that drops the genre `HAVING` or the `ESCAPE`, or that `count(*)`-over-counts the join).
- [ ] 7.4 N+1 guard test (Hibernate statistics): assert the EXACT small constant statement count (~3: id-page + count + hydrate) for a full page of multi-genre movies, and that it is identical for a 1-movie page versus a 100-movie page (not merely "constant").
- [ ] 7.5 Web/controller tests (MockMvc): browse-all happy path (envelope + summary shape, no synopsis, item self link); pagination metadata + HAL links (middle/first/last boundaries); empty match is 200 total 0; page beyond last is 200 total>0 no next; 400 for unsupported sort, page=-1, size=0, size=101, minRating=6, unknown genre (problem+json, correlationId, not 500).
- [ ] 7.6 Update `GeneratedApiCodegenTest` to assert `getMovies` returns the shared `MovieSummaryCollectionEnvelope` (no per-operation duplicates); confirm the public-endpoint consistency test passes with `/movies`.
- [ ] 7.7 Run `./gradlew build -x spotlessCheck` and confirm green.
