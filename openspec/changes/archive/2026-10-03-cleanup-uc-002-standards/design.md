## Context

See proposal.md (Why). Verified current state:

- `MoviePersistenceAdapter.predicates(...)` (line ~140) builds the title predicate as `cb.like(cb.lower(root.get("title")), cb.lower(cb.literal(pattern)), ESCAPE_CHAR)`. Hibernate 6 renders `cb.literal` inline into the SQL text. The same `predicates(...)` feeds both the count query and the id-page query. It is the only `cb.literal` or concatenated query value in `src/main`.
- `GlobalExceptionHandler.handleConstraintViolation` keeps only the violations that "belong" to the handler (root bean and METHOD node match), then answers `400` if **any** belongs. A belonging violation without a `PARAMETER` second node (for example a return value) falls through to "Rule 4", the generic `400`. A mix of belonging and non-belonging violations also gives `400`. Both contradict `standards/error-handling.md` §3 (the two `ConstraintViolationException` rows and the note).
- Tests: `SearchMoviesOrderingAndPagingTest.walkAllIds` uses `while (true)` with an `if/break` inside. `everySupportedSortProducesAStableCompleteOrder` asserts only the count. `CollectionLinksFactoryForwardedHeadersTest` asserts in a `for` loop against a test-only controller. `MovieSearchRefusalTest` (line ~59) has an unused `JsonNode body`. `H2DefaultRuntimeSmokeTest.fullyCuratedSampleMovieIsServed` asserts genre order on H2.
- Already on PostgreSQL, and kept: genre order for movie details (`MovieEndToEndTest`, `Thriller`/`Drama`/`Sci-Fi`); both-bounds and single-year filters, `heist`/`HEIST`, `%`, `_`, `\` (`SearchMoviesFilteringTest`); persistence-level huge page (`SearchMoviesOrderingAndPagingTest`); link presence for first/middle/last/beyond pages (`MovieSearchEndToEndTest`).
- Missing on PostgreSQL (S10): summary contents; paging defaults over 25; `rand h`, blank `title`, a quote in `title`; `releaseYearFrom` only and `releaseYearTo` only; decoded link queries and forwarded headers on the real endpoint; web-level `page=2147483647&size=100`.

**Contract:** no OpenAPI operation is added or changed, so there is no contract snippet. `openapi/` and the generated `MoviesApi` are untouched (`standards/openapi.md` §2a: links keep echoing only the parameters sent).
**Migrations:** none. No Flyway migration, so no rollback consideration beyond reverting the commit.
**Components touched:** an outbound adapter (`catalog/movies/adapters/out/persistence/MoviePersistenceAdapter`) and the platform web adapter (`platform/web/GlobalExceptionHandler`). No domain or application code changes, so dependencies stay inward-only.
**Query parameters:** none are added or changed. The malformed-input rule for query parameters does not apply. Existing coverage is unchanged.

## Goals / Non-Goals

**Goals:** close S6–S11 (except S9) and D5 with the smallest code change. Each fix is pinned by a test that fails if it regresses.

**Non-Goals:** splitting either over-length class (S9). The handler fix is a rewrite of one method plus one private helper, and leaves the class about the same length. Also out of scope: naming `@PathVariable` violations (see Open Questions), and new shared test infrastructure beyond what each test needs.

## Decisions

### D1. Bind the title pattern with `cb.parameter`, and pin it by inspecting the SQL
Replace `cb.literal(pattern)` with a named criteria parameter, `cb.parameter(String.class, "titlePattern")`. The adapter sets the parameter on each `TypedQuery` that uses the predicates (count and id page) when a title term is present. `cb.lower(...)` stays on both sides, so case-folding is unchanged, and the `ESCAPE_CHAR` constant stays.
- *Alternative: `cb.value(pattern)`.* §5 allows it, and it is shorter because there are no `setParameter` calls. Rejected: whether it is bound depends on Hibernate's `criteria.value_handling_mode` setting (default `BIND`), which a future config change could flip silently. A named parameter is bound regardless of that setting.
- *How the test pins it.* A quote alone does not prove binding: Hibernate escapes quotes when it inlines a literal, so `n's e` would match even with `cb.literal`. The PostgreSQL test therefore does two things. It asserts the behaviour (`n's e` matches only `Ocean's Eleven`). It also captures the SQL text through a test-scoped Hibernate `StatementInspector`. It asserts that no captured statement contains the term either raw (`n's e`) or SQL-escaped (`n''s e`, the form Hibernate writes when it inlines a literal), and that the title predicate's statement carries a bind placeholder (`like lower(?)`, matched case-insensitively and allowing for whitespace). The search goes through `MockMvc` on the real `GET /api/v1/movies?title=n's%20e` and asserts `200`, so it matches the spec scenario end to end. The inspector is set with `@TestPropertySource` (`spring.jpa.properties.hibernate.session_factory.statement_inspector`) on that one test class. This adds observation only and replaces no framework bean, so it complies with `testing.md` §2. The extra Spring context is the price, accepted for one class.
- **`src/main` check:** `grep -rn "cb.literal\|criteriaBuilder.literal" src/main` returns nothing. QA records this as the check for the first done criterion.

