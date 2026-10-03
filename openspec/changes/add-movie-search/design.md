## Context

See proposal.md (Why). The tree has the UC-000 platform and the UC-001 `catalog/movies` slice: `GET /movies/{id}` implemented by `MovieController implements MoviesApi`, a `Movie` aggregate whose factory owns genre ordering, `MoviePersistenceAdapter` over `MovieJpaEntity`/`GenreJpaEntity` (`@ManyToMany` via `movie_genre`), `V1__create_movie_catalog.sql`, a standalone-only demo seed of four movies, and a closed set of failure kinds built by `ProblemFactory` with fixed, non-echoing details.

Constraints carried forward: success is always `application/json`; success schemas are closed (`additionalProperties: false`) and validated at runtime by `InterfaceDescriptionContractTest`; shared concepts are defined once (`platform/interface-description`); `standards/openapi.md` §2/§2a fixes the collection shape (`data._embedded.<rel>`, `meta.pagination`, navigation links that preserve only the params actually sent, page counted from `0`); only one H2-booting test may exist; Spring Boot 3.5 (Spring Framework 6.2, Hibernate 6.6), openapi-generator 7.14 `spring` with `interfaceOnly`, `useTags`.

## Goals / Non-Goals

**Goals:**
- One public read-only collection operation implementing UC-002, reusing the UC-001 domain model, schema and failure kinds.
- A reusable paged-collection form (contract components + a small link helper) that UC-005/UC-006 can adopt unchanged.
- All invalid-search rules decided before the catalog is touched, and every refusal names the offending parameter.
- Order and paging computed in the database with a complete, stable order; no in-memory paging.

**Non-Goals:**
- No schema change, no new index, no caching of counts, no keyset paging.
- No new failure kind or `code`: invalid searches are `BAD_REQUEST`.

## Decisions

### D1. Contract (added operation `GET /movies`; changed shared `Meta`, `Problem`)
Added: `movies` path item in `paths/movies.yaml`; `MovieSearchEnvelope`, `MovieSearchPage`, `MovieSearchEmbedded`, `MovieSummary`, `MovieSearchLinks` in `components/schemas/movie.yaml`; `Pagination` and `InvalidParam` in `components/schemas/common.yaml`; new `components/parameters/common.yaml` with `Page` and `Size`. Changed (additive): `Meta` gains optional `pagination`; `Problem` gains optional `errors`. `MovieLinks` (UC-001) is reused for each summary's `_links` (`self` only). Nothing removed.

