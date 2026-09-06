---
name: "Write Use Case"
description: "Interactively draft a business use case (behaviour, not intent; no implementation), grounded in domain/, and save it to use-cases/."
argument-hint: "<rough idea for the use case>"
---

Help the user author a **business use case** — the use-case variant of `/write-ticket`, for the
use-case experiment (see `EXPERIMENT.md`). A use case describes **behaviour** end-to-end and stays at
the **business level**: NO implementation (no HTTP/endpoints/status codes/JSON/HAL/DB/frameworks/classes
— all of that is the architect's job downstream). Idea from the user:

$ARGUMENTS

## Steps

1. **Load context** — read `domain/` (overview, glossary, bounded-contexts, actors-and-personas,
   business-rules), `use-cases/TEMPLATE.md`, and skim `use-cases/` and `openspec/specs/` so the use
   case uses the right actors/language/context and doesn't duplicate prior work. (You may skim
   `standards/` for awareness, but do NOT cite implementation NFRs in the use case — it's business-level.)

2. **Interview the user for the gaps.** Ask only what you genuinely can't infer — use `AskUserQuestion`
   for the decisions that shape the behaviour (primary actor, the goal, main-flow shape, which
   alternative/exception courses matter, business-rule thresholds/ordering/visibility, non-goals).
   Don't ask what `domain/` already answers. Keep it to a couple of focused rounds.

3. **Draft** from `use-cases/TEMPLATE.md`: actors, goal, pre/postconditions, a numbered **main flow**,
   **alternative/exception flows** as first-class citizens (anchored to main-flow steps), and
   **centralized business rules** referenced by id. Behaviour and business language only — if you write
   *how*, restate it as observable behaviour or a business rule. Put genuine unknowns in **Open
   questions**, not silent assumptions.

4. **Save** to `use-cases/UC-<n>-<slug>.md` (next number). Show the user the draft and the path.

5. **Flag domain gaps** — if the use case needed a term/actor/rule missing from `domain/`, tell the user
   and offer to add it to the relevant `domain/` file.

6. Remind the user they can send it through the pipeline with `/build-ticket <path-to-use-case>` (the
   downstream pipeline is unchanged; the use case is theirs to approve first).

For non-interactive/delegated drafting, the `use-case-writer` agent does the same job in one shot
(surfacing questions instead of asking them live).
