## Context

See proposal.md (Why). The tree has the UC-000 platform and the UC-001 `catalog.movies` slice:
- `Movie` aggregate, `GetMovieUseCase`, `LoadMoviePort`;
- `MoviePersistenceAdapter` over `MovieJpaEntity`/`GenreJpaEntity` with `@ManyToMany` genres;
- `MovieController implements MoviesApi`.

The schema is `V1__create_movie_catalog.sql`: `movie`, `genre` (unique `name`) and `movie_genre`. The standalone seed `R__demo_movies.sql` holds 4 movies.

The platform constraints that bind this change:
- success is always `application/json`;
- schemas are closed (`additionalProperties: false`) and validated at runtime by `InterfaceDescriptionContractTest`;
- the failure kinds are a closed set, classified by status (`ProblemKind`), with fixed safe details;
- only `H2DefaultRuntimeSmokeTest` may boot H2;
- unknown exceptions go to the catch-all `500`.

Codegen is openapi-generator 7.14 `spring` with `interfaceOnly`, `useTags` and Bean Validation on by default. It therefore emits `@Min`/`@Max`/`@DecimalMin`/`@DecimalMax` on parameters, and it emits `@Validated` on the generated interface (confirmed, and pinned by `GeneratedApiCodegenTest`). The stack is Spring Boot 3.5 (Spring Framework 6.2).

## Goals / Non-Goals

**Goals:**
- One public read-only collection operation satisfying the `catalog/movies` search requirements.
- A reusable paging form: contract components, a domain page type and link assembly.
- Every refusal is `400` with the parameter named and no search performed. No bounds violation ever reaches the catch-all `500`.
- No schema migration.

**Non-Goals:**
- No caching, no total-count optimisation, no full-text index, no new problem kind or code.
- No change to `GET /movies/{id}` behaviour other than the `400` detail wording.

## Decisions

### D1. Contract (added operation `GET /movies`, shared paging components)
What changes in the contract:
- Added: `movies` in `paths/movies.yaml`; `components/parameters/common.yaml` (`page`, `size`); `Pagination` in `schemas/common.yaml`; search schemas in `schemas/movie.yaml`.
- Changed (additive): `Meta` gains an optional `pagination`.
- Nothing removed.

```yaml
# openapi.yaml (addition; tag Movies description becomes "Search movies and retrieve their details")
paths:
  /movies:
    $ref: './paths/movies.yaml#/movies'

# paths/movies.yaml (addition)
movies:
  get:
    operationId: searchMovies
    summary: Search and browse movies
    description: >
      Returns one page of movie summaries matching every given criterion (all optional;
      none = browse the whole catalog). Default order: release year newest first, then
      title A–Z. Unrated movies always sort last when ordering by rating. A page after
      the last is an empty page, not an error. Navigation links repeat the request's
      criteria, order and size exactly as given.
    tags: [Movies]
    security: []
    parameters:
      - name: title
        in: query
        description: Case-insensitive "contains" match on the title. Blank = no criterion.
        schema: { type: string }
      - name: genre
        in: query
        description: >
          Curated genre name, case-insensitive. Repeat (or comma-separate) for several;
          a movie must carry all of them. Unknown genre = 400.
        style: form
        explode: true
        schema: { type: array, items: { type: string } }
      - name: releaseYearFrom
        in: query
        description: Inclusive lower release-year bound.
        schema: { type: integer, format: int32 }
      - name: releaseYearTo
        in: query
        description: Inclusive upper release-year bound; must not be before releaseYearFrom.
        schema: { type: integer, format: int32 }
      - name: minRating
        in: query
        description: Inclusive minimum rating on the 0–5 scale; unrated movies are excluded.
        schema: { type: number, minimum: 0, maximum: 5 }
      - name: sort
        in: query
        description: Order; prefix "-" for descending. Default -releaseYear (then title A–Z).
        schema:
          type: string
          enum: [title, -title, releaseYear, -releaseYear, rating, -rating]
      - $ref: '../components/parameters/common.yaml#/page'
      - $ref: '../components/parameters/common.yaml#/size'
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
page:
  name: page
  in: query
  description: Zero-based page index.
  schema: { type: integer, format: int32, minimum: 0, default: 0 }
size:
  name: size
  in: query
  description: Page size.
  schema: { type: integer, format: int32, minimum: 1, maximum: 100, default: 20 }

# components/schemas/common.yaml (Meta gains optional pagination; new Pagination)
Meta:
  # ...existing required [timestamp, correlationId], additionalProperties: false
  properties:
    # ...timestamp, correlationId unchanged
    pagination: { $ref: '#/Pagination' }
Pagination:
  type: object
  required: [page, size, totalElements, totalPages]
  additionalProperties: false
  properties:
    page:          { type: integer, format: int32, minimum: 0 }
    size:          { type: integer, format: int32, minimum: 1, maximum: 100 }
    totalElements: { type: integer, format: int64, minimum: 0 }
    totalPages:    { type: integer, format: int32, minimum: 0 }
PageLinks:                       # shared: every paged list reuses it
  type: object
  required: [self, first, last]
  additionalProperties: false
  properties:
    self:  { $ref: '#/Link' }
    first: { $ref: '#/Link' }
    prev:  { $ref: '#/Link' }
    next:  { $ref: '#/Link' }
    last:  { $ref: '#/Link' }

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
    _links:    { $ref: './common.yaml#/PageLinks' }
MovieSearchEmbedded:
  type: object
  required: [movies]
  additionalProperties: false
  properties:
    movies: { type: array, items: { $ref: '#/MovieSummary' } }
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
```

