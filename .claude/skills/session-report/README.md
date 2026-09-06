# Session Report

Turn a **Claude Code session log** (`.jsonl`) into a single, self-contained HTML
report so you can *see* what happened in a run — especially in the multi-agent
SDLC pipeline (`architect → spec-reviewer → junior-dev → qa → senior-dev`).

It answers, at a glance:

- **What agents ran, when, and for how long** — a Gantt timeline.
- **Are the subagents pulling their weight?** — a value/efficiency table and
  auto-generated insights.
- **Where did the review gates earn their place?** — every reviewer run with its
  verdict and a snippet of what it caught.
- **What went wrong?** — failed commands, rejected tool calls, failed agents.
- **What decisions were made?** — a filterable, chronological activity feed.
- **What was touched?** — tools used (with time and errors) and every file read/written/edited.

This is a Claude Code [skill](https://docs.claude.com/en/docs/claude-code/skills):
Claude picks it up automatically when you ask to analyse a session. You can also
run the script directly.

## Quick start

```bash
# 1. Find the session log you want (newest first)
ls -lt ~/.claude/projects/-Users-paulrule-projects-current-sdlc/*.jsonl | head

# 2. Generate the report (collapses idle gaps so short runs stay visible)
python3 .claude/skills/session-report/session_report.py \
    ~/.claude/projects/-Users-paulrule-projects-current-sdlc/<session-id>.jsonl \
    --compact --open
```

The report is written to **`reports/sessions/<session-id>.report.html`** inside
the project, so it's versioned alongside the code it describes. Open it in any
browser — no server, no dependencies.

Or just ask Claude: *"analyse this session log"* / *"did the reviewers catch
anything in that run?"*

### Where are the logs?

Claude Code stores one `.jsonl` per session under
`~/.claude/projects/<slugified-project-path>/`. For this repo the folder is
`~/.claude/projects/-Users-paulrule-projects-current-sdlc/`. (The path
`.claude/sessions/…` does **not** exist — that was a guess; the real location is
the user-level `~/.claude/projects/` directory.)

## Usage

```
python3 session_report.py <session.jsonl> [-o OUTPUT] [--compact] [--open]

  <session.jsonl>   Path to the session log.
  -o, --output      Output HTML path. Default: reports/sessions/<name>.report.html
  --compact         Collapse idle gaps in the agent timeline (recommended —
                    runs span hours/days, so absolute-time bars become slivers).
  --open            Open the finished report in your browser.
```

No third-party packages — Python 3.8+ standard library only.

## What's in the report

| Section | What it tells you |
|---|---|
| **Summary cards** | Duration, agent runs, tool calls, errors, rejections, issues caught by gates, tokens. |
| **Insights** | Auto-generated callouts: which gates proved their value, which approved everything (low signal), where errors clustered, the slowest agent. |
| **Agent timeline** | Gantt of every agent/subagent run, coloured by type. Bar width = real duration; green outline = caught an issue; red = failed/rejected; hatched = still pending. With `--compact`, idle gaps are collapsed but widths stay proportional. |
| **Subagent value & efficiency** | Per-subagent table: runs, total time, average, gate catch-rate, errors. Reviewers show `caught N/M`; a gate that approves everything is flagged. |
| **Review-gate value** | Each review run (`spec-reviewer`, `senior-dev`, `qa`, …) with a verdict badge and a snippet of *what it caught* — the evidence that the gates are worth their cost. |
| **Errors & friction** | Failed commands, failed agents, and tool calls you rejected (where the agent guessed wrong). Your list of things to look into. |
| **Tool usage** | Every tool called (top-level **and** inside subagents), with call count, total time spent, and error count. |
| **Files touched** | Every file read / written / edited across the whole run (subagents included), ranked by activity, with per-operation counts and how many were written fresh (no prior read). |
| **Activity feed** | Chronological, filterable stream: prompts, decisions, tool calls (with durations), agent results, thinking. |

## Subagents are included

The multi-agent pipeline does most of its real work *inside* subagents
(`architect`, `junior-dev`, `qa`, …), and each gets its own full transcript at
`~/.claude/projects/<slug>/<session-id>/subagents/agent-<id>.jsonl`. The script
reads those too and folds them in:

- **Tool usage** and **Files touched** aggregate the top-level session *plus*
  every subagent — so you see all files read/written/edited across the run, not
  just the handful the orchestrator touched directly (e.g. 335 files instead of
  67 in one sample run).
- The **Subagent value & efficiency** table gains per-subagent columns —
  inner **Tool calls**, **Files**, and **Out tokens** — so you can see who did
  the heavy lifting (typically `junior-dev`) versus the lighter review gates.

Each transcript is matched back to its parent Agent call by prompt. Pass
`--no-subagents` to report the top-level session only.

## How it works (and its limits)

The parser reads the JSONL event stream and correlates each `tool_use` with its
matching `tool_result` (by `tool_use_id`) to compute durations and success.

Two details that matter for accuracy:

1. **Background agents.** Agents launched with `run_in_background: true` (e.g.
   `qa`, `senior-dev`) return an instant *"launched"* stub; their real duration
   and output arrive later in a `<task-notification>`. The script matches the
   notification back to the original launch, so async agents are timed by their
   real work, not by launch latency.
2. **Injected messages.** `<task-notification>`, `<system-reminder>` and other
   harness-injected user messages are filtered out of the "prompts" feed so they
   don't masquerade as things you typed.

**Verdict detection is heuristic.** Whether a review "caught something" is
inferred from the result text — formal tokens (`REQUEST CHANGES`), uppercase
severity labels (`CRITICAL`, `FAIL`, ❌), guarded against phrases like
"no CRITICAL issues" and adjectival "critically". It's accurate on the reviewers'
structured output but can misread unusually-worded results; the on-screen snippet
lets you verify, and unclassifiable runs show a verdict of `—`.

## Files

- `session_report.py` — the generator (stdlib only).
- `SKILL.md` — the skill definition Claude loads.
- `README.md` — this file.
- Reports land in `../../../reports/sessions/` (i.e. `reports/sessions/` at the
  repo root).
