---
name: session-report
description: Turn a Claude Code session log (.jsonl) into a beautiful, self-contained HTML report — an agent timeline (Gantt), a subagent value/efficiency table, which review gates caught issues, an errors & friction panel, and a filterable activity feed. Use when the user wants to analyse a session, see what agents ran when and how long they took, review pipeline efficiency, or find where things went wrong.
allowed-tools: Bash(python3:*), Read, Glob
license: MIT
metadata:
  author: paulrule
  version: "1.0"
---

Generate an HTML report from a Claude Code session log so a human can see, at a
glance, what happened in a run: which agents ran, when, for how long, what they
decided, where errors occurred, and whether the review gates earned their place.

## When to use

- "Analyse / visualise this session", "what agents ran", "how long did the
  pipeline take", "where did it go wrong", "did the reviewers catch anything",
  "are the subagents actually useful".

## Input

A session log path. These live under
`~/.claude/projects/<slugified-project-path>/<session-id>.jsonl`. For this repo
the slug is `-Users-paulrule-projects-current-sdlc`. If the user doesn't give a
path, list recent logs and ask which one (newest is usually the one they mean):

```bash
ls -lt ~/.claude/projects/-Users-paulrule-projects-current-sdlc/*.jsonl | head
```

## Steps

1. Run the generator. **Always pass `--compact`** — pipeline runs span hours or
   days with long idle gaps, and compact collapses the dead air so short agent
   runs stay visible. Reports default into `reports/sessions/` in the project so
   they're versioned alongside the code (that is the intended home — do not
   redirect them elsewhere unless the user asks):

   ```bash
   python3 .claude/skills/session-report/session_report.py <log.jsonl> --compact
   ```

   This writes `reports/sessions/<session-id>.report.html` and prints a one-line
   summary (events, agent runs, issues caught, errors).

2. Send the report to the user with `SendUserFile` (display: render) so they can
   open it, and relay the printed summary line.

3. If the user is drawing conclusions about pipeline efficiency, point them at
   the **Insights**, **Subagent value & efficiency**, and **Review-gate value**
   sections — those are the ones built to show whether each subagent is pulling
   its weight.

## Measuring whether domain/ & standards/ context helps

If the user asks whether the curated `domain/` and `standards/` docs are being
read / are helping or hindering:

- The **Context ingestion** panel already answers "are they read and used?" —
  per-doc reads, informed reads (before first write), citations, influence
  score, never-read / read-but-never-cited flags, reviewer catches attributed to
  the doc they cite, and a **Value/1K** (influence per 1000 tokens) signal-density
  score that flags large-but-rarely-used docs as `wordy / low-signal?`. The
  **Subagent value** table also shows **which model** each agent ran. Point them
  there first.
- "Helping vs hindering" is a causal question that needs a counterfactual, not a
  single run. Recommend an **ablation**: run the same ticket with vs without (or
  with trimmed) context, then diff the two runs:

  ```bash
  python3 .claude/skills/session-report/session_report.py <full>.jsonl \
      --compare <stripped>.jsonl --label-a "full" --label-b "stripped"
  ```

  This writes a `compare-…report.html` diffing outcome metrics (issues caught,
  errors, rework, tool calls, output tokens = cost, context reads/catches).
  Don't claim causation from one run's reads alone.

## Notes / gotchas

- **Subagents are included by default.** Each subagent has its own transcript at
  `<session-id>/subagents/agent-<id>.jsonl`; the script reads them all, folds
  their tool/file activity into the Tool-usage and Files-touched panels, and adds
  per-subagent workload columns (inner tool calls, files, output tokens) to the
  value table. Matched to parent Agent calls by prompt. Pass `--no-subagents`
  for top-level only.
- **Background agents** (`run_in_background: true`, e.g. `qa`, `senior-dev`)
  return an instant "launched" stub; their real duration and output arrive later
  in a `<task-notification>`. The script correlates the two by tool-use-id, so
  async agents are timed correctly — don't "fix" the near-instant stub yourself.
- **Verdict detection is heuristic** — it keys off formal tokens
  (`REQUEST CHANGES`, uppercase `CRITICAL`/`FAIL`, ❌) with a "no/0 critical"
  guard. It's good, not perfect; the on-screen snippet lets the reader verify.
  A couple of runs may show verdict `—` when unclassifiable.
- Standard library only (Python 3.8+); no dependencies, no network.
- Full documentation and the report anatomy: see the README beside this file.
