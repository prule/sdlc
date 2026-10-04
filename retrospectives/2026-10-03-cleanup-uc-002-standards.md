---
date: 2026-10-03
change: cleanup-uc-002-standards
input: tickets/CLEAN-001-uc-002-standards-cleanup.md
branch: chore/r5-uc-002-cleanup
outcome: archived
agent_runs: 7
session: 4ef81b30-f4c9-4f49-aa6f-bc19689be5c9
session_report: reports/sessions/4ef81b30-f4c9-4f49-aa6f-bc19689be5c9.report.html
note: First run with the retrospective step and the Run-log findings blocks. The session also holds the earlier presentation and experiment work, so its report covers more than this run.
---

# Run retrospective — cleanup-uc-002-standards

## Summary

CLEAN-001 brought the merged UC-002 code up to the amended standards:
- the title search value is now bound, proved by checking the captured SQL;
- constraint violations not wholly on handler parameters now return 500;
- seven test-standard problems are fixed;
- the missing PostgreSQL acceptance checks are added.

The plan needed a second round, because its key test couldn't fail on the buggy code. QA passed the change, then the senior-dev caught a regression: an empty set of violations satisfied "all are parameter violations". Final build: 321 tests. **Most worth improving (recurs):** tasks restated acceptance checks loosely instead of testing the spec's exact scenarios.

## Gate log

| # | Gate | Round | Verdict | Blocking | Advisory | Notes |
|---|------|-------|---------|----------|----------|-------|
| 1 | spec-reviewer | 1 | REQUEST CHANGES | 1 | 6 | `openspec validate --strict` passed |
| 2 | spec-reviewer | 2 | APPROVE | 0 | 0 | P1–P6 fixed; 3 questions left for the human |
| 3 | Gate 1 (human) | — | proceed | — | — | S9: follow-up ticket · path variables: narrow §3 · §3 wording: loosen |
| 4 | qa | 1 | READY TO ARCHIVE | 0 | 0 | 320 tests; 1 test added; verified the implementer's 3 claims (R3 working) |
| 5 | senior-dev | 1 | APPROVE (2 fixed in place) | 0 | 0 | 321 tests |
| 6 | Gate 2 (human) | — | archive | — | — | |

## Failed reviews

### F1 — spec-reviewer, round 1: REQUEST CHANGES

- **What failed:** the test meant to prove the title is bound (D1, task 2.1) checked that no SQL
  contained `n's e`. When Hibernate inlines the value it doubles the quote (`n''s e`), so the check
  would pass on the buggy `cb.literal` code. The fix would have shipped with no regression pin.
  There were also six advisories:
  - test the quote case through the endpoint;
  - two summary checks missing from the spec;
  - empty `title=`;
  - a git pathspec glob;
  - H2 wording;
  - the S9 contradiction in the ticket.
- **Root cause:** model slip. The design noticed the quote escaping for the behaviour check but not
  for the SQL check. The advisories are plan omissions, with tasks paraphrasing the ticket instead of the spec.
- **Preventable?** yes
- **Recommendation:** → R1, R4

## Standards violations

| # | Rule | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| S1 | `standards/testing.md` §1, `clean-architecture.md` §5 (test must fail on regression; values bound) | design D1, task 2.1 (SQL check couldn't fail) | spec-reviewer | fixed-by-rework | → R4 |
| S2 | `standards/error-handling.md` §3 (note: name `@PathVariable`s) | uniform-responses delta pinned the generic detail for path segments as a SHALL | spec-reviewer | fixed-by-rework | → R5 |
| S3 | `standards/error-handling.md` §3 (only parameter violations are client faults) | `GlobalExceptionHandler.java:102`: `allMatch` true on an empty set → 400 instead of 500; null set → NPE | senior-dev | fixed-in-place | → R3 |
| S4 | `standards/testing.md` §1 (each scenario tested) | `MovieSearchEndToEndTest`: no web-level test of `title=%20%20` | qa | fixed-in-place | → R1 |
| S5 | `standards/testing.md` §1 | `MovieSearchEndToEndTest.search(String)` built requests from a URL template (re-encodes `%`) | qa, senior-dev | fixed-in-place | → R2 |

## Other defects caught

| # | Kind | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| D1 | plan-defect | design D2: container-element violations "accepted, not tested" | spec-reviewer | fixed-by-rework (test-only `List<@Min(0) Integer>` operation) | → R6 |
| D2 | test-gap | tasks 5.1/5.2/2.1 paraphrased the ticket: missing `genres` `[]`, following `self`, empty `title=`, web-level `200` | spec-reviewer | fixed-by-rework | → R1 |
| D3 | plan-defect | ticket CLEAN-001: done criteria said S6–S11 fixed while out-of-scope excluded S9 | spec-reviewer | fixed-by-rework (S9 → deferred) | → R8 |
| D4 | plan-defect | task 1.1 git pathspec without `:(glob)`; task 4.5 wording | spec-reviewer | fixed-by-rework | none: one-off |

## Recommendations

- [x] **R1** — Tasks must test each spec `#### Scenario` with its exact request and cite the spec
  requirement by name, copying acceptance checks from the spec rather than from a ticket's
  paraphrase → `openspec/config.yaml` (tasks rules), `.claude/agents/architect.md` (from F1, S4, D2)
  · recurs: `2026-09-28-add-movie-search.md` (S10: spec acceptance checks not tested as specified) · adopted in #42
- [x] **R2** — MockMvc requests with pre-encoded queries use `get(URI.create(...))` or `.param(...)`,
  never a string URL template (the template is encoded again, so `%20` becomes `%2520` with no
  error) → `standards/testing.md` §4 (from S5; raised independently by qa and senior-dev) · adopted in #42
- [x] **R3** — Any "every X satisfies P" rule in a design (classification, validation, ownership)
  must state the outcome for an empty or null X, with a test → `openspec/config.yaml` (design rules) (from S3) · adopted in #42
- [x] **R4** — How to prove a value is bound: check the captured SQL for a bind placeholder, and
  for neither the raw nor the SQL-escaped value. A quote alone proves nothing, because Hibernate
  escapes inlined quotes → `standards/testing.md` (next to `clean-architecture.md` §5) (from F1, S1) · adopted in #42
- [x] **R5** — Gate 1 decision: narrow `error-handling.md` §3 so the named `detail` applies to query
  parameters only, and path variables keep the generic detail → `standards/error-handling.md` (from S2) · adopted in #42
- [x] **R6** — Gate 1 decision: reword §3 ownership to "the node after the handler's METHOD node is a
  PARAMETER node; deeper element or property nodes are allowed", matching the tested behaviour
  → `standards/error-handling.md` (from D1) · adopted in #42
- [ ] **R7** — Gate 1 decision: follow-up ticket to split `MoviePersistenceAdapter` (now 254 lines,
  three ports) and `GlobalExceptionHandler` (now 233) before UC-003 → code fix (ticket)
  (from `2026-09-28-add-movie-search.md` S9, now deferred)
- [x] **R8** — Technical-ticket template: done criteria list the finding IDs they close, and
  findings left out of scope are marked `deferred` → ticket template / `.claude/agents/ticket-writer.md` (from D3) · adopted in #42
