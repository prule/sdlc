---
date: 2026-10-03
change: add-movie-search
input: use-cases/UC-002-search-and-browse-movies.md
branch: experiment/pipeline-opus-uc-002 (experiment; not merged)
outcome: archived
agent_runs: 7
session: ec2d9e49-5794-4d19-831d-f98489aa6b14
session_report: reports/sessions/ec2d9e49-5794-4d19-831d-f98489aa6b14.report.html
note: Backfilled on 2026-10-03 from the session log and a blind review. Experiment re-run of UC-002 from 20d6e25 with every agent on Opus; standards as they were before #38.
---

# Run retrospective — add-movie-search (all-Opus experiment)

## Summary

The same use case re-run with every agent on Opus: 44 minutes, about $9.50, 340 green tests, every
flow and rule tested, one low defect in blind review. The plan again needed a second round, and the
headline finding **recurred**: the architect again planned to leave `@Validated` off the controller.
The senior-dev caught the title search term being written into the SQL text instead of bound.
**Most worth improving:** the H2 smoke test again ended up checking query results. The rule against
it was implicit, and the spec still asked for H2 query checks.

## Gate log

| # | Gate | Round | Verdict | Blocking | Advisory | Notes |
|---|------|-------|---------|----------|----------|-------|
| 1 | spec-reviewer | 1 | REQUEST CHANGES | 1 | 8 | 3 of the advisories marked "should fix" |
| 2 | spec-reviewer | 2 | APPROVE | 0 | 2 | all 9 findings fixed |
| 3 | Gate 1 (human) | — | proceed | — | — | no title length limit for now |
| 4 | qa | 1 | READY TO ARCHIVE | 0 | 0 | 339 tests; added 2 end-to-end tests |
| 5 | senior-dev | 1 | APPROVE (1 fixed in place) | 0 | 1 | 340 tests |
| 6 | Gate 2 (human) | — | archive | — | — | |

## Failed reviews

### F1 — spec-reviewer, round 1: REQUEST CHANGES

- **What failed:**
  - **Blocking:** D3 planned to leave `@Validated` off `MovieController` because it "would force
    the AOP path". The generated interface already carries it, so the controller is on that path
    regardless, and the deviation wasn't raised at Gate 1.
  - **Should fix:**
    - Every `ConstraintViolationException` was mapped to 400, including server-side ones that
      should be 500.
    - The reusable paging types were placed in the movies slice.
    - One spec acceptance check had no task.
- **Root cause:** missing/unclear standard (§2a said "class-annotated `@Validated`" without saying
  how it relates to the generated interface; error-handling had no 500 counterpart) · plan omission.
- **Preventable?** yes.
- **Recommendation:** → R1, R6.

## Standards violations

| # | Rule | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| S1 | `standards/openapi.md` §2a (`@Validated`) | design D3 | spec-reviewer | fixed-by-rework | → R1 |
| S2 | `standards/clean-architecture.md` (cross-context coupling) | `PageRequest`/`ResultPage` in `catalog.movies.domain` | spec-reviewer | fixed-by-rework | none: caught by the gate |
| S3 | `standards/formatting.md` | import order and line length in 4 files | blind review | fixed on commit by the pre-commit hook | none: working as designed |
| S4 | `standards/testing.md` §3 (H2 smoke scope) | `H2DefaultRuntimeSmokeTest.java:62-75` (exact order), `:79-99` (filter results) | blind review | outstanding (experiment branch) | → R2 |
| S5 | `standards/testing.md` §4 (no loops in tests) | `MovieSearchAdapterTest.java:315-319`, `MovieSearchEndToEndTest.java:270-275` (traversal, no bound), `:242-244` (fixture: now allowed), `MovieControllerSearchFailureTest.java:89-91` | blind review | outstanding (experiment branch) | → R2 |
| S6 | `standards/clean-code.md` §3/§9 (≤3 parameters, no boolean flags) | `PageLinkBuilder.build(...)` (5 params, 2 booleans), `MovieSearchCriteria.of(...)` (5) | blind review | outstanding (experiment branch) | → R7 |
| S7 | `standards/openapi.md` §2a (hrefs via `WebMvcLinkBuilder`) | `PageLinkBuilder.java:86-114` | blind review | waived → standard amended (#38) | → R1 |
| S8 | `standards/clean-code.md` §9 (magic strings) | parameter names repeated as literals in 5 files | blind review | outstanding (experiment branch) | → R7 |
| S9 | `standards/clean-code.md` §1 (class ≲ 200 lines; flag) | `GlobalExceptionHandler.java` (282) | blind review | outstanding (experiment branch) | → R6 |

## Other defects caught

| # | Kind | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| D1 | code-defect (medium) | `MovieSearchAdapter.java`: title LIKE pattern built with `cb.literal()`, so it was written into the SQL text, not bound | senior-dev | fixed-in-place | → R8 |
| D2 | test-gap | credential-ignored rule tested only on `/ping`; comma-separated genres tested only at binding | qa | fixed-in-place (2 tests added) | → R4 |
| D3 | code-defect (low) | mixing repeated and comma-separated `genre` values is refused (`?genre=Drama,Sci-Fi&genre=Comedy` → 400) | blind review | outstanding (experiment branch) | → R4 |

## Recommendations

- [x] **R1** — §2a: allow `@Validated` inherited from the generated interface if a codegen test pins
  it; build paging links from the request URI → `standards/openapi.md` (from F1, S1, S7)
  · recurs: `2026-09-28-add-movie-search.md` · adopted in #38
- [x] **R2** — Spell out what the H2 smoke test may assert; spec rule for H2 runtime-mode scenarios;
  bounded traversal loops only → `standards/testing.md` §3–§4, `openspec/config.yaml` (from S4, S5)
  · recurs: `2026-09-28-add-movie-search.md` · adopted in #38
- [x] **R6** — Add the counterpart row: a `ConstraintViolationException` not raised on a web
  handler's method parameters is a 500. Consider moving parameter-name resolution out of
  `GlobalExceptionHandler` → `standards/error-handling.md` §3 (from F1, S9)
  · recurs: `2026-09-28-add-movie-search.md` · adopted in #40
- [x] **R4** — Add a spec rule for malformed and mixed-form inputs on every query parameter
  (including repeated plus comma-separated) → `openspec/config.yaml` (from D2, D3)
  · recurs: `2026-09-28-add-movie-search.md` · adopted in #40
- [x] **R8** — Persistence: JPA Criteria values are always bound with `cb.parameter`/`cb.value`,
  never `cb.literal` → `standards/clean-architecture.md` (outbound adapters) (from D1) · adopted in #40
- [ ] **R7** — Published parameter names live in one constants holder per operation, and link
  builders take a page result rather than flags → none: style; covered by clean-code §3/§9 (from
  S6, S8)
