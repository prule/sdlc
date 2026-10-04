## 1. OpenAPI contract

- [x] 1.1 Add the shared components (design D1):
  - `components/parameters/common.yaml`, with `Page` and `Size`;
  - in `components/schemas/common.yaml`: a closed `Pagination`, an optional `Meta.pagination`, and a closed `CollectionLinks` (`self`/`first`/`last` required, `prev`/`next` optional).

  Verify: `npx redocly lint src/main/resources/openapi/openapi.yaml` passes.
- [x] 1.2 Add `MovieCollectionEnvelope`, `MovieCollection`, `MovieCollectionEmbedded` and `MovieSummary` to `components/schemas/movie.yaml`. All are closed, and `MovieSummary` has no `synopsis`. Then add the `movies` path item (`searchMovies`, with the parameters and 200/400/406/500 responses from D1) to `paths/movies.yaml`, and `/movies` to `openapi.yaml`. Verify: `./gradlew bundleOpenApiSpec` succeeds, and the bundled file has `/movies` with no external `$ref`.

## 2. Generate stubs

- [x] 2.1 Write the failing assertions in `GeneratedApiCodegenTest` first:
  - `MoviesApi` is annotated `@Validated`;
  - `MoviesApi.searchMovies(String, List<String>, Integer, Integer, BigDecimal, String, Integer, Integer)` returns `ResponseEntity<MovieCollectionEnvelope>`;
  - each parameter carries `@RequestParam` with the query name `title`, `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating`, `sort`, `page` or `size`;
  - `sort` is a `String`, not an enum;
  - `page`, `size` and `minRating` carry `@Min`/`@Max`/`@DecimalMin`/`@DecimalMax`;
  - `Meta` has an optional `pagination`;
  - no `SearchMovies*Response*` class exists.

  Then run `./gradlew openApiGenerate`. Verify: `./gradlew test --tests '*GeneratedApiCodegenTest'` passes.

## 3. Domain (test-first)

- [x] 3.1 Write failing unit tests for `shared.domain.paging.PageRequest` and `Page`. `PageRequest` rejects page `-1`, size `0` and size `101`, accepts `0`/`1`/`100`, and computes `offset()` as a `long` without overflow for `page` `2147483647` and size `100`. `Page.totalPages()` is `0`, `1`, `2` and `4` for 0/20, 20/20, 25/20 and 25/7 (total/size). Implement until green. Both must be JDK-only.
- [x] 3.2 Extract `GenreNames.normalize` from `Movie`. Verify: the existing `MovieTest` is still green. Then write failing tests for `MovieSummary` (genre normalization, required fields) and implement it.
- [x] 3.3 Write failing tests for `MovieSearchCriteria`:
  - a blank or whitespace-only title becomes empty;
  - a non-blank title is kept untrimmed;
  - `from > to` throws `InvalidSearchCriterionException(RELEASE_YEAR_RANGE)`;
  - `from == to` is allowed;
  - either bound may be absent.

  Also write failing tests for `MovieSortOrder.parse`: each of the six values, absent gives `DEFAULT`, and `""`, `Title` and `synopsis` throw `SORT`. Add `SearchCriterion` and `InvalidSearchCriterionException` (code `BAD_REQUEST`) and implement until green.

## 4. Application and ports (test-first)

- [x] 4.1 Write failing unit tests for `SearchMoviesService`, with mocked `LoadGenreVocabularyPort` and `SearchMoviesPort`:
  - known genres in any case are resolved to canonical names and passed to the search port;
  - an unknown or empty genre throws `InvalidSearchCriterionException(GENRE)`, and the search port is never invoked;
  - no genres skips the vocabulary lookup;
  - a port exception propagates.

  Add `SearchMoviesUseCase`, `SearchMoviesPort`, `LoadGenreVocabularyPort` and the service until green.

## 5. Outbound adapter (no migration)