**Schema choices.**
- `MovieSummary` reuses `MovieLinks` (`self` only).
- `PageLinks` lives in `common.yaml` because BR-7 makes it the shared paging convention.
- `Pagination` follows `standards/openapi.md` §2. With the global `non_null` inclusion, ping and movie-detail responses omit `pagination`.
- No `uniqueItems` on `genres`, for the same reason as UC-001 D1.
- `minRating` without `format` generates `BigDecimal`.

**The `sort` parameter.** `sort` uses an inline enum, so the published description lists the six values. Task 2.1 asserts that the generator binds `sort` as a plain `String`. The domain then parses it, which makes `sort=TITLE` (case-sensitive) and unknown values a named `400` (D4). If the generator instead emits an enum type, the codegen test fails and the implementer must register a strict `@InitBinder` editor for that type, as in UC-001 D4. Spring's lenient `Enum.valueOf` fallback would otherwise accept constant names.

**Defaults.** `default: 0`/`default: 20` make the generator use `@RequestParam(defaultValue=…)`. Link assembly therefore never relies on the bound values to know what the client sent (D6).

`GeneratedApiCodegenTest` asserts that `MoviesApi.searchMovies(...)` returns `ResponseEntity<MovieSearchEnvelope>`, that `genre` is a `List<String>`, `minRating` is a `BigDecimal` and `sort` is a `String`, that `page`/`size` carry `@Min`/`@Max` and `minRating` carries `@DecimalMin`/`@DecimalMax`, that `Meta.getPagination()` exists, and that no `SearchMovies*Response*` class exists. It also asserts that the generated `MoviesApi` interface carries `@Validated`. This is the observed generator output, so a generator upgrade that drops it fails the build and is reviewed against D3.

### D2. Components and dependency direction (inward only)
```
com.acme.shared.domain               (shared kernel: reused by every later paged list, e.g. UC-005/UC-006)
├── InvalidRequestException          final, extends DomainException; code BAD_REQUEST;
│                                    carries field() — the published name of the request element at fault
├── PageSpec                         record: int page ≥ 0, int size 1..100; long offset()
│                                    (named PageSpec, not PageRequest, to avoid clashing with Spring Data's
│                                    org.springframework.data.domain.PageRequest in adapter code)
└── ResultPage<T>                    record: List<T> items, int page, int size, long totalElements;
                                     int totalPages(), boolean hasNext(), hasPrevious(), int lastPage()
com.acme.platform.web
└── PageLinkBuilder                  generic page-link assembly from the current request (D6); no catalog import
com.acme.catalog.movies
├── domain/model/  MovieSearchCriteria (record: Optional<String> titleTerm, Set<String> genres,
│                    Optional<Integer> releaseYearFrom/To, Optional<BigDecimal> minRating)
│                  MovieSortOrder (record: SortField {TITLE, RELEASE_YEAR, RATING}, boolean descending;
│                    static parse(Optional<String>) — default -releaseYear)
├── application/port/in/SearchMoviesUseCase        ResultPage<Movie> search(SearchMoviesQuery q)
│   SearchMoviesQuery (record of raw-but-typed inputs: title, List<String> genres, Integer from/to,
│                      BigDecimal minRating, String sort, int page, int size)
├── application/port/out/SearchMoviesPort          ResultPage<Movie> search(MovieSearchCriteria, MovieSortOrder, PageSpec)
├── application/port/out/LoadGenreVocabularyPort   List<String> loadGenreNames()
├── application/service/SearchMoviesService        builds sort, page, criteria (each validates and throws
│                                                  InvalidRequestException); resolves genres against the
│                                                  vocabulary (case-insensitive → canonical; unknown → "genre")
├── adapters/out/persistence/MovieSearchAdapter    implements SearchMoviesPort + LoadGenreVocabularyPort
└── adapters/in/web/MovieController (@Validated; + searchMovies), MovieSearchLinks (declares the
                   recognised movie-search parameter names and delegates to PageLinkBuilder)
```

