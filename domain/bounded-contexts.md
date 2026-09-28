# Bounded contexts (subdomains / capabilities)

Each context owns its own language and rules. A ticket should name the context it belongs to; new
capabilities become `openspec/specs/<context>/<capability>`.

> **Delivery status** (done / next / planned) is tracked in [ROADMAP.md](../ROADMAP.md), not here.
> This file defines each context's language, intended design, and dependencies; where a capability
> is not yet built its notes describe the **target** design, marked *(planned)*.

## `platform`
Cross-cutting foundation every other context builds on (not a business domain). Built from
[UC-000](../use-cases/UC-000-get-started-with-the-catalog-service.md) as four capabilities under
`openspec/specs/platform/`:
- **availability-check**: consumers can confirm the service is available (public, harmless, works on
  an empty catalog).
- **interface-description**: a published description of every capability, both browsable (with
  try-it-out) and tool-readable. It always agrees with behaviour, and shared concepts are defined once.
- **uniform-responses**: the uniform success/failure form, correlation id, self-identifying
  navigable links, and the read-only refusal of writes that every `catalog` capability inherits.
- **runtime-modes**: standalone mode (no external infrastructure, data not retained) and persistent
  mode (for real operation) behave identically for every UC-000 behaviour.

## `catalog` (the product)
The movie database and its public read API. All consumer-facing capabilities live here.
- **movies** *(built — CAT-001, CAT-002)* — the Movie aggregate: retrieve movie detail by id (CAT-001)
  and search/browse movies by title with filters (genre, year, rating), pagination and sorting
  (CAT-002). Both delivered.
- **credits** *(planned — next; UC-003 / CAT-003)* — a Movie's cast and crew, reached from the
  Movie, introducing Person/Credit modelling. Split from **people** below: this capability serves
  credits *from the Movie side* (a movie's cast and crew, each Person shown alongside by identifier
  and name). Target design: once **people** exists, each Person shown on a Credit points to that
  Person's detail; until then the Person is named only — see UC-003 open questions.
- **people** *(planned — UC-004…UC-006 / CAT-004…CAT-006)* — a Person that can be looked up on their
  own by identifier (identifier and name, pointing to their filmography), a Person's **filmography**
  (the movies they are credited in, from the Person side — the inverse of **credits**' movie-side
  view), and a people search/list (filtered by name, paged, ordered by name, A–Z by default — the
  person-side analogue of movie search). Depends on **credits** (Person/Credit modelling).
- **genres-keywords** *(planned)* — browse/list genres and keywords; filter movies by them.
- **ratings-reviews** *(planned)* — a movie's aggregate rating and its curated reviews (read-only).

All `catalog` capabilities are **read-only** and **public** (no auth); see business-rules.md.

## Out of scope for this system
- **identity / auth** — there are no user accounts; the catalog is open to read. (Any future *admin* surface
  for curation would introduce auth, but that is not part of this product.)
- **curation / ingestion** — data is created and maintained out-of-band; not modeled here.

## Context relationships
> These describe the **intended design and dependencies** across capabilities. Relationships that
> involve a *(planned)* capability (credits, people, filmography) are the target once those are built,
> not current behaviour — see [ROADMAP.md](../ROADMAP.md) for what exists today.

- `catalog` depends on `platform` (uniform results and failures, correlation id, navigable results,
  interface description).
- Within `catalog`: **search** and **movies** reference **genres-keywords** (filtering) and surface
  **ratings-reviews** on movie detail. Movie detail **will point to** a Movie's **credits** once that
  capability exists, rather than including cast and crew itself (CAT-003). One answer may draw on
  several of these.
- `credits` depends on `movies`: a Movie must exist for its credits to be presented — asking for the
  credits of an unknown Movie is "no such movie" too. Presenting movie detail never depends on the
  credits themselves; the pointer to them follows from the Movie's identifier alone.
- `people`'s filmography (CAT-005) draws on the same credit and movie information as `credits`, from
  the Person side. A Person must exist for their filmography to be presented (an unknown Person is
  "no such person" too), but presenting person detail never depends on the filmography itself — its
  pointer follows from the Person's identifier alone, mirroring the movies/credits relationship above.
- TODO: if the catalog grows, decide whether people/ratings become their own contexts vs. sub-areas of `catalog`.