```yaml
# openapi.yaml (addition)
paths:
  /movies:
    $ref: './paths/movies.yaml#/movies'

# paths/movies.yaml (addition)
movies:
  get:
    operationId: searchMovies
    summary: Search and browse movies
    description: >
      Finds movies matching every given criterion, one page at a time. With no
      criteria, browses the whole catalog. `title` matches any part of the title
      ignoring case (blank = no criterion). Repeat `genre` to require several
      genres (all must be carried; case-insensitive; must be in the curated
      vocabulary). Release-year bounds are inclusive; give the same year twice for
      a single year. `minRating` is inclusive and leaves out unrated movies.
      `sort` defaults to `-releaseYear` (newest first, then title A–Z); unrated
      movies always come last when sorting by rating. A page after the last is an
      empty 200. Invalid searches are 400 with the offending parameter in `errors`.
    tags: [Movies]
    security: []
    parameters:
      - { name: title, in: query, required: false, schema: { type: string, maxLength: 200 } }
      - name: genre
        in: query
        required: false
        style: form
        explode: true
        schema: { type: array, items: { type: string } }
      - { name: releaseYearFrom, in: query, required: false, schema: { type: integer, format: int32 } }
      - { name: releaseYearTo,   in: query, required: false, schema: { type: integer, format: int32 } }
      - { name: minRating, in: query, required: false, schema: { type: number, minimum: 0, maximum: 5 } }
      - name: sort
        in: query
        required: false
        schema:
          type: string
          enum: [title, -title, releaseYear, -releaseYear, rating, -rating]
      - $ref: '../components/parameters/common.yaml#/Page'
      - $ref: '../components/parameters/common.yaml#/Size'
    responses:
      '200':
        description: One page of matching movie summaries.
        headers:
          X-Correlation-Id: { $ref: '../components/headers/common.yaml#/X-Correlation-Id' }
        content:
          application/json:
            schema: { $ref: '../components/schemas/movie.yaml#/MovieSearchEnvelope' }
      '400': { $ref: '../components/responses/common.yaml#/BadRequest' }
      '406': { $ref: '../components/responses/common.yaml#/NotAcceptable' }
      '500': { $ref: '../components/responses/common.yaml#/InternalError' }

# components/parameters/common.yaml (new)
Page:
  name: page
  in: query
  required: false
  description: Zero-based page index. A page after the last is an empty 200.
  schema: { type: integer, format: int32, minimum: 0, default: 0 }
Size:
  name: size
  in: query
  required: false
  description: Page size.
  schema: { type: integer, format: int32, minimum: 1, maximum: 100, default: 20 }

# components/schemas/movie.yaml (additions)
MovieSearchEnvelope:
  type: object
  required: [data, meta]
  additionalProperties: false
  properties:
    data: { $ref: '#/MovieSearchPage' }
    meta: { $ref: './common.yaml#/Meta' }
MovieSearchPage:
  type: object
  required: [_embedded, _links]
  additionalProperties: false
  properties:
    _embedded: { $ref: '#/MovieSearchEmbedded' }
    _links:    { $ref: '#/MovieSearchLinks' }
MovieSearchEmbedded:
  type: object
  required: [movies]
  additionalProperties: false
  properties:
    movies:
      type: array
      items: { $ref: '#/MovieSummary' }
MovieSummary:
  type: object
  required: [id, title, releaseYear, genres, _links]
  additionalProperties: false
  properties:
    id:             { type: string, format: uuid }
    title:          { type: string, minLength: 1 }
    releaseYear:    { type: integer }
    genres:         { type: array, items: { type: string, minLength: 1 } }
    runtimeMinutes: { type: integer, minimum: 1 }
    rating:         { type: number, minimum: 0, maximum: 5 }
    _links:         { $ref: '#/MovieLinks' }
MovieSearchLinks:
  type: object
  required: [self, first, last]
  additionalProperties: false
  properties:
    self:  { $ref: './common.yaml#/Link' }
    first: { $ref: './common.yaml#/Link' }
    prev:  { $ref: './common.yaml#/Link' }
    next:  { $ref: './common.yaml#/Link' }
    last:  { $ref: './common.yaml#/Link' }

# components/schemas/common.yaml (changes)
Meta:
  properties:
    # ...timestamp, correlationId unchanged; still additionalProperties: false
    pagination: { $ref: '#/Pagination' }
Pagination:
  type: object
  required: [page, size, totalElements, totalPages]
  additionalProperties: false
  properties:
    page:          { type: integer, format: int32, minimum: 0 }
    size:          { type: integer, format: int32, minimum: 1 }
    totalElements: { type: integer, format: int64, minimum: 0 }
    totalPages:    { type: integer, format: int32, minimum: 0 }
Problem:
  properties:
    # ...existing members unchanged
    errors:
      type: array
      minItems: 1
      items: { $ref: '#/InvalidParam' }
InvalidParam:
  type: object
  required: [field, message]
  additionalProperties: false
  properties:
    field:   { type: string }
    message: { type: string }
```
- `MovieSummary` is a separate schema, not `MovieDetail` minus `synopsis`: the closed schema then *guarantees* BR-2 (no synopsis) in the contract test. Same wire rules as UC-001: `rating` without `format` → `BigDecimal`, written with `stripTrailingZeros()`; `genres` without `uniqueItems` (keeps `List` and A–Z order).
- `sort` is a string enum with leading `-` for descending (one parameter, URL-friendly, the same convention later lists can reuse). The value is validated by the domain (`MovieOrder.parse`, D3), not by Spring enum binding: Spring converts request params to enums by constant *name*, which cannot be `-title`. Task 2.2 asserts in `GeneratedApiCodegenTest` that the generator emits `String sort` (inline enums on parameters are emitted as `String`); if the generator instead emits an enum type, the fallback is to declare `sort` as `type: string` with the allowed values in `description` and keep the same domain validation, leaving behaviour unchanged.
- `title` gets `maxLength: 200` (a guard on LIKE pattern cost; titles are ≤ 500 so this never prevents matching a real title fragment a consumer would type); exceeding it is a `400` naming `title`.
- No `pattern`/`minLength` on `genre` items: vocabulary membership is a data check (D3), and an empty value must be a `400` naming `genre`, which the domain enforces uniformly.
- `Meta.pagination` stays optional on the one shared `Meta` (standards §2), so `/ping` and `/movies/{id}` bodies are unchanged; `ResponseMetaFactory` sets it only for paged lists. `Problem.errors` is optional, so all existing problem bodies stay valid.
- `GeneratedApiCodegenTest` gains: `MoviesApi.searchMovies` returns `MovieSearchEnvelope`; no `SearchMovies200Response*` duplicate exists.

