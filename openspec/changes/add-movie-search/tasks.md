## 1. OpenAPI contract

- [x] 1.1 Add `Pagination` and `InvalidParam` to `components/schemas/common.yaml`, optional `Meta.pagination` and optional `Problem.errors` (minItems 1), all closed (design D1); verify `./gradlew bundleOpenApiSpec` succeeds and existing `InterfaceDescriptionContractTest` still passes
- [x] 1.2 Create `components/parameters/common.yaml` with `Page` (int32, min 0, default 0) and `Size` (int32, 1–100, default 20); verify the bundle contains both under `components.parameters`
- [x] 1.3 Add `MovieSummary`, `MovieSearchEnvelope`, `MovieSearchPage`, `MovieSearchEmbedded`, `MovieSearchLinks` to `components/schemas/movie.yaml`, reusing `MovieLinks` for items; verify bundle succeeds and no inline object schema is introduced
- [x] 1.4 Add the `movies` path item (`searchMovies`, parameters `title` maxLength 200, repeatable `genre`, `releaseYearFrom`, `releaseYearTo`, `minRating` 0–5, `sort` enum, `Page`, `Size`; responses 200/400/406/500; description per D1) to `paths/movies.yaml` and `/movies` to `openapi.yaml`; verify `redocly bundle` reports no errors

## 2. Generate stubs

- [x] 2.1 Run `./gradlew openApiGenerate` and confirm `MoviesApi.searchMovies` exists returning `ResponseEntity<MovieSearchEnvelope>` and `Meta` has `pagination`, `Problem`-related `InvalidParam` model exists
- [x] 2.2 Extend `GeneratedApiCodegenTest`: `searchMovies` returns `MovieSearchEnvelope`, its `sort` parameter is `String` (if not, apply the D1 fallback in the contract and re-run), and no `SearchMovies200Response*` class exists; verify the test passes

## 3. Domain

- [x] 3.1 Extract genre normalisation from `Movie` into package-private `GenreNames.normalize` and use it from `Movie`; verify `MovieTest` still passes unchanged
- [x] 3.2 Add `MovieSummary` record (id, title, releaseYear, normalised genres, optional runtime, optional rating) with `MovieSummaryTest` covering blank title rejection, genre A–Z/dedupe and empty optionals
- [x] 3.3 Add `InvalidCriteriaException` (shared kernel, code `BAD_REQUEST`, non-empty list of `Violation(field, message)`) with a unit test asserting code, violations and that messages are not built from inputs
- [x] 3.4 Add `MovieOrder` with `parse(String)` (null → `RELEASE_YEAR_DESC`; six supported values; anything else → `InvalidCriteriaException` field `sort`); verify `MovieOrderTest` covers all values, null, `popularity`, `Title` (case-sensitive refusal) and that the message does not contain the value
- [x] 3.5 Add `PageRequest` (defaults 0/20; `page < 0` → field `page`; size outside 1–100 → field `size`) and `ResultPage<T>` (`totalPages`, `lastPage`, `hasPrev`, `hasNext` per the platform rule) with tests for 0 results, exact multiples, partial last page, after-last page, and `page × size` beyond int range
- [x] 3.6 Add `MovieSearchCriteria` factory (blank/whitespace title → none; genres lower-cased with `Locale.ROOT` and de-duplicated; blank genre → field `genre`; `from > to` → fields `releaseYearFrom` and `releaseYearTo`; `minRating` outside 0–5 → field `minRating`; all violations collected into one exception); verify `MovieSearchCriteriaTest` covers each rule and asserts field names equal the contract parameter names

## 4. Application / ports

- [x] 4.1 Add `SearchMoviesUseCase`, `SearchMoviesQuery` (raw inputs), `SearchMoviesPort` and `GenreVocabularyPort`; verify they import only domain and shared-kernel types (`./gradlew compileJava`)
- [x] 4.2 Implement `SearchMoviesService` (D3 order: parse/collect domain violations → vocabulary check → port search); verify `SearchMoviesServiceTest` with mocked ports: valid query delegates with the right criteria/order/page, each invalid input throws `InvalidCriteriaException` with the right field and `verifyNoInteractions(searchMoviesPort)`, unknown genre is reported as `genre` and the search port is not called

## 5. Outbound adapter (no migration)

