# CLEAN-001: Bring the merged UC-002 code up to the amended standards

**Type:** Technical
**Area:** refactor (catalog/movies search, platform/web error handling, tests)
**Status:** Ready
**Source:** recommendation **R5** in `retrospectives/2026-09-28-add-movie-search.md`

## Problem / rationale

UC-002 (search & browse movies, #37) shipped with findings that a blind post-merge review and the
retrospective recorded as **outstanding**. Since then the standards were tightened (#38, #40), and
the merged code now breaks several of them. One is a real defect: the title search term is written
into the SQL text instead of bound. The rest are weak or rule-breaking tests, and acceptance checks
from the UC-002 spec delta that were never tested against PostgreSQL.

Fixing these now keeps `develop` conforming before the next capability (UC-003) builds on the
same search, paging and error-handling code.

## Scope

1. **Bind the title search value** (S11, `standards/clean-architecture.md` §5). The title LIKE pattern
   in the search persistence adapter must reach the database as a bind parameter, not a literal in
   the SQL text. Pin it with a test whose title term contains a quote.
2. **Server-side constraint violations are 500** (D5, `standards/error-handling.md` §3). A
   `ConstraintViolationException` that is not wholly on the handling controller's method
   parameters (for example a constraint on a handler's return value) must produce 500
   `INTERNAL_ERROR`, logged at ERROR, not a 400. Parameter violations keep their named 400.
3. **Test-standard violations** (`standards/testing.md` §1, §3, §4; `standards/clean-code.md` §6):
   - The page-walk helper in the ordering/paging persistence test loops with `while (true)`. A
     traversal loop needs a hard upper bound that fails the test, and no conditionals inside.
   - The forwarded-headers links test asserts inside a `for` loop over relations. Make it a
     parameterized test or a single assertion over the collected values.
   - The "every supported sort produces a stable, complete order" test only checks the count. It
     must check the actual order each supported sort produces.
   - An unused local `JsonNode body` in the movie-search refusal test.
   - The H2 smoke test checks that a sample movie's genres are in alphabetical order. Order and
     mapping checks are not allowed on H2. Remove it there, and make sure a PostgreSQL test covers
     genre ordering for movie details.
4. **Missing PostgreSQL acceptance checks** from the UC-002 spec delta (S10). Each needs a test
   against real PostgreSQL (Testcontainers):
   - Summary contents read from the database: the exact member set; a rating stored as `5.0`
     written as `5`; absent runtime, rating and genres omitted.
   - Paging defaults over 25 movies: 20 by default, 5 on `page=1`, 7 with `size=7`, and walking with
     `size=7` lists the same movies in the same order as one `size=100` page.
   - Title matching at the database: `title=rand h` (substring spanning a space) and a blank
     `title=` (browses everything).
   - Single-bound year filters: `releaseYearFrom=` only and `releaseYearTo=` only.
   - On the real `/api/v1/movies` endpoint: each navigation link's decoded query equals the
     request's recognised parameters (with the target page), and the links honour forwarded
     headers. The current forwarded-headers test uses a test-only controller.
   - A web-level `page=2147483647&size=100` request returns 200 with an empty page.

## Out of scope

- Any change to the API contract, response shapes or UC-002 behaviour, other than item 2's
  400 → 500 for server-side violations.
- Class-length flags (`clean-code.md` §1) on the persistence adapter and the exception handler.
  Split them only if item 2 genuinely needs it.
- Findings recorded only on the `experiment/*` branches, and R7 (a style point).
- New features, UC-003 work, migrations.

## Constraints (standards that apply)

- `standards/clean-architecture.md` §5 (values bound) and §9.
- `standards/error-handling.md` §3 (the two `ConstraintViolationException` rows and their note).
- `standards/testing.md` §1–§5. In particular: Testcontainers for every database assertion; the H2
  smoke test limited to status, shape and seeded-record presence; loops only for fixtures and
  bounded traversal.
- `standards/openapi.md` §2a: no contract change; paging links keep echoing only the parameters
  actually sent.

## Acceptance / done criteria

- [ ] No variable value reaches a query via `cb.literal(...)` or string concatenation anywhere in
      `src/main`. A Testcontainers test with a quote in the title term passes.
- [ ] A test proves a non-parameter constraint violation on a controller method gives 500
      `INTERNAL_ERROR` with no detail leaked, and parameter violations still give the named 400.
- [ ] No test contains an unbounded loop or assertions inside a loop. The H2 smoke test asserts no
      order, filter results or field mapping.
- [ ] The sort test fails if any supported sort returns the wrong order (it checks the order, not
      just the count).
- [ ] Every item under scope §4 has a passing PostgreSQL test.
- [ ] `./gradlew build` is green. The blind-review violations S6–S11 and D5 in
      `retrospectives/2026-09-28-add-movie-search.md` can be marked fixed.

## Risks

- Item 2 changes an error status (400 → 500) for a case no generated operation triggers today, so
  client impact is nil. The test must build the case deliberately.
- New PostgreSQL tests add suite time. Reuse the shared container and the existing fixtures.

## Open questions

- None. Use the existing PostgreSQL test fixtures and container.