### D2. Components and dependency direction (inward only)
```
com.acme.shared.domain
└── InvalidCriteriaException      NEW, extends DomainException, code BAD_REQUEST;
                                   List<Violation(String field, String message)> violations
com.acme.catalog.movies
├── domain/model/
│   ├── MovieSummary              NEW record: MovieId, title, releaseYear, genres (normalised as Movie does),
│   │                             Optional<RuntimeMinutes>, Optional<Rating>
│   ├── MovieSearchCriteria       NEW record: Optional<String> titleTerm, Set<String> genres (lower-cased),
│   │                             OptionalInt releaseYearFrom/To, Optional<Rating> minRating;
│   │                             factory blank-title → empty, rejects reversed range / blank genre
│   ├── MovieOrder                NEW enum {TITLE_ASC, TITLE_DESC, RELEASE_YEAR_ASC, RELEASE_YEAR_DESC,
│   │                             RATING_ASC, RATING_DESC}; parse(String) (null → RELEASE_YEAR_DESC)
│   ├── PageRequest               NEW record (int page ≥ 0, int size 1..100), defaults 0/20
│   └── ResultPage<T>             NEW record (List<T> items, int page, int size, long totalElements);
│                                 totalPages(), lastPage(), hasPrev(), hasNext()
├── application/port/in/SearchMoviesUseCase      ResultPage<MovieSummary> search(SearchMoviesQuery q)
│                       SearchMoviesQuery        NEW record of raw inputs (String title, List<String> genres,
│                                                Integer from, Integer to, BigDecimal minRating, String sort,
│                                                Integer page, Integer size)
├── application/port/out/SearchMoviesPort        ResultPage<MovieSummary> search(criteria, order, pageRequest)
│                        GenreVocabularyPort     Set<String> unknownGenres(Set<String> lowerCasedNames)
├── application/service/SearchMoviesService      builds domain values (throws InvalidCriteriaException,
│                                                collecting all domain violations), checks vocabulary,
│                                                then calls SearchMoviesPort
├── adapters/out/persistence/MovieSearchAdapter  implements SearchMoviesPort + GenreVocabularyPort (Criteria API)
└── adapters/in/web/MovieController              + searchMovies(...); PagedLinks helper (web-only)
com.acme.platform.web
├── GlobalExceptionHandler        + InvalidCriteriaException, ConstraintViolationException → 400 with errors;
│                                   populates errors for parameter-attributable framework 400s
├── ProblemFactory                + create(int status, List<InvalidParam-like (field,message)>)
└── ResponseMetaFactory           + create(Pagination)
```
- Domain imports JDK only (`MovieSearchCriteria`, `MovieOrder`, `PageRequest`, `ResultPage` have no Spring/JPA types). Application imports domain + shared kernel. Adapters import application ports. Platform imports the shared kernel only, never `catalog`. Dependency direction stays inward.
- Field names in `InvalidCriteriaException` violations are the criterion names, which are deliberately identical to the contract parameter names (`genre`, `releaseYearFrom`, `releaseYearTo`, `sort`, `page`, `size`, `minRating`, `title`). This is a documented naming agreement, not an import of web types. `MovieSearchCriteriaTest` asserts the names, so a rename in either place fails a test.
- `PageRequest`, `MovieOrder` and `ResultPage` are generic enough for UC-005/UC-006; they stay in the movies domain now and move to the shared kernel when a second list needs them (no speculative sharing).
- `MovieSummary` normalises genres with the same rule as `Movie`; the private `normalizeGenres` is extracted to a package-private `GenreNames.normalize(List<String>)` used by both, so the A–Z rule has one home.
- Why the service, not the controller, parses raw inputs: the controller must not build errors, and the parse rules (blank title, reversed range, unknown order) are business rules (BR-3, BR-10) that belong inward. Bean Validation still enforces the declared bounds at the boundary (D4) so the contract and behaviour agree; the domain re-checks `page`/`size`/`minRating` bounds in its value objects (defence in depth; same `field` names).
- Alternative considered: Spring Data `Pageable`/`Sort` in the use case port. Rejected: it leaks Spring into application/domain and cannot express "unrated last" plus the title secondary key without custom handling anyway.

