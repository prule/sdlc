# Evaluating the Delivery Pipeline

A manual for figuring out **how well the multi-agent SDLC pipeline is working** —
whether each agent earns its place, whether the curated context (`domain/`,
`standards/`) helps, and where the run leaks time, tokens, or quality.

It is written to be used by anyone, not just the person who built the pipeline.
You do not need to understand the agents' internals — every question below is
answered from data the run already leaves behind.

---

## 1. What you are evaluating

The pipeline turns a requirement into merged, reviewed code through a chain of
agents, with two review gates:

```
ticket-writer / use-case-writer     (author the requirement)
        │
        ▼
   architect          → plans the change (OpenSpec proposal, design, spec, tasks)
        │
        ▼
   spec-reviewer      ← GATE 1: reviews the plan (APPROVE / REQUEST CHANGES)
        │
        ▼
   junior-dev         → implements the tasks (the workhorse)
        │
        ▼
   qa                 ← GATE 2: verifies against spec + tests
        │
        ▼
   senior-dev         ← final code-review gate, fixes issues, then archive
```

Each agent runs as a **subagent** with its own transcript. "How well is it
working?" decomposes into four concrete questions:

| # | Question | Kind of answer |
|---|---|---|
| Q1 | Does each agent do useful work, or is it dead weight? | Observational (one run) |
| Q2 | Do the **review gates** actually catch things? | Observational (one run) |
| Q3 | Is the curated **context** (`domain/`, `standards/`) read and used? | Observational (one run) |
| Q4 | Does a change to the pipeline (context, input format, an agent) make outcomes **better or worse**? | Comparative (needs two runs) |

Q1–Q3 you can answer from a single run. **Q4 is causal** and can only be answered
by comparing two runs — see [§6](#6-comparative-evaluation-the-only-way-to-prove-better-or-worse).

---

## 2. The one tool you need

Everything here is driven by the **`session-report`** skill, which turns a
session log into a self-contained HTML report. Full tool docs:
[`.claude/skills/session-report/README.md`](../.claude/skills/session-report/README.md).

Session logs live at:

```
~/.claude/projects/<slugified-project-path>/<session-id>.jsonl
```

For this repo the folder is
`~/.claude/projects/-Users-paulrule-projects-current-sdlc/`. Each subagent has its
own transcript under `<session-id>/subagents/`, and the report folds those in
automatically.

Generate a report for a run:

```bash
# newest session first
ls -lt ~/.claude/projects/-Users-paulrule-projects-current-sdlc/*.jsonl | head

python3 .claude/skills/session-report/session_report.py \
    ~/.claude/projects/-Users-paulrule-projects-current-sdlc/<session-id>.jsonl \
    --compact --open
```

The report is written to `reports/sessions/<session-id>.report.html` (versioned
with the project). Or just ask Claude: *"analyse this session"*.

---

## 3. The three-layer framework

Keep these layers separate — conflating them is the most common evaluation
mistake.

1. **Ingestion** — was the context/work *available*? (e.g. was `security.md`
   read at all?) Cheap, deterministic, visible in one run.
2. **Utilisation / attribution** — was it *used*? (cited in reasoning; a catch
   that names the doc). Visible in one run.
3. **Effect on outcome** — did it make the result *better*? This is a
   counterfactual: **reading ≠ benefit, and a catch ≠ net value**. Only a
   comparison of two runs can answer it.

The report covers layers 1–2 directly. Layer 3 needs `--compare` (§6).

---

## 4. Evaluating a single run

Open the report and read it top to bottom. Each section answers a specific
question:

| Report section | Answers | What "good" looks like |
|---|---|---|
| **Summary cards** | Scale & cost of the run | — |
| **Insights** | Auto-generated callouts | Read these first; they flag the obvious wins and smells |
| **Agent timeline** (Gantt) | *When* each agent ran, how long, overlaps | Work is progressing, not stalled; parallel where possible |
| **Subagent value & efficiency** | Q1 — who does the work, who catches issues, at what cost | Producers do most tool calls/files; gates are cheaper but catch things |
| **Review-gate value** | Q2 — what each reviewer actually caught | Gates with real, specific findings (not rubber-stamps) |
| **Errors & friction** | Where the run stumbled | Few command errors; few rejected tool calls |
| **Tool usage** | Where time/effort went per tool | No single tool dominating unexpectedly |
| **Files touched** | What was read/written/edited across the whole run | Writes concentrated in the change's area |
| **Context ingestion** | Q3 — are `domain/`/`standards/` read & used | High-influence docs read *before* writing; catches cite docs |
| **Activity feed** | The narrative — prompts, decisions, tool calls | Sanity-check the reasoning where a metric surprises you |

### Reading the gate value (Q2)

A review gate proves its worth by **catching real, specific issues** — not by
approving everything. In the **Review-gate value** panel each reviewer run shows a
verdict badge and a snippet of what it caught. In the **Subagent value** table
each gate shows a catch-rate (`caught N/M`); a gate that shows `0/M — approved
all` is either downstream of very clean work or is a rubber-stamp. Investigate
which.

> Verdicts are inferred from the reviewer's text (formal tokens like
> `REQUEST CHANGES`, uppercase severity labels). They're accurate on structured
> reviewer output but not infallible — the snippet is shown so you can verify.

