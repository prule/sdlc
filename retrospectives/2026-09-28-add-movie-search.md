---
date: 2026-09-28
change: add-movie-search
input: use-cases/UC-002-search-and-browse-movies.md
branch: feat/uc-2-search-and-browse-movies (merged as #37)
outcome: archived
agent_runs: 8
session: 05ad576e-90db-460e-a21a-278fef817c63
session_report: reports/sessions/05ad576e-90db-460e-a21a-278fef817c63.report.html
note: Backfilled on 2026-10-03 from the session log and a blind post-merge review. junior-dev and qa ran on Sonnet.
---

# Run retrospective — add-movie-search

## Summary

UC-002 (search and browse movies) shipped with 298 green tests and every use-case flow and rule
tested. The plan needed a second round: the architect proposed dropping `@Validated` on a false
premise and asked the H2 smoke test to check query results. QA passed the change, then the
senior-dev found and fixed five real bugs, including two paths to a 500. **Most worth improving:**
the spec delta had no malformed-input scenarios, so QA's tests could not catch the 500s.

## Gate log

| # | Gate | Round | Verdict | Blocking | Advisory | Notes |
|---|------|-------|---------|----------|----------|-------|
| 1 | spec-reviewer | 1 | REQUEST CHANGES | 3 | 6 | `openspec validate --strict` passed |
| 2 | spec-reviewer | 2 | APPROVE | 0 | 3 | all round-1 findings fixed |
| 3 | Gate 1 (human) | — | touch up, then implement | — | — | 0-based pages confirmed; two advisories folded in |
| 4 | qa | 1 | READY TO ARCHIVE | 0 | 0 | 295 tests; endorsed a test-config claim that was false (see F3) |
| 5 | senior-dev | 1 | APPROVE (5 fixed in place) | 0 | 2 | 298 tests |
| 6 | Gate 2 (human) | — | archive | — | — | |

## Failed reviews

### F1 — spec-reviewer, round 1: REQUEST CHANGES

- **What failed:**
  - D2 planned to leave `@Validated` off the controller, assuming Spring's built-in validation would
    take over. The generated `MoviesApi` already carries `@Validated`, so the planned path could
    never run and its tests would have tested a path production never uses. Task 8.1 would also
    have written that wrong rule into `standards/openapi.md`.
  - Task 7.3 asked `H2DefaultRuntimeSmokeTest` to check browse order and title matching, which
    `standards/testing.md` §3 forbids.
  - D6 put the unknown-genre translation before the use-case call that throws it, so a literal
    implementation would return 500.
- **Root cause:** missing/unclear standard (§2a didn't say the generated interface supplies
  `@Validated`; §3 didn't say what the H2 test may assert) · plan omission (D6 ordering).
- **Preventable?** yes for the first two, partly for the third.
- **Recommendation:** amend `openapi.md` §2a and `testing.md` §3, and add a spec rule for H2
  runtime-mode scenarios → see R1, R2.

### F2 — Gate 1 (human): touch up, then implement

- **What failed:** not a rejection. Two round-2 advisories (D2's 400-vs-500 ordering, how the
  controller reaches the request) were folded in before implementation.
- **Root cause:** agent-instruction gap — the error-handling standard doesn't say which
  `ConstraintViolationException`s stay 500.
- **Preventable?** partly.
- **Recommendation:** → R6.

### F3 — qa, round 1: READY TO ARCHIVE, but the senior-dev reversed a QA finding

- **What failed:** QA investigated `MethodValidationTestConfig` and endorsed the junior-dev's claim
  that `@WebMvcTest` slices lack Spring Boot's validation setup. The senior-dev checked Boot 3.5.16
  and found the claim false: the config replaced the production validator in every slice test, so
  those tests proved a test double.
- **Root cause:** agent-instruction gap — QA treated the implementer's justification as evidence
  instead of a claim to verify.
- **Preventable?** yes.
- **Recommendation:** → R3.

## Standards violations

| # | Rule | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| S1 | `standards/openapi.md` §2a (`@Validated` on controller) | design D2, task 8.1 | spec-reviewer | fixed-by-rework | → R1 |
| S2 | `standards/testing.md` §3 (H2 smoke scope) | spec "both runtime modes", task 7.3 | spec-reviewer | fixed-by-rework | → R2 |
| S3 | `standards/testing.md` §2 (slice tests use production wiring) | `MethodValidationTestConfig.java` | senior-dev | fixed-in-place | → R3 |
| S4 | `standards/openapi.md` §2a (hrefs via `WebMvcLinkBuilder`) | `CollectionLinksFactory.java:34,95-102` | blind review | waived → standard amended (#38) | → R1 |
| S5 | `standards/openapi.md` §2a (`@Validated` on the class) | `MovieController.java:47-48` | blind review | waived → standard amended (#38) | → R1 |
| S6 | `standards/testing.md` §4 (no loops in tests) | `SearchMoviesOrderingAndPagingTest.java:178-190` (`while (true)`), `CollectionLinksFactoryForwardedHeadersTest.java:36-39` (asserts in a loop) | blind review | fixed (`cleanup-uc-002-standards`, CLEAN-001) | → R5 |
| S7 | `standards/testing.md` §1 (test fails on regression) | `SearchMoviesOrderingAndPagingTest.java:74-88` checks size, not order | blind review | fixed (`cleanup-uc-002-standards`, CLEAN-001) | → R5 |
| S8 | `standards/clean-code.md` §6 (dead code) | `MovieSearchRefusalTest.java:59` unused local | blind review | fixed (`cleanup-uc-002-standards`, CLEAN-001) | → R5 |
| S9 | `standards/clean-code.md` §1 (class ≲ 200 lines; flag) | `MoviePersistenceAdapter.java` (238, three ports), `GlobalExceptionHandler.java` (237) | blind review | deferred (flag only; CLEAN-001 out of scope) | → R5 |
| S10 | `standards/testing.md` §1/§5 (spec acceptance checks tested as specified) | summary contents, paging defaults, single-bound year filters and the huge-page request not checked against Postgres | blind review | fixed (`cleanup-uc-002-standards`, CLEAN-001) | → R4, R5 |
| S11 | `standards/clean-architecture.md` §5 (values bound, never inlined — added by R8) | `MoviePersistenceAdapter.java:140`: title LIKE pattern built with `cb.literal(pattern)` | adoption of R8 (same defect the senior-dev fixed in `2026-10-03-add-movie-search.md`; missed in this run) | fixed (`cleanup-uc-002-standards`, CLEAN-001) | → R5 |

## Other defects caught

| # | Kind | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| D1 | code-defect (high) | `SearchMoviesService.java`: genre names differing only in case → `IllegalStateException` → 500 on every genre search | senior-dev | fixed-in-place | → R4 |
| D2 | code-defect (high) | `CollectionLinksFactory.java`: malformed percent-encoding (`?title=%zz`) → 500 | senior-dev | fixed-in-place | → R4 |
| D3 | code-defect (medium) | `GlobalExceptionHandler.java`: `@RequestParam(name = …)` produced `Query parameter '' is invalid.` | senior-dev | fixed-in-place | → R4 |
| D4 | code-defect (low) | `MoviePersistenceAdapter.java`: a movie deleted between the two queries → NPE | senior-dev | fixed-in-place | none: one-off |
| D5 | plan-defect (low) | D2: return-value violations get 400, not 500 (unreachable today) | senior-dev | fixed (`cleanup-uc-002-standards`, CLEAN-001) | → R6 |

## Recommendations

- [x] **R1** — `openapi.md` §2a: allow `@Validated` inherited from the generated interface if
  `GeneratedApiCodegenTest` pins it; build paging links from the request URI, not `methodOn`
  → `standards/openapi.md` (from F1, S1, S4, S5) · recurs: `2026-10-03-add-movie-search.md` · adopted in #38
- [x] **R2** — Spell out what the H2 smoke test may assert, and add a spec rule keeping H2
  runtime-mode scenarios to "responds with seeded data" → `standards/testing.md` §3,
  `openspec/config.yaml` (from F1, S2) · recurs: `2026-10-03-add-movie-search.md` · adopted in #38
- [x] **R4** — Add a spec rule: every query parameter gets malformed-input scenarios (bad
  percent-encoding, wrong type, empty, repeated), and every risk listed in the design gets an
  acceptance check → `openspec/config.yaml` (from D1–D3, S10) · adopted in #40
- [x] **R3** — Slice tests must run the production framework wiring; a test-only replacement of a
  framework bean needs a cited reason. QA verifies an implementer's justification against the
  framework source instead of accepting it → `standards/testing.md` §2, `.claude/agents/qa.md`
  (from F3, S3) · adopted in #40
- [x] **R6** — Add the counterpart row: a `ConstraintViolationException` not raised on a web
  handler's method parameters (a service bean, a return value, a JPA flush) is a 500
  → `standards/error-handling.md` §3 (from F2, D5) · recurs: `2026-10-03-add-movie-search.md` · adopted in #40
- [x] **R5** — Cleanup change for the outstanding code findings S6–S11 and D5 (return-value violations now break the R6 row) → code fix (ticket) · adopted as CLEAN-001 (`cleanup-uc-002-standards`); S9 deferred to a follow-up split ticket