### D3. Validation order and refusal (BR-10)
1. **Binding/constraints (framework):** type mismatch (`page=abc`, `releaseYearFrom=1e3`, out-of-int32) → `MethodArgumentTypeMismatchException`; declared bounds (`page`, `size`, `minRating`, `title` length) → method validation. All → `400` with `errors[].field` = parameter name (D4).
2. **Domain:** `MovieOrder.parse` (unknown `sort`), `MovieSearchCriteria` (blank genre value, `releaseYearFrom > releaseYearTo` → both fields), `PageRequest`, `Rating`-range for `minRating`. All violations from this step are collected and thrown together as one `InvalidCriteriaException`.
3. **Vocabulary:** `GenreVocabularyPort.unknownGenres` with the lower-cased, de-duplicated genre names; any unknown → `InvalidCriteriaException(genre)`. One cheap query: `SELECT lower(name) FROM genre WHERE lower(name) IN (:names)`.
4. Only then `SearchMoviesPort.search`. Steps 1–3 never touch the movie tables, satisfying "refused before searching" (asserted with a mocked port: `verifyNoInteractions`).
- Genre recognition is by `lower(name)` (Locale.ROOT in Java). If the curated vocabulary ever held two names equal ignoring case, a criterion matches a movie carrying either — the documented, deterministic meaning of "recognised ignoring letter case".
- Messages are fixed strings per rule (e.g. `must be one of the supported orders`, `is not in the curated genre vocabulary`, `must not be after releaseYearTo`) and never include the value.

### D4. Problem `errors` for parameter faults (platform)
- `ProblemFactory.create(status, violations)` adds the `errors` property only when `violations` is non-empty and the kind is `BAD_REQUEST`; otherwise unchanged. `detail` stays the fixed `BAD_REQUEST` detail.
- `GlobalExceptionHandler`:
  - `@ExceptionHandler(InvalidCriteriaException)` → 400 with its violations (logged at DEBUG).
  - `handleExceptionInternal` derives violations for the parameter-attributable framework exceptions it already receives: `MethodArgumentTypeMismatchException` (`getName()`), `MissingServletRequestParameterException` (`getParameterName()`), `HandlerMethodValidationException` (each `ParameterValidationResult` → `getMethodParameter().getParameterName()`, falling back to the `@RequestParam` name). Message is a fixed text per exception type (`is not a valid value`, `is required`, `is out of the allowed range`); Bean Validation's message text is not used, so it can never echo a value or vary by locale.
  - `@ExceptionHandler(ConstraintViolationException)` → 400 with violations from the property path's last node. Why both: the generated `MoviesApi` is `@Validated`, so depending on whether Spring 6.2's built-in method validation or the `MethodValidationPostProcessor` proxy fires first, the same `size=101` surfaces as `HandlerMethodValidationException` or `ConstraintViolationException`. Today the latter would hit the catch-all and become a `500`, violating "no framework-detected client fault is 500". Task 7.x asserts `size=101` → `400` naming `size`, whichever path fires. `-parameters` is already on (the Spring Boot Gradle plugin adds it); the generated `@RequestParam(value = "size")` gives the authoritative name.