**Validation order and blank values.** Validation runs in this fixed order: `sort`, `page`/`size`, `minRating`, release-year range, `genre`. When several parameters are wrong, the first one in this order is named. Blank `title` and blank `genre` values are dropped in `MovieSearchCriteria`.

**Domain-level bounds.** The domain re-checks `page`, `size` and `minRating` bounds. The framework normally catches them first (D3), but the rules stay unit-testable and correctness doesn't depend on the generator's annotations.

**Summaries reuse the `Movie` aggregate.** The controller maps to `MovieSummary` and drops the synopsis. This is the smallest change. Loading a synopsis per row is negligible at this catalog size. The alternative, a separate `MovieSummary` domain type plus a projection query, was rejected for now; it can be introduced later without any contract change.

**Dependency direction.**
- Domain imports only the JDK and the shared kernel.
- The application layer imports domain and ports.
- The adapters implement the ports.
- The platform imports only `com.acme.shared.domain`, never `catalog`, the same as the UC-001 `ResourceNotFoundException` precedent.
- Link assembly stays in the web adapter layer (`standards/openapi.md` §2a). The generic part, `PageLinkBuilder`, lives in `com.acme.platform.web` next to `ResponseMetaFactory`. It takes only the current request, a list of recognised parameter names and page numbers, so it imports neither `catalog` nor any domain type. `MovieSearchLinks` in `catalog.movies.adapters.in.web` supplies the movie-search parameter names.

### D3. 400 mapping and parameter naming (platform)
**Why 400, not 422.** `standards/error-handling.md` §3 maps "Malformed request / bad param" to `400` `BAD_REQUEST`. `422` `VALIDATION_FAILED` is for request-*body* field validation (`MethodArgumentNotValidException`), and this operation has no body. Every search refusal is a bad query parameter, so it is `400`. Do not "fix" this to `422`. The shared `BadRequest` response description in `components/responses/common.yaml` becomes "The request could not be understood, or a request parameter is not valid." so the published contract says the same.

**ProblemFactory.** `ProblemFactory` gains `createBadRequest(String parameterName)`. It returns the `BAD_REQUEST` kind with detail `The request parameter '<name>' is not valid.`. The name always comes from code: the declared `@RequestParam`/`@PathVariable` name or `InvalidRequestException.field()`. It never comes from client input. That is why unrecognised parameters can never appear in a detail.

**`@Validated` on the controller (follows `standards/openapi.md` §2a).** `MovieController` is class-annotated `@Validated`. Bean Validation on generated parameters (`@Min`/`@Max`/`@DecimalMin`/`@DecimalMax`) is therefore enforced by Spring's AOP `MethodValidationPostProcessor`, which throws `jakarta.validation.ConstraintViolationException` before the handler body runs. The generator's default `useBeanValidation=true` also puts `@Validated` on `MoviesApi`, which activates the same path. This is confirmed, and `GeneratedApiCodegenTest` (task 2.1) pins it, so the behaviour is not left to chance. The `ConstraintViolationException` mapping is therefore the **main path**. `HandlerMethodValidationException`, from Spring MVC's built-in method validation, is mapped as a **safety net**, so a bounds violation is never `500` whichever path runs.

**Handlers in `GlobalExceptionHandler`.**

| Failure | Override or handler | Parameter named |
|---|---|---|
| `jakarta.validation.ConstraintViolationException` (main path) | new `@ExceptionHandler`, narrowed as below | the selected violation's request-parameter name |
| `HandlerMethodValidationException` (safety net) | override `handleHandlerMethodValidationException` | the invalid parameter with the lowest index, resolved the same way |
| `MethodArgumentTypeMismatchException` | override `handleTypeMismatch` | `getName()` |
| `MissingServletRequestParameterException` | override `handleMissingServletRequestParameter` | `getParameterName()` |
| `InvalidRequestException` | new `@ExceptionHandler` | `field()` |

