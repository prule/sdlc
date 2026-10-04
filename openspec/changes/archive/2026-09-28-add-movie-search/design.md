## Context

UC-000 already provides the uniform envelope (`ResponseMetaFactory`), problem handling (`GlobalExceptionHandler`, `ProblemFactory`, `ProblemKind`), correlation ids and forwarded-header-aware links. UC-001 provides the `catalog.movies` hexagon: `Movie`, `Rating`, `RuntimeMinutes`, `MovieJpaEntity`/`GenreJpaEntity`, `MoviePersistenceAdapter`, `MovieController implements MoviesApi`, the V1 schema (`movie`, `genre`, `movie_genre`) and the standalone demo seed. `standards/openapi.md` §2/§2a already sketches `Meta.pagination`, HAL `_embedded`/`_links` and link-preservation rules. This change makes those rules concrete and testable in `platform/collection-paging`. Requirements live in the spec deltas; this document covers only the how.

## Goals / Non-Goals

**Goals:** one search operation; a reusable, platform-level paging and link toolkit that the next list capability can adopt without copying; a single validation path per parameter that yields a parameter-naming `400`. The generated interface's `@Validated` and `standards/openapi.md` §2a are kept as they are.

**Non-Goals:** keyset paging, count caching, full-text indexes, and any schema migration.

## Decisions

### D1. Contract (OpenAPI 3.1, additive)

`paths/movies.yaml` gains a `movies` path item, and `openapi.yaml` gains `/movies: $ref: './paths/movies.yaml#/movies'`.

```yaml
movies:
  get:
    operationId: searchMovies
    summary: Search and browse movies
    description: >
      All parameters optional; no criteria browses the catalog. Default order:
      release year newest first, then title A–Z. Pages are zero-based.
    tags: [Movies]
    security: []
    parameters:
      - { name: title, in: query, required: false, schema: { type: string } }
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
        schema: { type: string, enum: [title, -title, releaseYear, -releaseYear, rating, -rating] }
      - $ref: '../components/parameters/common.yaml#/Page'
      - $ref: '../components/parameters/common.yaml#/Size'
    responses:
      '200':
        headers: { X-Correlation-Id: { $ref: '../components/headers/common.yaml#/X-Correlation-Id' } }
        content:
          application/json:
            schema: { $ref: '../components/schemas/movie.yaml#/MovieCollectionEnvelope' }
      '400': { $ref: '../components/responses/common.yaml#/BadRequest' }
      '406': { $ref: '../components/responses/common.yaml#/NotAcceptable' }
      '500': { $ref: '../components/responses/common.yaml#/InternalError' }
```

The new `components/parameters/common.yaml` holds the two shared parameters:
- `Page`: `page`, integer, int32, `minimum: 0`, `default: 0`.
- `Size`: `size`, integer, int32, `minimum: 1`, `maximum: 100`, `default: 20`.

`components/schemas/common.yaml` gains:
- `Pagination`: closed; `page`, `size`, `totalElements` (int64) and `totalPages`, all required.
- An optional `pagination` on `Meta`. The field is omitted when null (`non_null`).
- `CollectionLinks`: closed; `self`, `first` and `last` are required, `prev` and `next` optional. It is reusable by later collections.

`movie.yaml` gains these schemas, all closed:
- `MovieCollectionEnvelope`: `{data: MovieCollection, meta: Meta}`.
- `MovieCollection`: `{_embedded: MovieCollectionEmbedded, _links: CollectionLinks}`, both required.
- `MovieCollectionEmbedded`: `{movies: MovieSummary[]}`, required.
- `MovieSummary`: like `MovieDetail` without `synopsis`, reusing `MovieLinks`.

The change is non-breaking: the only change to an existing schema is the optional `Meta.pagination`.

### D2. Validation: one path per parameter, and every refusal names the parameter