- Not `422 VALIDATION_FAILED`: the platform's closed kind set collapses every other 4xx to `BAD_REQUEST`, UC-002 calls this "asked in a way that isn't allowed" (a request fault, not a body-field validation), and adding a kind would be a platform change with no consumer benefit.
- The test-only controller's existing 400 scenarios now also carry `errors` (platform ADDED requirement); `FailureKindsTest` is extended accordingly.

### D5. Search query (persistence, Criteria API)
`MovieSearchAdapter` (outbound, `@Transactional(readOnly = true)`) runs three statements per search, none of which loads more than one page of movies:
1. **Count:** `SELECT count(m.id) FROM MovieJpaEntity m WHERE <predicates>`.
2. **Page of ids:** `SELECT m.id ... WHERE <predicates> ORDER BY <order> OFFSET :offset LIMIT :size`. Skipped (empty page) when `offset ≥ count`; offset is computed as `long` so `page × size` can never overflow.
3. **Rows:** `movieJpaRepository.findAllByIdIn(ids)` with `@EntityGraph(attributePaths = "genres")`, then re-ordered in Java by the id list from step 2.

Why ids-then-rows: paging a query that fetch-joins the `genres` collection makes Hibernate page in memory (HHH90003004) and miscount; fetching ids first keeps `LIMIT/OFFSET` in SQL and still avoids N+1.