- [x] 5.1 Add `GenreJpaRepository`, and implement `LoadGenreVocabularyPort` in `MoviePersistenceAdapter` test-first, with a Testcontainers test (extending `PostgresIntegrationTest`) asserting the vocabulary names. Verify: the test passes.
- [x] 5.2 Write failing Testcontainers tests for `SearchMoviesPort` filtering:
  - title substring, ignoring case, including `%`, `_` and `\` matched literally;
  - all-genres matching, and the same genre given twice;
  - inclusive year bounds and a single year;
  - inclusive `minRating`, and unrated excluded at `0`;
  - a combined search (the five-movie fixture from the spec);
  - totals.

  Implement the criteria queries (D5: count, then page ids, then entities with genres, re-ordered) until green.
- [x] 5.3 Write failing Testcontainers tests for ordering and paging:
  - the default order fixture (`alpha`, `Arrival`, `Zebra`, `Laugh Track`);
  - all six sort values;
  - unrated last for `rating` and `-rating`;
  - ten same-title movies walked with size 3, each appearing exactly once and in the same order on a repeat walk;
  - a beyond-last offset, which returns empty and issues no page query;
  - `page` `2147483647`.

  Implement the D4 order until green.

## 6. Inbound web (test-first)

- [x] 6.1 Write failing platform web tests with a test-only interface annotated `@Validated`, as the generated interfaces are. Its operation takes `@Min(0) @RequestParam("page") Integer p` and `@Max(100) @RequestParam("size") Integer s`, with deliberately different Java argument names. The test-only controller implements that interface, so the tests go through the `MethodValidationPostProcessor` path. Assert:
  - a `400` for out-of-range values (a `ConstraintViolationException`), and for non-numeric and int-overflow values (a type mismatch), with `BAD_REQUEST`, the same `type` as other 400s, and a `detail` of `Query parameter '<name>' is invalid.` that uses the `@RequestParam` name, not the argument name, with no value or type name;
  - that when both parameters are violated, the lower-index one (`page`) is named;
  - that path-variable 400s (UC-001) keep the generic detail;
  - that `InvalidQueryParameterException("x")` maps the same way;
  - that a real violation from a test-only `@Validated` service bean is `500`, not a parameter-naming `400`. The test controller's handler calls a service method whose constrained parameter (for example `@Min(0) int x`) sits at the **same index** as one of the controller's `@RequestParam`s. A hand-built, empty `ConstraintViolationException` is not acceptable;
  - that a constrained path variable on the test interface gives the generic `400`;
  - that a `ConstraintViolationException` thrown with no `HandlerMethod` (for example, from a filter) is `500`.

  Implement `ProblemFactory.invalidQueryParameter`, `InvalidQueryParameterException`, the `@ExceptionHandler(ConstraintViolationException.class)` with the D2 decision order (no handler, then not-this-handler via the root bean class and the method node, then the parameter-naming 400, then the generic 400), and the `handleTypeMismatch` override (D2) until green. Verify: `FailureKindsTest` and `MovieControllerEdgeIdTest` still pass.

- [x] 6.2 Write failing unit and web tests for `platform/web/CollectionLinksFactory` covering:
  - the first, middle, last and beyond-last pages, and no matches (the relation sets and target pages);
  - raw values preserved exactly, including repeated `genre` and mixed case;
  - unrecognised parameters dropped;
  - defaults not leaked (`/movies` gives `?page=0` only);
  - forwarded host and scheme.

  Add `ResponseMetaFactory.create(Pagination)`. Implement until green.
- [x] 6.3a Write failing `@PlatformWebTest` slice tests for `MovieController.searchMovies`, with `SearchMoviesUseCase` mocked, covering:
  - the 200 mapping: `_embedded.movies`, summary member sets, no `synopsis`, rating literals, absent optionals, `genres` `[]`;
  - each summary's `self` equals the UC-001 details href;
  - `meta.pagination`;
  - `Accept: application/problem+json` still gives 200 `application/json`;
  - `Authorization: Bearer garbage` still gives 200;
  - the criteria, order and page request passed to the use case: blank `title` is absent; `title=a&title=b` becomes the term `a,b`; the raw `genre` values are not comma-split; `minRating=`, `releaseYearFrom=`, `releaseYearTo=`, `page=` and `size=` are absent or default.

  Implement until green. The controller imports the generated `MovieSummary` DTO and refers to the domain `MovieSummary` by its fully qualified name (D6). It wraps both building the inputs and `useCase.search(...)` in the `InvalidSearchCriterionException` → `InvalidQueryParameterException` translation.

- [x] 6.3b Write failing slice tests for the refusals and the fault, through the real `MovieController` (behind its `@Validated` proxy), then make them pass:
  - bounds via `ConstraintViolationException`: `page=-1`, `size=0`, `size=101`, `minRating=5.1` and `minRating=-0.1`, each giving 400 with `detail` naming `'page'`, `'size'` or `'minRating'`, and the use case never invoked;
  - type mismatch: `page=abc`, `page=99999999999`, `size=1.5`, `minRating=abc` and `releaseYearTo=abc`, each giving 400 naming the parameter;
  - domain-detected while the inputs are built: `sort=`, `sort=Title`, `sort=synopsis`, `sort=title&sort=rating` and `releaseYearFrom=2010&releaseYearTo=2000`, giving 400 naming `'sort'` or `'releaseYearFrom'`, and the use case never invoked;
  - unknown genre: the mocked use case throws `InvalidSearchCriterionException(GENRE)` for `genre=Western` and for `genre=`, giving 400 naming `'genre'`, not `500`;
  - 500 when the use case throws `RuntimeException("secret-db-host:5432 refused")`, with the generic detail and no `secret-db-host` in the body.

## 7. Cross-cutting tests

- [x] 7.1 Extend `ReadOnlyRefusalTest` with the four write methods on `/movies`, each expecting 405 with `Allow` including `GET`. Verify: the test passes.
- [x] 7.2 Add a Testcontainers end-to-end `MovieSearchEndToEndTest` covering the spec scenarios that need real data:
  - browse; the empty catalog; no matches;
  - each filter and ordering scenario; the middle, first, last and beyond-last page links;
  - following a summary's `self` to the details;
  - `POST` followed by `GET` with `totalElements` unchanged.

  Verify: the test passes.
- [x] 7.3 Add `MovieSearchRuntimeModeAssertions`: 200 for browse, 400 naming `'size'` for `size=0`, and 400 naming `'genre'` for `genre=Western`. Call it from both mode tests. In `H2DefaultRuntimeSmokeTest`, also assert only that the browse ids include `11111111-1111-4111-8111-111111111111` and `22222222-2222-4222-8222-222222222222`. Assert no order and no filter result (standards/testing.md §3). Verify: `./gradlew test --tests '*H2DefaultRuntimeSmokeTest' --tests '*PersistentModeIntegrationTest'` passes.

- [x] 7.4 Extend `InterfaceDescriptionContractTest` to validate the real `searchMovies` 200 (populated and empty), 400 and 405 bodies against the served schema. Also assert that `/ping` and `getMovie` bodies have no `meta.pagination`. Verify: the test passes, and `NoUndocumentedHttpCapabilitiesTest` still passes.

## 8. Docs and final check

- [x] 8.1 Correct `standards/openapi.md` §2a without changing its `@Validated` rule:
  - pages are zero-based;
  - beyond-last means `page > lastPage`, where `lastPage = totalPages − 1` (this fixes the off-by-one in "`page` index > `totalPages`");
  - `page=` is always appended last in navigation links;
  - an invalid query parameter gives a 400 whose `detail` names it.

  In `standards/error-handling.md` §3, add rows mapping `ConstraintViolationException` (from a handler-method parameter) and `MethodArgumentTypeMismatchException` to `400` `BAD_REQUEST`, with a detail naming the query parameter. In `domain/bounded-contexts.md`, mark movie search as built. Verify: `grep -n "ConstraintViolationException" standards/error-handling.md` shows the new row.

- [x] 8.2 Run the full build: `./gradlew spotlessApply build`. Verify: all tests pass and `spotlessCheck` is clean.