- **Bounds are checked by the `@Validated` proxy. This is the primary, specified path.**
  - The generated `MoviesApi` is annotated `@Validated`, because `useBeanValidation` is on by default. So `MethodValidationPostProcessor` proxies `MovieController`, and Spring MVC's built-in method validation is switched off.
  - The generated `@Min`/`@Max` on `page`/`size` and `@DecimalMin`/`@DecimalMax` on `minRating` are therefore enforced before the method body runs. A violation throws `jakarta.validation.ConstraintViolationException`.
  - `standards/openapi.md` §2a is unchanged. The controller simply inherits `@Validated` from the generated interface, and task 2.1 pins that.
  - `GlobalExceptionHandler` gains `@ExceptionHandler(ConstraintViolationException.class)`. Its handler declares a `HandlerMethod` argument (Spring supplies it when the failure came from a handler; it is `null` otherwise). It classifies the exception in this order, and the first rule that applies decides the outcome:
    1. **No `HandlerMethod`:** `500`. The violation did not come from a request handler.
    2. **The violation does not belong to this handler:** `500`. A violation belongs to the handler only if **both** of these hold:
       - `violation.getRootBeanClass()` is assignable to `handlerMethod.getBeanType()`;
       - the first node of `getPropertyPath()` is a `METHOD` node whose name and parameter types match `handlerMethod.getMethod()`.

       This rules out, for example, a `@Validated` service called by the controller whose constrained parameter happens to sit at the same index as a controller `@RequestParam`.
    3. **It belongs, and a `@RequestParam` name resolves:** a `400` that names the parameter. The name is found from the index of the `PARAMETER` node that follows, via `handlerMethod.getMethodParameters()[index]`. It is taken from the `@RequestParam` annotation, which merges the interface's annotations. The Java argument name is never used. The handler returns `ProblemFactory.invalidQueryParameter(name)`: the `BAD_REQUEST` kind (same `type`, `code` and status as every other 400) with the detail `Query parameter '<name>' is invalid.` The detail never includes the value or the constraint message. When several belonging violations resolve, the one with the lowest index is named.
    4. **It belongs, but no `@RequestParam` name resolves** (for example, a constrained path variable): the generic `400`.
  - No `HandlerMethodValidationException` override is added. That path is unreachable while the interface is `@Validated`.
- **Type mismatches are framework-detected.** Non-numeric values and int32 overflow reach `handleTypeMismatch` as `MethodArgumentTypeMismatchException`. The override uses `ex.getParameter()`'s `@RequestParam` name in the same way. Path variables, such as the UC-001 id, keep today's generic detail.
- **Empty values are handled by conversion.** Spring converts an empty string to `null` for `Integer`/`BigDecimal`, so `minRating=`, `releaseYearFrom=`, `releaseYearTo=`, `page=` and `size=` are treated as absent, and the defaults apply. `sort=` stays `""`, which the domain refuses, and `genre=` is refused (see "Raw `genre` values" below).
- **Repeated single-value parameters are joined by the framework.** Spring joins repeated values of a `String` parameter with a comma. `sort=title&sort=rating` therefore becomes `title,rating`, which is refused, and `title=a&title=b` becomes the literal term `a,b`. For repeated numeric parameters, the framework's choice is not part of the contract.
- **Domain-detected failures** are an unsupported `sort`, a reversed year range and an unknown genre. They throw `InvalidSearchCriterionException(SearchCriterion)`, a new `DomainException` with code `BAD_REQUEST`, where `SearchCriterion` is an enum: `SORT`, `RELEASE_YEAR_RANGE`, `GENRE`. The domain never knows HTTP parameter names. Translation to the platform `InvalidQueryParameterException(name)` (in `platform/web`) happens in the controller, as described in D6.
- **The domain's own re-checks are a programming-error guard only.** `PageRequest` and `Rating` enforce their invariants by throwing `IllegalArgumentException`. The controller builds them only from values that the `@Validated` proxy has already bounds-checked, so user input cannot reach those throws. If one fires, it is a bug, and the catch-all `500` is correct.
- **Raw `genre` values**: the controller reads them from `HttpServletRequest.getParameterValues("genre")`, not from the generated `List<String>`. The generated `searchMovies(...)` has no request parameter, and `skipDefaultInterface=true` means there is no `getRequest()`. So the controller receives the request through **constructor injection of `HttpServletRequest`**. Spring injects a request-scoped proxy that resolves to the current request on each call. The overriding method's signature MUST stay identical to the generated one; do not add a parameter. `CollectionLinksFactory` does not need the injected request: it reads the current request through `ServletUriComponentsBuilder`/`RequestContextHolder`. Spring's String→List conversion turns `genre=` into an empty list, which would silently browse. It also splits `Drama,Sci-Fi` on commas. Reading the raw values makes `genre=` and comma forms reach the vocabulary check and be refused.
- **`sort` must be generated as `String`.** An enum-typed parameter would convert `sort=` to `null` and silently default. The codegen test pins the type.
- Alternatives rejected:
  - **Turning off bean validation and validating everything in the domain.** That contradicts the standard and the generated contract.
  - **Relying on the Java argument name in the violation path.** That is fragile, because it depends on the `-parameters` compiler flag and the generator's naming.