### D2. Classify constraint violations as "all on handler parameters, or 500"
Rewrite `handleConstraintViolation` to follow the §3 note in this order:
1. No `HandlerMethod` → `500`.
2. If **any** violation is not a *handler-parameter violation* → `500`. A handler-parameter violation needs the root bean class to be assignable to the handler's bean type, the first path node to be a `METHOD` node matching the handler's name and parameter types, and the second node to be a `PARAMETER` node.
3. Otherwise → `400`. The detail names the lowest-index parameter that has a `@RequestParam` name, as today. With no `@RequestParam` name (a path variable), the implementation keeps today's generic `400` detail. The spec does not pin that detail (see Open Questions).

`belongsToHandler` and `requestParamName` merge into one predicate, `isHandlerParameterViolation`, plus the existing name lookup. The `500` path reuses `internalError(ex)`, which already logs at ERROR with the correlation id and returns the generic body.
- *"Path ends in a method-parameter node":* we require the node after `METHOD` to be `PARAMETER`, and we allow deeper nodes (for example a container element of a `List` parameter). Such a violation is still client input. Rejecting deeper nodes would turn a `List<@Min(0) Integer>` element violation into a `500`. A test-only operation proves it (D3). The standard's wording should be loosened to match; that is a human decision, not part of this change (see Open Questions).
- *Path variables:* the classification above is unchanged for them (they are handler parameters, so `400`). This change does not specify the `detail` for a constrained path segment. Today's generic detail stays as implemented, but it is not written into the spec (see Open Questions).
- *Alternative: keep rule 4 (generic `400` for a belonging non-parameter violation).* Rejected: the standard states the opposite.

### D3. Test-only operations for the new 500 cases
Extend `TestOnlyValidatedApi` / `TestOnlyValidatedController`. The interface stays `@Validated`, so the production `MethodValidationPostProcessor` path runs. Add:
- `GET /test-only/validated/result-constraint`: a return value annotated `@NotNull(message = "secret-detail must not be blank")`. The implementation returns `null`, so the AOP proxy raises a real return-value `ConstraintViolationException` with `METHOD` → `RETURN_VALUE` nodes.
- `GET /test-only/validated/elements`: a `@RequestParam("ids") List<@Min(0) Integer>` parameter. `ids=1&ids=-1` must give `400` with `detail` `Query parameter 'ids' is invalid.` (the path is `METHOD` → `PARAMETER` → `CONTAINER_ELEMENT`).
- A method with both a constrained `@RequestParam("page")` and a constrained return value, used only by the mixed-violation unit test. That test builds a real violation set with `validator.forExecutables()` (`validateParameters` plus `validateReturnValue` on the controller instance) and calls `handleConstraintViolation` with a real `HandlerMethod`. A hand-built empty exception is not acceptable.

### D4. Test-standard fixes
- **Bounded walk** (`testing.md` §4): `walkAllIds` fetches page 0 to read `totalPages`. It then asserts `totalPages ≤ MAX_PAGES` (a constant, for example 10), which fails the test when exceeded. Next, `for (int page = 0; page < totalPages; page++) ids.addAll(idsOf(search(order, page, size)))`. The loop body has no `if`, no `break` and no assertion. The assertions run on the collected list afterwards.
- **Every-sort order** (S7): replace the count-only test with a `@ParameterizedTest` over a `@MethodSource` of `(MovieSortOrder, expected titles)`. The four-movie fixture gives six pairwise-distinct expected orders, so mapping any sort to the wrong comparator fails. The fixture is `A` (2003, 2.0), `B` (2001, unrated), `C` (2004, 4.0) and `D` (2002, 3.0):
  - `title` → A B C D; `-title` → D C B A;
  - `releaseYear` → B D A C; `-releaseYear` → C A D B;
  - `rating` → A D C B; `-rating` → C D A B.
