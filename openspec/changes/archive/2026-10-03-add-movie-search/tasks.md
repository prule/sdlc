## 1. OpenAPI contract

- [x] 1.1 Add the shared paging pieces:
  - `components/parameters/common.yaml` with `page` and `size`;
  - `Pagination` and `PageLinks` in `components/schemas/common.yaml`;
  - an optional `pagination` on `Meta`.

  Follow design D1. Verify: `npx redocly lint` passes.
- [x] 1.2 Change the shared `BadRequest` response description in `components/responses/common.yaml` to "The request could not be understood, or a request parameter is not valid." (design D3). Add `MovieSearchEnvelope`, `MovieSearchPage`, `MovieSearchEmbedded` and `MovieSummary` (all closed) to `components/schemas/movie.yaml`. Add `movies` (`searchMovies`, `security: []`, responses 200/400/406/500) to `paths/movies.yaml`. Add the `/movies` `$ref` and the updated `Movies` tag description to `openapi.yaml`. Verify: `./gradlew bundleOpenApiSpec` succeeds, and the bundle has `/movies` with no external `$ref`.

## 2. Generate stubs

- [x] 2.1 Write the failing assertions in `GeneratedApiCodegenTest` first:
  - `MoviesApi.searchMovies` returns `ResponseEntity<MovieSearchEnvelope>`;
  - its `genre` parameter is a `List<String>`, `minRating` is a `BigDecimal`, and `sort` is a `String`;
  - `Meta.getPagination()` exists;
  - no `SearchMovies*Response*` class exists;
  - whether the generated `MoviesApi` interface carries `@Validated`, asserted as the observed fact (expected: present, given the generator's default `useBeanValidation=true`), so a generator upgrade that changes it fails the build and is reviewed against design D3;
  - the `page`/`size` parameters carry `@Min`/`@Max` and `minRating` carries `@DecimalMin`/`@DecimalMax`.

  Then run `./gradlew openApiGenerate`. If `sort` is generated as an enum, apply the D1 fallback. Verify: `./gradlew test --tests '*GeneratedApiCodegenTest'` passes.

## 3. Domain (test-first)

- [x] 3.1 Write failing unit tests for the shared-kernel types in `com.acme.shared.domain` (all JDK-only), then implement them until green:
  - `InvalidRequestException`: its code is `BAD_REQUEST` and it exposes `field()`;
  - `PageSpec`: the bounds 0, 1, 100 → accepted; -1 → `InvalidRequestException("page")`; 0 and 101 → `InvalidRequestException("size")`; `offset()` as a `long`, for example page `Integer.MAX_VALUE` at size 100;
  - `ResultPage<T>`: `totalPages` for 0, 5 and 25 matches at size 2/20; `hasNext`/`hasPrevious`/`lastPage` on the first, middle and last pages, a page after the last, and an empty result.
- [x] 3.2 Write failing unit tests for `MovieSortOrder.parse`:
  - each of the six values;
  - absent → `-releaseYear`;
  - `TITLE`, `popularity` and empty → `InvalidRequestException("sort")`.

  Implement until green.
- [x] 3.3 Write failing unit tests for `MovieSearchCriteria`:
  - a blank title becomes absent;
  - blank genres are dropped;
  - `minRating` 0 and 5 are accepted, while -0.1 and 5.1 → `minRating`;
  - from > to → `releaseYearFrom`;
  - from == to is allowed.

  Implement until green.

## 4. Application and ports (test-first)

- [x] 4.1 Write failing unit tests for `SearchMoviesService`, with `SearchMoviesPort` and `LoadGenreVocabularyPort` mocked:
  - `drama` and `DRAMA` resolve to canonical `Drama` and are de-duplicated;
  - an unknown genre → `InvalidRequestException("genre")`, and the search port is never invoked;
  - a reversed range and a bad sort, page or size → the port is never invoked;
  - when several parameters are invalid, the first in the D2 order is named;
  - the valid path passes the criteria, sort and page through and returns the port's page.

  Then add `SearchMoviesUseCase`, `SearchMoviesQuery`, both ports and the service until green.

## 5. Outbound adapter (no migration)

- [x] 5.1 Add `findAllByIdIn` with `@EntityGraph(attributePaths = "genres")` to `MovieJpaRepository`. Then write a failing Testcontainers test for `MovieSearchAdapter` (extending `PostgresIntegrationTest`, with `JdbcTemplate` fixtures) covering the filters:
  - title substring ignoring case;
  - the literal `%`, `_` and `\`;
  - all-of genres;
  - a movie without genres excluded by a genre filter;
  - inclusive and open-ended year ranges;
  - `minRating` inclusive, with unrated movies excluded;
  - an empty catalog;
  - `loadGenreNames`.

  Implement the count and predicates (design D5) until green.
- [x] 5.2 Write failing Testcontainers tests for ordering and paging:
  - each of the six sort orders, asserted as a full id sequence, including unrated movies last in both directions;
  - the default order;
  - a `size=2` walk over ties (same year, same rating, same title) whose concatenation equals the `size=100` result;
  - the last partial page;
  - a page after the last, which returns empty items, the total, and no id or hydrate query.

  Ordering fixtures use titles of letters and digits only, distinct at their first differing letter, with no ordering that hinges on spaces, punctuation or accents (design D5, collation). Implement the id page and hydration until green. Verify: `./gradlew test --tests '*MovieSearchAdapterTest'` passes. (Criteria queries are built per request, not checked at boot; the H2 dialect is exercised by the search requests in task 7.3.)

## 6. Inbound web (test-first)

- [x] 6.1 Write failing platform web tests first, using the test-only controller (class-annotated `@Validated`, like `MovieController`).
  - Add:
    - an operation with two bounded query parameters, `@RequestParam("first-param") @Max(10) int a` and `@RequestParam("second-param") @Max(10) int b`, where the published names differ from the Java names;
    - an operation that throws `InvalidRequestException("thing")`;
    - an operation that calls a test-only `@Validated` non-controller `@Component` whose method parameter is `@Max(1)`, passing it `2`.
  - Assert:
    - one out-of-bounds value → `400` and `The request parameter 'first-param' is not valid.` (the published name, not `a`);
    - both out of bounds → `first-param` is named, on every one of several repeated requests (lowest parameter index wins);
    - the UUID parameter, malformed and omitted → the named detail for `id`;
    - the thrown `InvalidRequestException` → `400` naming `thing`;
    - the non-controller constraint violation → `500` `INTERNAL_ERROR` with the generic detail, logged at ERROR;
    - none of the `400` details echoes the value.
  - Update the existing `FailureKindsTest` 400 expectations accordingly.

  Then add `ProblemFactory.createBadRequest(String)` and the D3 handlers until green: the narrowed `ConstraintViolationException` handler (main path, `@RestController` root bean plus `ParameterNode`, lowest index, annotation-name resolution, otherwise delegate to `handleUnexpected`), the `HandlerMethodValidationException` safety net, type mismatch, missing parameter and `InvalidRequestException`. Add a unit test for the safety net that builds a `HandlerMethodValidationException` directly and asserts the named `400`, because with `@Validated` the MVC path may not otherwise be exercised.
- [x] 6.2 Write failing unit tests for the generic `com.acme.platform.web.PageLinkBuilder` (design D6), driven by a mock request and an arbitrary recognised-name list:
  - recognised parameters are kept verbatim and in a fixed order, including repeated `genre`;
  - defaults are absent;
  - an unrecognised parameter is dropped;
  - `self` keeps the raw `page`, or no `page` when the request had none;
  - `first`, `last`, `prev` and `next` page values on the first, middle and last pages, a page after the last, and an empty result;
  - forwarded scheme and host are honoured;
  - a title with `&`, `%`, `+` and a space round-trips.

  Implement `PageLinkBuilder`, then `MovieSearchLinks` (the movie parameter names plus mapping to `PageLinks`) until green.
- [x] 6.3 Write failing `@PlatformWebTest` slice tests for `MovieController.searchMovies`, with `SearchMoviesUseCase` mocked:
  - the envelope shape: `_embedded.movies`, `_links` and `meta.pagination`;
  - the summary has no `synopsis`, absent optional fields are absent keys, `genres` `[]`, and the rating literals are `4.5` and `5`;
  - each entry's `self` link;
  - `Accept: application/problem+json` → `200` `application/json`;
  - the arguments passed to the use case for defaults and for explicit values.

  Add `ResponseMetaFactory.create(Pagination)`, then implement `searchMovies` on the `@Validated` `MovieController` until green.
- [x] 6.4 Write failing slice tests for the failure cases:
  - every refused case in the spec gives `400` and names the parameter. For framework-detected cases (`page`, `size`, `minRating` bounds and ill-typed values), the use case is never invoked;
  - the use case throwing `RuntimeException("secret-db-host:5432 refused")` → `500` with the generic detail, no `secret-db-host` in the body, and the error logged with the correlation id (`LogCaptor`).

  Make them pass.
- [x] 6.5 Extend the existing UC-001 malformed-id tests (`MovieControllerFailureTest`, `MovieControllerEdgeIdTest`) to assert that `detail` is exactly `The request parameter 'id' is not valid.` for every malformed value, and still does not contain the supplied value. Verify: `./gradlew test --tests '*MovieController*'` passes.

## 7. Cross-cutting tests

- [x] 7.1 Add the four write methods on `/movies` to `ReadOnlyRefusalTest`, each expecting `405` with `Allow` including `GET`. Extend the ping envelope test to assert there is no `meta.pagination`. Verify: `ReadOnlyRefusalTest`, `FailureKindsTest` and `PingControllerTest` pass.
- [x] 7.2 Add a Testcontainers end-to-end `MovieSearchEndToEndTest`. It covers every `catalog/movies` search scenario that needs real data:
  - browse 25, with the default page and totals;
  - combined criteria;
  - genre case-insensitivity;
  - an empty catalog and no matches;
  - a page after the last;
  - the links-preserve-criteria scenario;
  - following `next` to the end;
  - following an entry's `self` to `GET /movies/{id}`;
  - `POST` followed by a search showing the same total.

  Verify: the test passes.
- [x] 7.3 Extend `MovieRuntimeModeAssertions` with `genre=Spaghetti` → `400` and `title=zzzz-no-such-title` → `200` with an empty list. Extend `H2DefaultRuntimeSmokeTest` to assert that `GET /movies` lists the 4 samples in the default order. These search requests are also the check that the Criteria queries run on the H2 dialect. Verify: `./gradlew test --tests '*H2DefaultRuntimeSmokeTest' --tests '*PersistentModeIntegrationTest'` passes.
- [x] 7.4 Extend `InterfaceDescriptionContractTest`:
  - validate real `searchMovies` 200 bodies against the served schema: a populated page (Postgres fixture), an empty page and a page after the last;
  - validate the 400 and 405 bodies the same way;
  - re-validate ping and `getMovie`;
  - assert `Pagination`, `page` and `size` appear exactly once and are `$ref`ed by `/movies` (`platform/interface-description`).

  Verify: the test passes, and `NoUndocumentedHttpCapabilitiesTest` still passes.

## 8. Docs and final check

- [x] 8.1 Update the `ROADMAP.md` UC-002 row to reference `add-movie-search`. Set `use-cases/UC-002-search-and-browse-movies.md` **Status** to match the project's convention for a planned use case, as UC-001 did. Verify: `git diff` shows only those lines.
- [ ] 8.2 Run the full build: `./gradlew spotlessApply build`. Verify: all tests pass, `spotlessCheck` is clean, and `openspec validate add-movie-search --strict` passes.