### D3. Domain and application (inward-only; JDK-only domain)

- `com.acme.shared.domain.paging`:
  - `PageRequest(int page, int size)` validates `page ≥ 0` and `1 ≤ size ≤ 100`, holds the constants `DEFAULT_SIZE = 20` and `MAX_SIZE = 100`, and has `long offset()`.
  - `Page<T>(List<T> items, PageRequest request, long totalElements)` has `totalPages()`. These are reusable by later list capabilities.
- `catalog.movies.domain.model`:
  - `MovieSummary`: a record of id, title, releaseYear, genres, runtime and rating.
  - `GenreNames.normalize(...)`: extracted from `Movie`, so the summary and the details sort genres identically.
  - `MovieSearchCriteria`: `Optional<String> titleTerm` (blank becomes empty; not trimmed), `Set<String> genres` (as requested), `Optional<Integer> releaseYearFrom/To` (the factory rejects from > to) and `Optional<Rating> minRating`.
  - `MovieSortOrder`: an enum `DEFAULT, TITLE_ASC, TITLE_DESC, RELEASE_YEAR_ASC, RELEASE_YEAR_DESC, RATING_ASC, RATING_DESC`, with `parse(Optional<String>)`. Absent means `DEFAULT`, and any other string throws.
  - `SearchCriterion` and `InvalidSearchCriterionException`.
- Application:
  - `SearchMoviesUseCase.search(MovieSearchCriteria, MovieSortOrder, PageRequest) → Page<MovieSummary>`.
  - `SearchMoviesService` first resolves the requested genres through `LoadGenreVocabularyPort.loadGenreNames()`, comparing with `toLowerCase(Locale.ROOT)`. Any unknown genre throws `InvalidSearchCriterionException(GENRE)` before `SearchMoviesPort.search(...)` is called. The service then passes the canonical names to the port.
- Dependency direction: the web adapter depends on application ports and domain, and the persistence adapter implements the ports. The domain imports nothing from Spring, JPA, HATEOAS or `generated`.

### D4. Ordering and the tiebreak (BR-5)

The final tiebreak is **movie id ascending** for every order. The id is the primary key, so it is unique and immutable, and the order is always total. It is not documented to consumers. Title comparisons use `lower(title)`.

| sort | ORDER BY |
|---|---|
| (absent) | `release_year DESC, lower(title) ASC, id ASC` |
| `title` / `-title` | `lower(title) ASC/DESC, id ASC` |
| `releaseYear` / `-releaseYear` | `release_year ASC/DESC, id ASC` |
| `rating` / `-rating` | `CASE WHEN rating IS NULL THEN 1 ELSE 0 END ASC, rating ASC/DESC, id ASC` |

The `CASE` key puts unrated movies last in both directions without relying on `NULLS LAST` support in Criteria. The portability of these expressions (`lower`, `CASE`, `LIKE … ESCAPE`) across H2 (`MODE=PostgreSQL`) and PostgreSQL is a design argument, not tested behaviour. Query correctness is tested on PostgreSQL only (standards/testing.md §3). Alternative rejected: title as a secondary key for the explicit sorts. BR-5 does not ask for it, and the id alone is stable.

### D5. Persistence (outbound adapter; no migration)

`MoviePersistenceAdapter` also implements `SearchMoviesPort` and `LoadGenreVocabularyPort`, using JPA Criteria over `MovieJpaEntity` with `@Transactional(readOnly = true)`. It runs three queries per search:
1. `count(*)` with the predicates.
2. The page of ids, with the predicates, the D4 order and `setFirstResult`/`setMaxResults`. It is skipped when `offset ≥ total`, which also avoids `int` overflow for huge `page` values.
3. The entities for those ids, fetching genres with `@EntityGraph`, re-ordered in memory to query 2's order.

