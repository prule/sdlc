---
date: YYYY-MM-DD
change: <openspec change name>
input: <use-cases/UC-n-<slug>.md, or the ticket title>
branch: <git branch>
outcome: archived | stopped-at-gate-1 | stopped-at-gate-2 | escalated | aborted
agent_runs: <n>
session: <session id>
session_report: reports/sessions/<session id>.report.html
---

# Run retrospective — <change>

## Summary

<2–4 sentences: what was built, how the gates went, and the single thing most worth improving.>

## Gate log

<One row per gate result, in order, including human gates and re-reviews.>

| # | Gate | Round | Verdict | Blocking | Advisory | Notes |
|---|------|-------|---------|----------|----------|-------|
| 1 | spec-reviewer | 1 | REQUEST CHANGES | 1 | 4 | |
| 2 | spec-reviewer | 2 | APPROVE | 0 | 0 | |
| 3 | Gate 1 (human) | — | proceed | — | — | |
| 4 | qa | 1 | READY TO ARCHIVE | 0 | 0 | |
| 5 | senior-dev | 1 | APPROVE (2 fixed in place) | 0 | 0 | |
| 6 | Gate 2 (human) | — | archive | — | — | |

## Failed reviews

<One entry per gate result that did not pass first time: REQUEST CHANGES, NOT READY, a senior-dev
hand-back, a human "revise" or "stop" at a gate, or an escalation/stall. Write "None — every gate
passed first time." if so.>

### F1 — <gate>, round <n>: <verdict>

- **What failed:** <the blocking findings, one line each>
- **Root cause:** <why the upstream step produced it: missing/unclear standard · use-case gap ·
  agent-instruction gap · plan omission · model slip · environment>
- **Preventable?** yes · partly · no
- **Recommendation:** <concrete change> → `<target file>`

## Standards violations

<Every standards finding from any gate, fixed or not. Write "None." if there were none.>

| # | Rule | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|
| S1 | `standards/testing.md` §3 | `H2DefaultRuntimeSmokeTest.java:74` | qa | fixed-in-place | <change> → `openspec/config.yaml` |

Status: **fixed-in-place** (the gate fixed it) · **fixed-by-rework** (routed back and fixed) ·
**outstanding** (shipped as is) · **waived** (accepted deviation — give the reason).

## Other defects caught

<Correctness bugs, test gaps and plan defects caught by the gates that are not standards findings.
Same columns as above. Write "None." if there were none.>

| # | Kind | Where | Found by | Status | Recommendation → target |
|---|------|-------|----------|--------|-------------------------|

## Recommendations

<Deduplicated, most valuable first. Each is a concrete change to the pipeline's inputs — a standard,
an `openspec/config.yaml` rule, an agent instruction, a `domain/` doc or the use-case template —
never "be more careful". Mark findings that also appear in earlier records. Tick a box when the
change is adopted and note the PR.>

- [ ] **R1** — <change> → `<target file>` (from F1, S1) · recurs: `<earlier record>.md`