- [x] 5.1 Implement `GenreVocabularyPort` in `MovieSearchAdapter` (`lower(name) IN (:names)`); verify a Testcontainers test returns the unknown names only, matching case-insensitively
- [x] 5.2 Add `MovieJpaRepository.findAllByIdIn` with `@EntityGraph("genres")` and implement predicates in `MovieSearchAdapter` (title LIKE with `\` escaping, one EXISTS per genre, year bounds, rating guard); verify Testcontainers tests for title (`HEIST`, `rand h`, `%`, `_`, `\`), genre all-of, year range edges and min-rating scenarios from the spec
- [x] 5.3 Implement count + ordered id page + rows re-ordered by id list, skipping the id/row queries when offset ≥ count (D5 order table, CASE key for unrated-last, `lower(title)`, `id` tiebreak); verify Testcontainers tests for every `sort` value and the default, unrated-last both directions, case-insensitive title order, 45-movie paging counts, after-last page, and a size=2 page walk equal to the size=100 list with no duplicates
- [x] 5.4 Verify no N+1: a Testcontainers test with Hibernate statistics asserts a 20-item page runs at most 3 SELECT statements (plus at most one vocabulary query when genres are given); confirm `./gradlew test` boots with `ddl-auto: validate` (no Flyway change)

## 6. Platform error and meta support

- [x] 6.1 Extend `ProblemFactory` with violations → `errors` (only for `BAD_REQUEST`), and `ResponseMetaFactory.create(Pagination)`; verify unit tests that non-400 kinds never carry `errors` and `/ping` meta has no `pagination`
- [x] 6.2 Extend `GlobalExceptionHandler`: `InvalidCriteriaException` → 400 with violations; `ConstraintViolationException` → 400 with parameter names; populate `errors` from `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` and `HandlerMethodValidationException` with fixed messages; verify `FailureKindsTest` extended per the platform ADDED requirement (test-only UUID param named in `errors`, value not echoed, other kinds have no `errors`)

## 7. Inbound controller

- [x] 7.1 Add `PagedLinks` helper (declared-param whitelist, raw values preserved, `page` replaced, prev/next/first/last rules, forwarded headers) with a unit/web test asserting links for first, middle, last, after-last and empty pages, no-param requests (links carry only `page`), repeated `genre` order preserved, a stray param dropped, and `title=100%25 Love` round-tripping; plus a test asserting the whitelist equals the operation's parameter names in the bundled spec
- [x] 7.2 Implement `MovieController.searchMovies` (raw query → use case → summaries with item `self` links, `stripTrailingZeros` rating, absent optionals, `meta.pagination`, `application/json`); verify a web-slice `MovieSearchControllerTest` with the use case mocked: envelope shape, `Accept: application/problem+json` still 200 JSON, summary member set, no `synopsis`
- [x] 7.3 Add `MovieSearchControllerFailureTest` (use case mocked): parameterised refusals for `sort=popularity`, `size=0`, `size=101`, `page=-1`, `page=abc`, `page=99999999999`, `minRating=5.5`, `minRating=-1`, `minRating=high`, `title` of 201 chars, reversed year range — each `400`, `BAD_REQUEST`, expected `errors[].field`, value absent from body, and use case not invoked for framework-detected faults; plus `500` generic for a thrown `secret-db-host:5432 refused`

## 8. Integration, platform and runtime-mode tests

- [x] 8.1 Add `MovieSearchEndToEndTest` (Testcontainers, real stack): browse, summary content (fully recorded and minimal movie), following an item `self` link returns the details, combined criteria, empty catalog / no-match `200` with `self`/`first`/`last`, unknown genre `400`, forwarded-header links, and anonymous access
- [x] 8.2 Extend `ReadOnlyRefusalTest` with `POST/PUT/PATCH/DELETE /movies` → `405`, `Allow` includes `GET`; verify a following browse total is unchanged (Testcontainers)
- [x] 8.3 Extend `InterfaceDescriptionContractTest` to validate real `searchMovies` 200 (non-empty, empty, after-last), 400 with `errors` and 405 bodies against the served schema, and that `Pagination`/`InvalidParam` each appear once under `components`
- [x] 8.4 Extend `MovieRuntimeModeAssertions` (both modes: `size=1` → 200 paged form; `sort=popularity` → 400 naming `sort`) and `H2DefaultRuntimeSmokeTest` (default browse lists `Arrival`, `The Grand Heist`, `Laugh Track`, `Untitled Reel`, total 4); add a sentence to the `searchMovies` description noting standalone samples; verify both mode tests pass
- [x] 8.5 Run `./gradlew spotlessApply build` and verify the full suite (unit, web, Testcontainers, H2 smoke, codegen) is green
