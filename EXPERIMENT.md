# Experiment: Use Cases vs User Stories as pipeline input

## Hypothesis
The *input format* to the delivery pipeline changes the outcome. The story-driven run (branch
`milestones/movies-1`) fed the pipeline **user-story tickets** (`tickets/`) that — in this repo — often
pre-decided much of the solution (endpoints, envelopes, HAL, status codes, NFRs). This experiment feeds
**business use cases** instead: behaviour described end-to-end at the business level, with *no*
implementation, leaving every technical decision to the architect.

> *"User Stories describe intent. Use Cases describe behaviour."* —
> [martinelli.ch: Use Cases vs User Stories](https://martinelli.ch/use-cases-vs-user-stories-same-content-different-outcome/)

## What changes vs the story-driven run
Only the **input** changes. Everything else is held identical so the comparison is fair:

| | Story-driven (`milestones/movies-1`) | Use-case-driven (`experiment/use-cases`) |
|---|---|---|
| Input | User-story tickets (`tickets/`) | Business use cases (`use-cases/`) |
| Authoring | `ticket-writer` / `/write-ticket` | `use-case-writer` / `/write-use-case` |
| Pipeline | architect → spec-reviewer → junior → QA → senior-dev, two gates | **same, unchanged** |
| Stack / standards / domain | Java 25, Spring, Clean Arch, OpenSpec, `standards/`, `domain/` | **same, unchanged** |

The use case is deliberately thinner on *how* and richer on *behaviour* (main flow, alternative flows as
first-class citizens, centralized business rules). The interesting question: does handing the architect a
business-level use case — rather than a story that pre-made decisions — yield a different (better? worse?
more consistent?) implementation.

## Setup on this branch
- **`use-cases/`** + **`use-cases/TEMPLATE.md`** — the business-level use-case format (Actors, Goal,
  Pre/Postconditions, Main flow, Alternative/exception flows, Business rules).
- **`.claude/agents/use-case-writer.md`** + **`.claude/commands/write-use-case.md`** — author use cases.
- The catalog product code is **reset to the walking-skeleton foundation** (platform kept: codegen reuse,
  HAL, H2 default, Swagger UI). The catalog features are then **rebuilt one at a time from use cases**.
- The original story tickets remain in `tickets/` as the comparison reference.

## How to run
1. Author a use case: `/write-use-case <idea>` (or the `use-case-writer` agent).
2. Send it through the unchanged pipeline: `/build-ticket use-cases/UC-<n>-<slug>.md`.
3. Approve the two gates as usual; merge/archive.
4. Repeat per feature, one at a time.

## How to compare
For each catalog capability, compare this branch against `milestones/movies-1`:
- **Behaviour/contract** — same endpoints, envelopes, HAL, status codes? Any divergence traceable to the
  input format?
- **Code** — `git diff milestones/movies-1 experiment/use-cases -- src/main/java/com/acme/catalog` (and
  tests, specs). Structure, naming, decisions the architect made unprompted.
- **Process** — how many gate revisions / spec-review findings / code-review fixes did each input format
  incur? (See observability: `logs/` hooks report and the OTel/Grafana dashboards.)
- **Fidelity** — did the use case's alternative/exception flows produce more complete edge/failure
  coverage than the story's acceptance criteria?

## Status
- [ ] Catalog reset to foundation, build green
- [ ] UC-per-capability authored and rebuilt (movie detail, movie search, credits, person detail,
      filmography, person search)
- [ ] Comparison written up