- **Forwarded-headers unit test:** collect the `self`/`first`/`last` hrefs into a list and make one assertion, `assertThat(hrefs).allSatisfy(h -> assertThat(h).startsWith(...))`. Keep it as factory-level coverage. The spec's acceptance check moves to the real endpoint (D5).
- **Unused local:** delete `JsonNode body =` and keep the call.
- **H2 smoke test:** assert the presence of `runtimeMinutes`/`synopsis`/`rating` and that `genres` is a non-empty array. Assert no values or order (`testing.md` §3). Genre order stays covered on PostgreSQL by `MovieEndToEndTest`.

### D5. Where the new PostgreSQL tests live
All new tests extend `PostgresIntegrationTest` (the shared container) and reuse the existing JDBC insert helpers. No new base class.
- Persistence level, `SearchMoviesFilteringTest`: `rand h`; blank title (criteria built through `MovieSearchCriteria.of`, so blank means no criterion); `releaseYearFrom` only (2005 → 2005 and 2016); `releaseYearTo` only (2005 → 1998 and 2005).
- Quote binding (D1): a new `MovieTitleBindingTest`, separate because of its own `@TestPropertySource` context.
- Web level, through the real `/api/v1/movies` (`MockMvc` on the full context): summary contents (exact member set, literal `5` for `5.0`, absent keys), paging defaults over 25 (20 / 5 on `page=1` / 7 on `size=7`, and walking `size=7` equals one `size=100` page, using the bounded-walk shape from D4), decoded link queries, forwarded headers, and `page=2147483647&size=100`. These go in a new `MovieSearchPagingAndLinksEndToEndTest`, so `MovieSearchEndToEndTest` stays a readable size. Summary contents stay in `MovieSearchEndToEndTest`.
- Link query decoding: split each href's raw query on `&`, URL-decode each pair, and compare the list to the expected list in one assertion per relation. The relations are a `@ParameterizedTest` over `(relation, targetPage)`, so no assertion sits in a loop.

## Risks / Trade-offs

- **[Risk] The 400 → 500 change hits a real client path.** → No generated operation has return-value constraints or calls a validated service with request data. Today the only reachable violations are the `page`/`size`/`minRating` parameter bounds, which stay a named `400`. Covered by the scenario "Parameter violations remain a named client fault".
- **[Risk] A container-element violation of a parameter could be misclassified as a server fault (D2).** → Tested through the test-only `elements` operation (D3), which must give the named `400`.
- **[Risk] The `StatementInspector` test adds a Spring context and couples to the Hibernate property name.** → Limited to one class. If the property is wrong, no SQL is captured. To catch that, the test also asserts that the captured statement list is non-empty.
- **[Risk] The SQL check passes on the buggy code.** If it looked only for the raw term, it would miss Hibernate's escaped `n''s e`. → The test checks both forms plus the `?` placeholder, and task 2.1 requires it to be shown red on the current `cb.literal` code first.
- **[Risk] Suite time grows.** → The shared static container is reused. Only D1's class adds a context.
- **[Trade-off] Mixed violations are tested only at handler level, not through HTTP.** The AOP proxy validates parameters before the method runs and the return value after it, so it never raises one exception that mixes both. The unit test uses a real validator and a real `HandlerMethod`.

## Migration Plan

None. No schema, contract or configuration change. Rollback is a revert of the commit.

## Open Questions

- `standards/error-handling.md` §3's note says to name the parameter "from `@RequestParam`/`@PathVariable`". Today a constrained path variable gives the generic `400` detail (`constrainedPathVariableGivesTheGenericDetail`), and the ticket does not ask for a change. This plan keeps the current behaviour. A human should confirm whether path-variable naming is wanted as a separate change. It is deferrable because no operation has a constrained path variable.
- **S9 (class length, `clean-code.md` §1):** `MoviePersistenceAdapter` (238 lines, three ports) and `GlobalExceptionHandler` (237 lines) stay over the ~200-line flag. CLEAN-001 puts the split out of scope, so the retrospective will record S9 as deferred. Does a human want a follow-up ticket to split them before UC-003, or to accept them as they are?
- **`standards/error-handling.md` §3 wording:** "its path ends in a method-parameter node" should be loosened to "its path's second node is a parameter node of the handler that ran". That makes violations deeper inside a parameter (container elements) explicitly a `400`, which this change implements and tests. The amendment is a human decision and is not part of this change.