All of these log at DEBUG, except the `500` fall-through, which logs at ERROR like any other server fault.

**Narrowing the `ConstraintViolationException` mapping.** A `ConstraintViolationException` is a client fault only when it comes from validating a web handler's parameters. Elsewhere (for example a service or domain-level `@Validated` bean, or a JPA entity) it is a server fault and stays `500`. The handler maps it to `400` only when **every** violation satisfies both conditions:
- its root bean class (`getRootBeanClass()`, unwrapped from any proxy with `AopUtils.getTargetClass`/`ClassUtils.getUserClass`) is annotated `@RestController`;
- its property path contains a `ParameterNode` (`ElementKind.PARAMETER`), meaning a method parameter, not a return value or a bean property.

Otherwise the handler delegates to the existing catch-all, `handleUnexpected`, so the `500` behaviour, generic detail and ERROR log are unchanged.

**Choosing one violation.** The order of `getConstraintViolations()` is not defined. The handler therefore picks the violation whose `ParameterNode.getParameterIndex()` is lowest. If several share that index, it picks the one whose constraint annotation simple name sorts first. The result is the same on every run.

**Mapping the Java parameter name to the published name.** The handler resolves the method from the path's `MethodNode` (name plus parameter types) on the root bean class, and reads the `@RequestParam`/`@PathVariable` `name`/`value` at the violation's parameter index. It searches the class hierarchy and interfaces with `AnnotatedElementUtils`/`MethodParameter`, because the annotations live on the generated `MoviesApi`. If no annotation name can be found, the handler gives the generic `400` (`The request could not be understood.`). It never falls back to the Java parameter name, which is an implementation detail. The same resolution serves the `HandlerMethodValidationException` safety net, through `ParameterValidationResult.getMethodParameter()`.

**Effect on UC-001.** UC-001's malformed id now gets detail `The request parameter 'id' is not valid.`. The status, `code` and `type` are unchanged. The existing UC-001 malformed-id tests are extended to assert the new detail (task 6.5).

### D4. Domain rules
**Sort.** `MovieSortOrder.parse` accepts exactly the six strings, case-sensitive. Anything else throws `InvalidRequestException("sort")`.

**Range.** `MovieSearchCriteria`'s factory throws `InvalidRequestException("releaseYearFrom")` when the lower bound is after the upper bound.

**Genres.** The service matches genres against `loadGenreNames()` using `toLowerCase(Locale.ROOT)`. It replaces each with its canonical name and de-duplicates. The genre vocabulary is curated and small, so loading it whole is one cheap query.

**No search after a refusal.** Every check runs before `SearchMoviesPort.search` is called. A refused search never queries the movie table, except for the vocabulary read.

### D5. Persistence query (no migration)
`MovieSearchAdapter` (`@Transactional(readOnly = true)`) uses the JPA Criteria API through `EntityManager`. It never concatenates strings into JPQL. It runs three queries:

1. **Count.** `select count(m) from MovieJpaEntity m where <predicates>`. If `offset >= total`, it returns an empty `ResultPage` immediately. Offset arithmetic is `long`, so a large `page` cannot overflow `setFirstResult` (it is never reached).
2. **Id page.** `select m.id … where <predicates> order by <orders>` with `setFirstResult((int) offset)` and `setMaxResults(size)`.
3. **Hydrate.** `MovieJpaRepository.findAllByIdIn(ids)` with `@EntityGraph("genres")`, re-ordered in memory to match the id list.

Splitting the id page from hydration avoids Hibernate's in-memory pagination over a collection fetch (HHH90003004) and N+1 queries.

**Predicates.**

