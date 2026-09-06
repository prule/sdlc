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

## Notes / gotchas

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
