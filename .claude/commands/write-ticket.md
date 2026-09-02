---
name: "Write Ticket"
description: "Interactively draft a well-formed feature or technical ticket, grounded in domain/ and standards/, and save it to tickets/."
argument-hint: "<rough idea for the ticket>"
---

Help the user author a requirements ticket **interactively**. Capture **what** and **why** only —
no solution design or code (that's the architect's job downstream). Idea from the user:

$ARGUMENTS

## Steps

1. **Load context** — read `domain/` (overview, glossary, bounded-contexts, actors-and-personas,
   business-rules), the relevant `standards/`, `tickets/TEMPLATE.md`, and skim `openspec/specs/` and
   existing `tickets/` so the ticket uses the right language, personas, context, and NFRs and doesn't
   duplicate prior work.

2. **Classify** — Feature (business requirement) or Technical (enabler/chore)? State which and why.

3. **Interview the user for the gaps.** Ask only what you genuinely can't infer — use `AskUserQuestion`
   for the decisions that shape the ticket (persona/bounded context, scope boundaries & non-goals,
   key acceptance criteria, any policy values, priority). Don't ask what `domain/`/`standards/` already
   answer. Keep it to a couple of focused rounds.

4. **Draft** the ticket from `tickets/TEMPLATE.md`. Requirements altitude only; every acceptance
   criterion independently testable (happy/edge/failure); cite the standards that constrain it; put
   genuine unknowns in **Open questions**, not silent assumptions.

5. **Save** to `tickets/<AREA>-<n>-<slug>.md` (AREA from the bounded context, next number). Show the
   user the draft and the path.

6. **Flag domain gaps** — if the ticket needed a term/persona/rule missing from `domain/`, tell the
   user and offer to add it to the relevant `domain/` file.

7. Remind the user they can send it through the pipeline with `/build-ticket` (after they're happy with
   it — the ticket is theirs to approve).

For non-interactive/delegated drafting, the `ticket-writer` agent does the same job in one shot
(surfacing questions instead of asking them live).