Predicates (all `AND`ed; absent criteria add nothing):
- title: `lower(m.title) LIKE :pattern ESCAPE '\'`, pattern = `'%' + escape(term.toLowerCase(ROOT)) + '%'` where `escape` prefixes `\`, `%`, `_` with `\`.
- each genre g: `EXISTS (SELECT 1 FROM MovieJpaEntity m2 JOIN m2.genres g2 WHERE m2.id = m.id AND lower(g2.name) = :g)` — one subquery per genre gives "must carry all" without `GROUP BY/HAVING` and keeps the count query trivial.
- year: `m.releaseYear >= :from`, `m.releaseYear <= :to`.
- rating: `m.rating IS NOT NULL AND m.rating >= :min` (the `>=` alone already drops NULLs; the explicit guard documents BR-4).

Order (`MovieOrder` → list of Criteria `Order`s):
| `sort` | ORDER BY |
|---|---|
| `-releaseYear` (default) | `release_year DESC, lower(title) ASC, id ASC` |
| `releaseYear` | `release_year ASC, lower(title) ASC, id ASC` |
| `title` / `-title` | `lower(title) ASC/DESC, id ASC` |
| `rating` / `-rating` | `CASE WHEN rating IS NULL THEN 1 ELSE 0 END ASC, rating ASC/DESC, lower(title) ASC, id ASC` |

- `id` is the final tiebreak (unique ⇒ total order ⇒ no repeats/skips between pages for an unchanged catalog). It is not exposed as a contract guarantee.
- Unrated-last uses a `CASE` key rather than `NULLS LAST`: JPA 3.1's Criteria API has no null-precedence, and the CASE expression is portable to H2 and PostgreSQL.
- `lower(title)` gives the case-insensitive A–Z. Where two titles differ only in case, `id` decides.
- `ResultPage` carries `page`/`size` as requested and the count; the domain computes `totalPages`.

### D6. Web adapter: mapping and links
- `MovieController.searchMovies(...)` builds a `SearchMoviesQuery` from the raw parameters (no defaults applied here), calls the use case, maps `MovieSummary` → generated `MovieSummary` DTO (same rating/absent-field rules as `getMovie`; each item's `self` via `linkTo(methodOn(MoviesApi.class).getMovie(id))`), and returns `ResponseEntity.ok().contentType(APPLICATION_JSON)` with `meta = responseMetaFactory.create(pagination)`.
- `PagedLinks` (package `adapters/in/web`, web-only, reusable later): starts from `ServletUriComponentsBuilder.fromCurrentRequest()` (honours forwarded headers via `forward-headers-strategy: framework`), **removes every query parameter not declared on the operation** (so stray params never propagate), keeps the declared ones exactly as sent (raw values, repeated `genre` preserved in order), and for each relation calls `replaceQueryParam("page", n)`. `self` keeps the request's own `page` if it was sent and omits it if not. Relations follow the platform rule (`prev` iff `1 ≤ page ≤ last + 1`, `next` iff `page < last`, `last = max(totalPages − 1, 0)`). The declared-parameter set is a constant next to the controller and asserted against the bundled spec in a test, so it cannot drift.
- Raw values are re-emitted encoded (`build().encode()` on the raw query values): a `title=100%25 Love` round-trips to the same term.

### D7. Persistence/migration plan (Flyway)
- **No migration.** The query uses existing columns (`movie.title`, `release_year`, `rating`, `movie_genre`, `genre.name`) and `movie_genre_genre_idx`. For the curated catalog's modest size (UC-002 open-question resolution), sequential scans and per-request `count` are acceptable; an index on `(release_year, title)` or a trigram index for `title` can be added later as a forward `V2__…` migration without any contract change.
- **Rollback:** code-only; revert the commit. No data or schema to undo. The added optional `Meta.pagination` / `Problem.errors` disappear with it; no client could have depended on them before this change.
- `ddl-auto: validate` is unaffected (no entity mapping changes; a new repository method only).

### D8. Read-only, runtime modes, sample data
- No `SecurityConfig` change: writes on `/movies` reach MVC and get `405` from the GET-only generated mapping. `ReadOnlyRefusalTest` adds `/movies` (MODIFIED platform requirement).
- `MovieRuntimeModeAssertions` gains the search checks; `H2DefaultRuntimeSmokeTest` asserts the four-sample default-order browse (its permitted "demo seed populates" purpose). The demo seed is unchanged; the operation `description` notes that standalone mode browses the samples.
- `InterfaceDescriptionContractTest` validates real `searchMovies` 200 (non-empty, empty, after-last), 400 (with `errors`) and 405 bodies against the served schema.
- `NoUndocumentedHttpCapabilitiesTest` needs no change (the new mapping is documented).

### Assumptions (recorded; no open questions remained in UC-002)
- **A-PAGE0:** pages are counted from `0` (`standards/openapi.md`); "a page before the first" is `page < 0`.
- **A-SORT-PARAM:** one `sort` parameter with `-` prefix for descending.
- **A-TIE:** every non-title order breaks ties by title A–Z (so explicit `-releaseYear` equals the default), then by identifier. Only the default's title secondary key is required by BR-5; the rest is the stable tiebreak.
- **A-PAGE-RANGE:** `page` and `size` are 32-bit integers; a value outside that range is ill-typed (`400`), not an after-last page.
- **A-TITLE-MAX:** a title term longer than 200 characters is refused (`400`, `title`).
- **A-STRAY-PARAMS:** unknown query parameters are ignored and are not carried onto links.
- **A-ERRORS:** parameter faults are reported as `400 BAD_REQUEST` with an `errors[]` list naming the parameters (no `422`).
- **A-COLLATION:** title order is by lower-cased title; ordering of non-ASCII titles follows the database's collation and is only guaranteed stable, not locale-correct.

## Risks / Trade-offs

- [Per-request `count(*)` and `%term%` LIKE cannot use an index] → Acceptable for a curated catalog of modest size (UC-002 resolution); forward-only `V2` index migration available without contract change.
- [PostgreSQL vs H2 collation differ for `lower(title)` ordering of punctuation/non-ASCII] → Contract guarantees only stable order; tests use ASCII titles; the runtime-mode assertions compare the sample browse in both modes.
- [Which validation path fires for `@Min/@Max` (built-in vs `@Validated` proxy) is framework-version-sensitive] → Both are handled; a web test pins `size=101`, `page=-1` and `minRating=5.5` to `400` with `errors`.
- [Generator emits an enum type for `sort`] → Codegen assertion fails fast; documented fallback in D1 keeps behaviour.
- [Catalog changes between page requests can shift items across pages] → Out of scope (curation is out-of-band and rare); the guarantee is for an unchanged catalog.
- [`errors` on all parameter-attributable 400s changes existing 400 bodies] → Additive optional member; existing contract tests still pass and are extended.
- [Large `offset` on a deep page] → Skipped entirely when `offset ≥ count`; otherwise bounded by catalog size.

## Migration Plan

1. Ship contract, code and tests together; no Flyway migration, no data change. Both runtime modes start exactly as before.
2. Rollback: revert the commit. Nothing persistent to undo.
