## 1. Contract, stubs, domain, application

- [x] 1.1 Confirm no change is needed: `git diff --stat develop -- src/main/resources/openapi src/main/resources/db ':(glob)src/main/java/**/domain/**' ':(glob)src/main/java/**/application/**'` is empty at the end of the change (no OpenAPI, generated-stub, domain, application or migration edits)

## 2. Outbound adapter — bind the title value (S11, design D1)

- [x] 2.1 Write `MovieTitleBindingTest` (extends `PostgresIntegrationTest`, `@AutoConfigureMockMvc`, `@TestPropertySource` registering a test `StatementInspector` that records SQL). With `Ocean's Eleven` and `Arrival` in the catalog, send `GET /api/v1/movies?title=n's%20e` through `MockMvc` and assert `200` and that only `Ocean's Eleven` is returned. Assert that the recorded statements are non-empty, that none contains `n's e` or `n''s e`, and that the title predicate's statement has a bind placeholder (`like lower(?)`, case- and whitespace-tolerant). Verify it fails (red) on the current `cb.literal` code before 2.2
- [x] 2.2 In `MoviePersistenceAdapter`, replace `cb.literal(pattern)` with `cb.parameter(String.class, "titlePattern")` and set the parameter on both the count and the id-page queries when a title term is present. Verify that 2.1 and all of `SearchMoviesFilteringTest` pass, and that `grep -rn "\.literal(" src/main` returns nothing

## 3. Inbound web — server-side constraint violations are 500 (D5, design D2/D3)

- [x] 3.1 Add to `TestOnlyValidatedApi`/`TestOnlyValidatedController` a `GET /test-only/validated/result-constraint` operation whose return value is `@NotNull(message = "secret-detail must not be blank")` and returns `null`. Add `GET /test-only/validated/elements` with `@RequestParam("ids") List<@Min(0) Integer>`. Add a method with a constrained `@RequestParam("page")` and a constrained return value for the mixed test. Verify that the test sources compile
- [x] 3.2 In `ConstraintViolationHandlingTest`, add tests that fail on current code:
  - the result-constraint request gives `500` `INTERNAL_ERROR` with detail `An unexpected error occurred.`, the body does not contain `secret-detail`, and an ERROR event carries the correlation id (`LogCaptor`);
  - the mixed test builds real violations with `validator.forExecutables()` (`page=-1` plus a null return) and calls `handleConstraintViolation` with a real `HandlerMethod`, asserting `500`;
  - `elements` with `ids=1&ids=-1` gives `400` `BAD_REQUEST` with `detail` `Query parameter 'ids' is invalid.` (this case must stay green before and after 3.3).
- [x] 3.3 Rewrite `GlobalExceptionHandler.handleConstraintViolation` per design D2: `500` unless every violation is a handler-parameter violation; otherwise the lowest-index `@RequestParam`-named `400`, or the generic `400`. Verify that all of `ConstraintViolationHandlingTest` passes (including the existing service-conflict `500`, path-variable generic `400` and named `400`s), along with `MovieSearchRefusalTest` and `FailureKindsTest`

## 4. Test-standard fixes (S6, S7, S8, H2 rule)

- [x] 4.1 `SearchMoviesOrderingAndPagingTest.walkAllIds`: rewrite as a bounded walk (read `totalPages` from page 0, assert `≤ MAX_PAGES`, then `for` over pages with no `if`/`break`/assertion in the body). Verify that the ten-same-title test passes and that `grep -n "while (true)" src/test` returns nothing
- [x] 4.2 Replace `everySupportedSortProducesAStableCompleteOrder` with a `@ParameterizedTest` over `(MovieSortOrder, expected titles)` using the four-movie fixture from design D4 (six distinct orders). Verify that it passes. Then temporarily swap two sort mappings in the adapter and confirm the test fails before reverting
- [x] 4.3 `CollectionLinksFactoryForwardedHeadersTest`: replace the `for` loop with one `allSatisfy` assertion over the collected hrefs. Verify that the test passes
- [x] 4.4 `MovieSearchRefusalTest`: remove the unused `JsonNode body` local. Verify that the test passes and the compiler/IDE reports no unused variable
- [x] 4.5 `H2DefaultRuntimeSmokeTest.fullyCuratedSampleMovieIsServed`: replace the genre `containsExactly` check with "`genres` is a non-empty array". Verify that the smoke test passes and contains no order, filter or field-mapping assertions (shape checks such as `genres` being `[]` for the minimal movie are allowed). Confirm that `MovieEndToEndTest`'s `Thriller`/`Drama`/`Sci-Fi` order test still passes on PostgreSQL

## 5. Missing PostgreSQL acceptance tests (S10, design D5)

- [x] 5.1 `SearchMoviesFilteringTest`: add tests for `rand h` (only `The Grand Heist`), blank title `"  "` built via `MovieSearchCriteria.of` (every movie), `releaseYearFrom=2005` only (2005 and 2016) and `releaseYearTo=2005` only (1998 and 2005). In `MovieSearchEndToEndTest`, also add a web-level test for an empty `title=`: `200` and every movie in the catalog is returned. Verify that they pass
- [x] 5.2 `MovieSearchEndToEndTest`: add a summary-contents test with a fully curated movie (with synopsis), a movie with nothing optional and no genres, and a movie rated `5.0`. Assert each summary's exact member set, the literal JSON text `5`, `genres` `[]` with no `runtimeMinutes`/`rating` keys for the minimal movie (the main spec is authoritative over the ticket's "omitted" for genres), and that following each summary's `_links.self.href` gives `200` with the same `id`. Verify that it passes
- [x] 5.3 New `MovieSearchPagingAndLinksEndToEndTest` (extends `PostgresIntegrationTest`, 25 movies via a fixture builder): assert 20 items by default, 5 on `page=1` and 7 on `size=7`, and that a bounded walk at `size=7` equals one `size=100` page's ids in order. Also assert `page=2147483647&size=100` gives `200` with an empty `_embedded.movies`. Verify that they pass
- [x] 5.4 In the same class, add a `@ParameterizedTest` over `(relation, targetPage)` for `?title=Movie&genre=drama&genre=SCI-FI&foo=bar&sort=-rating&size=7&page=1`. Each link's URL-decoded query pairs equal `title=Movie, genre=drama, genre=SCI-FI, sort=-rating, size=7, page=<target>`. Add a test that, with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.test`, every href starts with `https://api.example.test/api/v1/movies?` (one `allSatisfy`). Verify that they pass

## 6. Verification

- [x] 6.1 Run `./gradlew build` (tests plus `spotlessCheck`) and confirm it is green. Confirm by grep that `src/test` has no `while (true)` and no assertion inside a `for`/`forEach` body in the touched classes
- [x] 6.2 In `retrospectives/2026-09-28-add-movie-search.md`, mark S6, S7, S8, S10, S11 and D5 `fixed` with a reference to this change, and mark S9 `deferred (flag only; CLEAN-001 out of scope)`. Verify that the table rows read that way
