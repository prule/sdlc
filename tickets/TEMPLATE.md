# Ticket templates

Copy the relevant block into `tickets/<AREA>-<n>-<slug>.md`. Delete guidance comments. Keep it at the
requirements altitude — **what & why**, not **how**.

---

## Feature ticket (business requirement)

```markdown
# <AREA>-<n>: <concise title>

**Type:** Feature
**Bounded context:** <e.g. identity> (see domain/bounded-contexts.md)
**Status:** Draft

## User story
As a <persona from domain/actors-and-personas.md>, I want <capability>, so that <business value>.

## Background & domain context
<Why now; the domain terms involved (link domain/glossary.md); which business rules apply
(domain/business-rules.md). Enough for the architect to understand the intent without guessing.>

## Acceptance criteria
- [ ] <Given/When/Then, or a clear observable behaviour>
- [ ] <edge case>
- [ ] <failure case>
<Each criterion must be independently testable.>

## Non-goals
- <explicitly out of scope, to prevent scope creep>

## NFRs / constraints (cite standards where relevant)
- Security: <authz, ownership/tenant, PII — standards/security.md>
- Errors: <expected failure responses — standards/error-handling.md>
- Other: <performance, rate limiting, audit, etc.>

## Dependencies
- <other tickets/capabilities this needs; external systems>

## Open questions (need a human decision before/at Gate 1)
- <unknowns; do not guess — surface them>
```

---

## Technical ticket (enabler / chore)

```markdown
# <AREA>-<n>: <concise title>

**Type:** Technical
**Area:** <e.g. build, platform, ci, refactor>
**Status:** Draft

## Problem / rationale
<What's wrong or missing, and why it matters now. The trigger (e.g. a code-review finding).>

## Scope
- <what will change, at a high level — not a design>

## Out of scope
- <what this deliberately does not touch>

## Constraints (standards that apply)
- <e.g. standards/clean-architecture.md, standards/testing.md — what must still hold>

## Acceptance / done criteria
- [ ] <observable, verifiable outcome, e.g. "build green with X", "no Y in generated output">

## Risks
- <what could go wrong; blast radius>

## Open questions
- <decisions needed from a human>
```