### Reading context ingestion (Q3)

The **Context ingestion** panel scores each `domain/`/`standards/` doc by:

- **Reads** — times an agent opened it;
- **Informed** — share of reads that happened *before* the agent's first write
  (so the doc could actually shape the output);
- **Cited** — times the agent's own reasoning referenced the doc;
- **Influence** = reads + citations;
- flags for **never read** (dead weight) and **read but never cited** (opened,
  but did it change anything?).

**Catches attributed to a doc** are the strongest single-run signal of value: a
`REQUEST CHANGES` that cites `clean-architecture.md` is direct evidence that
document earned its place.

> `CLAUDE.md` is always in every agent's context and is **not** counted as a
> read. A doc showing "never read" may still be reaching agents via the summary
> in `CLAUDE.md`.

---

## 5. The question → answer cheat sheet

| You want to know… | Look at… |
|---|---|
| Is `junior-dev` (or any agent) actually doing the work? | Subagent value table — tool calls / files / tokens per agent |
| Is a review gate worth keeping? | Review-gate value + its catch-rate in the value table |
| Did the reviewers catch standards violations specifically? | Context ingestion → "catches citing a doc" |
| Which standard/domain doc pulls its weight? | Context ingestion → Influence column |
| Which doc is ignored? | Context ingestion → never-read / read-but-never-cited flags |
| Where did the run waste time or error out? | Errors & friction; Tool usage (total time) |
| Was the plan wrong (lots of rework)? | Count of `REQUEST CHANGES` / `FAIL` gate runs |
| How expensive was the run? | Summary cards (tokens); Subagent value (out tokens per agent) |
| Did I confuse the agent (guessed wrong)? | Errors & friction → "rejected" entries |

---

## 6. Comparative evaluation — the only way to prove "better or worse"

Single-run metrics show what *happened*, never what *would have happened
otherwise*. To prove a pipeline change helps or hinders, run the **same
requirement twice**, changing exactly **one** thing, and diff the outcomes.

This is an **ablation**. Because this repo is an experiment harness (see
[`EXPERIMENT.md`](../EXPERIMENT.md), which ablates the *input format* — user
stories vs use cases), it is cheap to do.

### Protocol

1. **Baseline.** Reset the repo to the pre-run state. Run the requirement through
   the pipeline. Note its session id.
2. **Change one variable.** For a context ablation: remove or trim `domain/`
   and/or `standards/` (or a single file). For an input-format ablation: swap the
   ticket for a use case. For an agent ablation: drop a gate. **Change only one
   thing** or the comparison is uninterpretable.
3. **Re-run** the *same* requirement from the same reset state.
4. **Compare** the two session logs:

```bash
python3 .claude/skills/session-report/session_report.py <baseline>.jsonl \
    --compare <variant>.jsonl \
    --label-a "full context" --label-b "stripped"
```