This avoids fetch-join-plus-paging (Hibernate's in-memory paging) and N+1 queries.

**Rule:** the ids query (query 2) selects from `movie` only. It never uses `SELECT DISTINCT` and never joins `genre`/`movie_genre`; genre filtering uses `EXISTS` subqueries only. PostgreSQL rejects `ORDER BY` expressions that are not in a `DISTINCT` select list, such as `lower(title)` and the `CASE` key, and a join would duplicate rows.

Predicates:
- **Title**: `lower(title) LIKE lower(:p) ESCAPE '\'`, where `p = '%' + escape(term) + '%'` and `escape` prefixes `\`, `%` and `_` with `\`.
- **Genres**: one `EXISTS` subquery per requested genre on the `genres` join, comparing `lower(g.name) = lower(:name)`.
- **Year**: `>=` and `<=`.
- **Rating**: `rating IS NOT NULL AND rating >= :min`.

A new `GenreJpaRepository` backs the vocabulary lookup. Scans are acceptable for a curated catalog of modest size, so no new index is added.

**Flyway**: none. The schema is unchanged. **Rollback**: revert the code. No data or schema needs undoing.

### D6. Web adapter and links

`MovieController.searchMovies(...)` runs only after the `@Validated` proxy has passed (D2). It then:
1. Reads the raw `genre` values from the constructor-injected `HttpServletRequest` proxy (D2). The method signature is unchanged from the generated interface.
2. Builds the domain inputs **and** calls `useCase.search(...)` inside one translation block. That block catches `InvalidSearchCriterionException` and rethrows `InvalidQueryParameterException` with the mapped name: `SORT` becomes `sort`, `RELEASE_YEAR_RANGE` becomes `releaseYearFrom`, and `GENRE` becomes `genre`. The block covers the `GENRE` case, which is thrown inside `SearchMoviesService` during the `search` call, not while the inputs are built. `GlobalExceptionHandler` maps only the platform `InvalidQueryParameterException`. It gets no mapping for the movies-specific exception, which keeps the platform independent of `catalog`.
3. Maps each domain summary to the generated DTO. The two classes share the simple name `MovieSummary`. The controller imports the generated `com.acme.generated.model.MovieSummary` and refers to the domain type by its fully qualified name, `com.acme.catalog.movies.domain.model.MovieSummary`, or the reverse, but consistently within the file. Nothing is renamed in the contract. Each `self` link uses the same `linkTo(methodOn(MoviesApi.class).getMovie(id))` as UC-001, so the hrefs are identical. Ratings use `stripTrailingZeros()`.
4. Returns `200 application/json`.

Links come from a new, reusable `platform/web/CollectionLinksFactory.create(Page<?> page, Set<String> recognisedParams)`:
- The base is `ServletUriComponentsBuilder.fromCurrentRequestUri()`, which honours `forward-headers-strategy: framework`.
- The query is parsed from the raw `request.getQueryString()`, so values stay exactly as encoded and in first-appearance order. The factory keeps only the recognised names, drops `page`, and appends `page=<n>`.
- `lastPage = max(totalPages − 1, 0)`. `prev` is added when `0 < page ≤ lastPage`, and `next` when `page < lastPage`.

`ResponseMetaFactory` gains `create(Pagination)`.

## Risks / Trade-offs

- [Generator output differs from assumptions: `sort` as an enum, or no `@Min`/`@Max` on the parameters] → `GeneratedApiCodegenTest` asserts the `searchMovies` signature, that `sort` is a `String`, and the constraint annotations. Web-slice tests assert the naming `400`s.
- [The generator stops emitting `@Validated`, or the validation path changes (for example, `useBeanValidation` is turned off)] → `GeneratedApiCodegenTest` asserts `@Validated` on `MoviesApi`. Slice tests through the real `MovieController` assert the parameter-naming 400s for the bounds cases.
- [The count and ids queries can disagree if the catalog changes between them] → acceptable, because the data is curated and changes out-of-band and rarely. The next request is consistent again.
- [UUID tiebreak or `lower()` order could differ between H2 and PostgreSQL] → the tiebreak is not a consumer contract. Ordering is tested on PostgreSQL only, and the standalone H2 mode is a demo runtime.
- [`lower()` differs by DB collation for non-ASCII titles] → acceptable. Tests use ASCII, and BR-5 only requires stability.
- [Count plus page query per request] → acceptable at curated-catalog scale (UC-002 note). Keyset paging is a non-goal.
- [Genre names that differ only in letter case in the vocabulary] → matching treats them as the same genre, because the `EXISTS` subquery compares lowercased names. See the open questions.

## Open Questions

- Should the curated `genre` table enforce case-insensitive uniqueness of names? Nothing prevents `Drama` and `drama` today, and this change treats such duplicates as one genre. A DB constraint would need an expression index, which H2 does not support, so it is deferred to the curation or genres-keywords work (UC-007). It does not affect this change's specs or tasks.
