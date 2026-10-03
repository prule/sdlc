## Why

UC-002 (search & browse movies, #37) merged with findings S6–S11 and D5 still outstanding (`retrospectives/2026-09-28-add-movie-search.md`, recommendation R5). The standards have since been tightened (#38, #40), and the merged code breaks them: the title search term is written into the SQL text instead of bound (a real defect), a server-side constraint violation can be blamed on the client, several tests break `standards/testing.md`, and acceptance checks from the UC-002 spec were never run against PostgreSQL. Ticket: `tickets/CLEAN-001-uc-002-standards-cleanup.md`. Fixing this now keeps `develop` conforming before UC-003 builds on the same search, paging and error-handling code.

## What Changes

- **Bind the title search value** (S11): the title LIKE pattern reaches the database as a bind parameter. A PostgreSQL test with a quote in the term pins it.
- **Server-side constraint violations are 500** (D5): a `ConstraintViolationException` whose violations are not all on the handling controller method's parameters (for example a return-value constraint) gives `500` `INTERNAL_ERROR`, logged at ERROR. Parameter violations keep their named `400`. This is the only observable behaviour change, and no generated operation triggers it today.
- **Test-standard fixes** (S6, S7, S8): bounded page walk with no conditionals in the loop; no assertions inside a loop in the forwarded-headers test; the every-sort test checks the actual order; remove the unused local in the refusal test; the H2 smoke test no longer asserts genre order.
- **Missing PostgreSQL acceptance tests** (S10): summary contents, paging defaults, `rand h` and blank title, single-bound year filters, criteria-preserving links and forwarded headers on the real `/api/v1/movies`, and a web-level `page=2147483647&size=100`.

No API contract, response shape, DB schema or migration change. Not breaking.

## Non-goals

- Any other change to the API contract or UC-002 behaviour.
- Splitting `MoviePersistenceAdapter` or `GlobalExceptionHandler` for class length (S9, `clean-code.md` §1). Item 2 does not need it.
- Naming `@PathVariable` constraint violations in the `400` detail (the existing generic `400` stays). See design Open Questions.
- Findings recorded only on `experiment/*` branches; R7 (style).
- UC-003 work, new features, migrations.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `platform/uniform-responses`: adds a requirement classifying constraint violations by ownership — named `400` only when every violation is on the handler's parameters, otherwise `500`.
- `catalog/movies`: the title requirement gains a quote-in-term acceptance check; the standalone sample-movie requirement drops the genre-order check on H2 (order is verified on PostgreSQL by the genre-order requirement).
- `platform/collection-paging`: the navigation-links acceptance check runs on the real `GET /api/v1/movies` against PostgreSQL, not a test-only controller.

## Impact

- `src/main/.../catalog/movies/adapters/out/persistence/MoviePersistenceAdapter.java` (title predicate).
- `src/main/.../platform/web/GlobalExceptionHandler.java` (constraint-violation classification).
- Tests: `SearchMoviesOrderingAndPagingTest`, `SearchMoviesFilteringTest`, `CollectionLinksFactoryForwardedHeadersTest`, `MovieSearchRefusalTest`, `H2DefaultRuntimeSmokeTest`, `ConstraintViolationHandlingTest` and its test-only `@Validated` API/controller, `MovieSearchEndToEndTest`.
- Suite time: a few more PostgreSQL tests on the shared container.