| Criterion | Predicate |
|---|---|
| Title | `lower(m.title) like lower(:pattern) escape '\'`. The pattern is `%` + the term with `\`, `%` and `_` escaped + `%`. The lowering is done by the database on both sides, so case folding is consistent. |
| Genres (n canonical names) | `m.id in (select mg.id from MovieJpaEntity mg join mg.genres g where g.name in :names group by mg.id having count(distinct g.id) = :n)` |
| Year | `m.releaseYear >= :from`, `m.releaseYear <= :to` |
| Rating | `m.rating >= :min`. A `NULL` rating never satisfies it, so unrated movies are excluded. |

**Orders.**

| Sort | `order by` |
|---|---|
| `title` | `lower(m.title) asc, m.id asc` (`desc` for `-title`) |
| `releaseYear` | `m.releaseYear asc, lower(m.title) asc, m.id asc` (`desc` on year only for `-releaseYear`) |
| `rating` | `m.rating asc nulls last, lower(m.title) asc, m.id asc` (`desc nulls last` for `-rating`) |

**Title collation.** `lower(m.title)` is compared with the database's default collation. That collation can differ between PostgreSQL (the container's locale, typically `en_US.UTF-8`, which largely ignores spaces and punctuation at the first comparison level) and H2 (code-point order). So titles that differ in spaces, punctuation or accents (`The Ring` vs `Theater`, `Up!` vs `Upside`) may sort differently between the two modes. A fixed collation (PostgreSQL `COLLATE "C"`) was considered and rejected: collation syntax is dialect-specific and cannot be expressed portably through the Criteria API, and byte order sorts punctuation in ways users find surprising. Instead:
- Ordering correctness is asserted only on Testcontainers PostgreSQL, the production engine.
- Ordering fixtures (tasks 5.2 and 7.2) use titles made only of letters and digits, distinct in their first differing letter, with no comparisons that hinge on spaces, punctuation or accents.
- The H2 smoke test asserts only the default order of the four sample movies, which have distinct release years, so collation never decides it.

`m.id` is the final tiebreak (BR-5). Ids are unique and immutable, so the order is complete and stable. `NULLS LAST` is expressed with `HibernateCriteriaBuilder.asc(expression, nullsFirst)`/`desc(expression, nullsFirst)` with `nullsFirst = false`, in both directions. It works on PostgreSQL and H2 `MODE=PostgreSQL`.

**Bound values.** Every client value is a bound parameter. The title pattern goes through `HibernateCriteriaBuilder.value(...)`, not `literal(...)`, because Hibernate renders a criteria literal inline in the SQL text.

**Vocabulary.** `loadGenreNames()` is `select g.name from GenreJpaEntity g`.

**Migration plan.** None. The queries use existing columns.
- Indexes were considered and rejected for now. `%term%` cannot use a btree. Ordering over a curated catalog of modest size is a trivial sort. `movie_genre_genre_idx` already serves the genre subquery.
- If the catalog grows, a forward `V2__…` can add a `pg_trgm` GIN index on `lower(title)` and btree indexes on `release_year` and `rating`. These are additive and need no code change.
- **Rollback:** reverting the code is enough. There is no schema to undo.

### D6. Link assembly (web adapter)
**Generic helper.** `com.acme.platform.web.PageLinkBuilder` is a reusable `@Component`. Its input is the set of recognised parameter names (an ordered list), the page number, `lastPage`, `hasPrevious` and `hasNext`. Its output is the five hrefs (`self`, `first`, `last`, `prev`/`next` or empty). Every later paged list (filmography, people search) reuses it with its own parameter names. `MovieSearchLinks` in the movie web adapter declares `title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`, `size`, calls the builder, and maps the result to the generated `PageLinks` DTO.

**How hrefs are built.** `PageLinkBuilder` takes the current request URI (including the `/api/v1` context path) and applies `ForwardedHeaderUtils.adaptFromForwardedHeaders`. With `forward-headers-strategy: framework`, `ForwardedHeaderFilter` has already applied and removed the forwarded headers, so in the running service this step changes nothing. It lets the unit test drive forwarded scheme and host with a plain mock request. It discards the incoming query string and re-adds only the recognised parameters from `HttpServletRequest.getParameterMap()`, in the given fixed order. Each value is copied verbatim, so repeated `genre` values and blank values stay as sent.

**Page values per link.**
- `self` re-adds `page` only if the request had it, using the raw value.
- `first` is `page=0`.
- `last` is `page=max(totalPages−1, 0)`.
- `prev` is `page−1` and is present only if `0 < page ≤ lastPage`.
- `next` is `page+1` and is present only if `page < lastPage`.

**Encoding.** Each parameter name and value is percent-encoded with `UriUtils.encode(value, UTF-8)`, which encodes every character outside the unreserved set. The href is then built with `build(true)` (already encoded) and `toUri()`. `UriComponentsBuilder.encode()` is not used because it leaves `&` and `+` unencoded in query values, so they would not round-trip. A title containing `&`, `%`, `+` or a space must round-trip, and a unit test asserts this.

**Not used.** `linkTo(methodOn(...).searchMovies(...))` would serialise bound (defaulted) values, which the "defaults are not written into links" rule forbids. Entry `self` links reuse UC-001's `linkTo(methodOn(MoviesApi.class).getMovie(id))`.

`ResponseMetaFactory` gains `create(Pagination)`. The controller forces `Content-Type: application/json`, as `getMovie` does.

### D7. Read-only, security, runtime modes
**Read-only and security.** `SecurityConfig` needs no change; it is already `permitAll`. The generated GET-only mapping on `/movies` makes writes `405`. `ReadOnlyRefusalTest` adds `/movies`.

**Standalone seed.** The seed is unchanged. Its 4 movies give a deterministic default order for the H2 smoke test (`Arrival` 2016, `The Grand Heist` 2005, `Laugh Track` 1998, `Untitled Reel` 1974). It also exercises nulls-last ordering (`Untitled Reel` is unrated) and an empty genre list.

**Mode tests.** `MovieRuntimeModeAssertions` adds the unknown-genre `400` and no-match `200` checks. No new H2 test is added.

### Assumptions (need confirmation at Gate 1)
- **A-PAGE0:** pages are zero-based (`page=0` is the first), following `standards/openapi.md` §2a. "A page before the first" therefore means `page < 0`.
- **A-SORT-SYNTAX:** `sort` uses a single parameter with a `-` prefix for descending, rather than Spring-style `sort=field,dir` or two parameters.
- **A-YEAR-PARAMS:** the year range is two parameters, `releaseYearFrom` and `releaseYearTo`. A single year is expressed as equal bounds. No separate `releaseYear` parameter is added.
- **A-BLANK-GENRE:** a blank `genre` value is ignored, like a blank title, rather than refused. Comma-separated genres are accepted as the framework's list binding does.
- **A-TITLE-NOTRIM:** the title term is not trimmed, so `" arrival"` requires the space.
- **A-SECONDARY:** for the year and rating orders, ties are broken by title A–Z before the id tiebreak.
- **A-UNKNOWN-PARAMS:** unrecognised query parameters are ignored, not refused, and are dropped from links.
- **A-DETAIL:** a refused parameter is named in the `detail` text, not in a new problem member such as `errors[]`. This keeps the closed `Problem` schema unchanged. The wording change also applies to UC-001's `400`.

## Risks / Trade-offs

- [Collation differences between PostgreSQL and H2: spaces, punctuation and accents, not only non-ASCII] → Titles that differ only in those may order differently in standalone mode than in persistent mode. Ordering is verified on PostgreSQL only, ordering fixtures avoid such titles, and the H2 smoke test relies on distinct years (D5). The difference is confined to standalone (evaluation) mode, so it is accepted. It is also listed as an open question.
- [`count(*)` on every request] → Required by BR-6. Cheap at this catalog size, as UC-002 acknowledges.
- [Leading-wildcard `LIKE` is a full scan] → Accepted at this size. A trigram index is the documented upgrade path (D5).
- [The generator emits an enum type for `sort`, or changes whether `MoviesApi` carries `@Validated`] → The codegen test pins both. `MovieController` is `@Validated` regardless, both validation paths are mapped (D3), and the domain re-validates bounds.
- [The narrowed `ConstraintViolationException` mapping misclassifies a server-side violation as `400`] → It maps only violations on `@RestController` method parameters, and a test asserts that a violation from a non-controller bean stays `500` (task 6.1).
- [The request parameter map echoes client-supplied values into links] → Values are URL-encoded inside a JSON string and limited to recognised parameter names. They are not HTML, so there is no injection surface. Problem bodies never echo values.
- [A changed `400` detail breaks a client matching on text] → `detail` is human-readable by contract. `code` and `type` are unchanged.
- [`Meta` gains a property] → It is optional and additive, and the closed schema still rejects anything else. `InterfaceDescriptionContractTest` re-validates ping and movie responses.

## Open Questions

- **Q-TITLE-MAXLEN:** should `title` have a published maximum length (for example `maxLength: 200`, which would refuse longer terms with `400`)? The use case sets no limit. None is added in this change, so a very long term is simply matched (and matches nothing). Adding a limit later would make some requests that succeed today fail, so it is better decided now.
- **Q-COLLATION:** is it acceptable that title ordering may differ between standalone (H2) and persistent (PostgreSQL) mode for titles differing only in spaces, punctuation or accents (D5)? The alternative is a fixed collation in both databases, which needs a dialect-specific query or a migration.

## Migration Plan

1. Ship the contract and code together. There is no DB migration.
2. Rollback: revert the commit. No data or schema to undo.
