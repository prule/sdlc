# Use-case template

Copy the block into `use-cases/UC-<n>-<slug>.md`. Delete the guidance comments.

**A use case describes _behaviour_, not intent, and never _how_.** Stay at the business level: the actor
interacts with **"the system"** in **domain language** (see [domain/](../domain/)). Do **not** name HTTP
methods, endpoints, URLs, status codes, JSON/response shapes, HAL, databases, tables, frameworks,
classes, or packages — every one of those is the architect's decision downstream. If you catch yourself
writing *how*, restate it as observable business behaviour or move it to a Business Rule.

> Contrast with a user story (`tickets/`): a story states *intent* ("As a … I want … so that …") and, in
> this repo, tended to pre-decide a lot of the solution. A use case makes the **whole behaviour explicit
> and end-to-end** — main flow, alternatives as first-class citizens, and centralized business rules —
> and leaves the solution entirely to the architect.

---

```markdown
# UC-<n>: <concise goal-oriented title, e.g. "Retrieve a movie's details">

**Primary actor:** <the actor who wants the goal — from domain/actors-and-personas.md>
**Secondary actors:** <other systems/actors the system relies on to complete the goal, if any; else "None">
**Goal:** <the outcome the primary actor wants, in one business sentence>
**Scope:** <the system/bounded context under design — e.g. the movie catalog>
**Level:** <User-goal | Subfunction — keep these at user-goal level>
**Status:** Draft

## Preconditions
<What must already be true before the flow can start. Business state only
(e.g. "the catalog contains at least one movie"), never technical setup.>

## Postconditions
- **Success:** <what is true when the goal is achieved — what the actor now knows/has.>
- **Failure:** <what is true if the goal cannot be achieved — the actor is informed, nothing changes.>

## Main flow (basic course of events)
<Numbered, plain-language, alternating actor↔system steps that reach the success postcondition.
Each step is an observable business interaction, not a technical operation.>
1. The <actor> asks the system for <…>.
2. The system <does business step / applies [BR-n]>.
3. The system presents <the business result> to the <actor>.

## Alternative & exception flows
<First-class. Each is anchored to a main-flow step number and states the condition and the outcome.
Cover the edge and failure courses the story's "edge/failure" criteria would have.>
- **2a. <condition>:** <what the system does instead; where the flow resumes or ends.>
- **3a. <condition>:** <…>

## Business rules
<Centralized and referenced by id from the flows above. Business policy only — thresholds, validity,
ordering, visibility — expressed in domain terms, not implementation.>
- **BR-1:** <rule>
- **BR-2:** <rule>

## Non-goals
- <behaviour explicitly outside this use case, to prevent scope creep>

## Open questions (need a human decision before/at Gate 1)
- <genuine unknowns / policy calls — surface, don't guess or bury as settled>
```