This writes `reports/sessions/compare-<a>-vs-<b>.report.html`: a metric-by-metric
diff. Green/red is applied only where the direction is unambiguous (fewer
errors/rejections/tokens = better; more doc-cited catches = better). Ambiguous
metrics are shown without a verdict, because their meaning depends on your
hypothesis (see below).

### Interpreting an ablation

There is no single "score". Read the deltas as a story:

- **Stripped context catches *more* issues late** → the context was *preventing*
  defects upstream. It helped.
- **Stripped context catches *fewer* issues but ships worse code** → the gates
  lost their reference and are now missing things. The context helped.
- **Same issues caught, but full context costs many more tokens** → the context
  is *hindering on cost* for no quality gain. Trim it.
- **More errors / rejections without the context** → agents were guessing; the
  context was steering them.

Always pair the compare table with a look at the two full reports — the numbers
tell you *where* to look, the reports tell you *why*.

### Making it fair

- Change exactly one variable between the two runs.
- Start both from the identical repo state.
- Prefer the same requirement, or run several requirements and look at the trend
  — one run can swing on luck.
- Watch cost (tokens) as a first-class outcome, not an afterthought.

---

## 7. Metrics glossary

| Metric | Meaning | Better when |
|---|---|---|
| **Agent runs** | Total subagent invocations | Fewer, for the same output (less rework) |
| **Issues caught by gates** | Gate runs whose verdict flagged something | Context-dependent — see §6 |
| **Errors** | Failed commands / failed agents | Lower |
| **Rejections** | Tool calls a human declined | Lower (less agent misfire) |
| **Tool calls (incl. subagents)** | All tool use across the run | Lower for equal output (efficiency) |
| **Files touched** | Distinct files read/written/edited | Context-dependent |
| **Output tokens** | Generation cost | Lower for equal quality |
| **Influence** (per doc) | reads + citations | Higher = the doc is engaged with |
| **Informed %** (per doc) | reads before the agent's first write | Higher = context shaped the work |
| **Catches citing a doc** | Gate findings that name the doc | Higher = doc demonstrably useful |

---

## 8. Red-flags checklist

Skim these on any run:

- [ ] A **gate approves everything** (`0/M`) across several runs → rubber-stamp?
- [ ] A **doc is never read** and not summarised in `CLAUDE.md` → dead weight.
- [ ] A **doc is read but never cited** anywhere → not influencing output.
- [ ] **High rejection count** → the agent repeatedly guessed wrong; unclear
      instructions or missing context.
- [ ] **Repeated `REQUEST CHANGES` → re-plan cycles** on one change → the plan
      stage isn't landing first time.
- [ ] **One agent dominates tokens** with little to show (few files, no catches)
      → cost without value.
- [ ] **Errors clustered in one tool** → an environment or instruction problem.

---

## 9. Limits & honesty

- **Verdict / attribution detection is heuristic.** It reads the agents' text; it
  is accurate on structured output but can misclassify unusually-worded results.
  Snippets are shown so you can verify; treat borderline calls as prompts to look,
  not as facts.
- **Reads and citations are not proof of benefit.** They are necessary, not
  sufficient. Only §6's comparison establishes effect.
- **One run is anecdote.** Wall-clock spans days and swings on luck; prefer trends
  across several runs, and lean on token cost over wall time.
- **`CLAUDE.md` context is invisible here** — it's always loaded and not counted
  as a read.

---

## 10. Where things live

| Thing | Path |
|---|---|
| Report generator (skill) | [`.claude/skills/session-report/`](../.claude/skills/session-report/) |
| Generated reports | [`reports/sessions/`](../reports/sessions/) |
| Curated domain context | [`domain/`](../domain/) |
| Engineering standards | [`standards/`](../standards/) |
| Input-format experiment | [`EXPERIMENT.md`](../EXPERIMENT.md) |
| Pipeline agents | `.claude/agents/` |
| Session logs (raw) | `~/.claude/projects/<slug>/<session-id>.jsonl` |
