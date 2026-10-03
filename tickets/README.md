# Tickets

Human-authored requirements — the **input** to the delivery pipeline. A ticket says **what** and
**why**; the pipeline's architect decides **how**. Write the ticket first, then feed it to
`/build-ticket` (or the individual agents).

## Two kinds
- **Feature** (business requirement) — a user-facing capability. Framed as a user story + acceptance
  criteria + domain context.
- **Technical** (enabler / chore) — infrastructure, refactors, tooling, tech-debt. Framed as
  problem/rationale + scope + constraints.

Both use [TEMPLATE.md](TEMPLATE.md).

## Workflow
1. Draft with the **ticket-writer** agent or the `/write-ticket` command — both read `standards/` and
   `domain/` so the ticket uses the right language, personas, contexts, and NFRs.
2. Review/edit the draft (it's yours — the ticket-writer captures requirements, it doesn't invent scope).
3. Run it through the pipeline: `/build-ticket <paste the ticket>` (or `Use the architect agent …`).

## Conventions
- File name: `<AREA>-<n>-<slug>.md` (e.g. `AUTH-142-password-reset.md`, `PLAT-003-…`).
- Keep the ticket at the requirements altitude — no detailed design, no code. Flag unknowns in
  **Open questions** rather than guessing.
- When a ticket introduces new domain language/rules, add them to `domain/` as part of the work.
