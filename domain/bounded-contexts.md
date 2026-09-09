# Bounded contexts (subdomains / capabilities)

Each context owns its own language and rules. A ticket should name the context it belongs to; new
capabilities become `openspec/specs/<context>/<capability>`.

> **Delivery status** (done / next / planned) is tracked in [ROADMAP.md](../ROADMAP.md), not here.
> This file defines each context's language, intended design, and dependencies; where a capability
> is not yet built its notes describe the **target** design, marked *(planned)*.

## `platform`
Cross-cutting technical foundation every other context builds on (not a business domain).
- **health-check** — liveness/ping endpoint. (`openspec/specs/platform/health-check`)
- **api-codegen** — contract-first OpenAPI bundle→generate pipeline. (`openspec/specs/platform/api-codegen`)

## `catalog` (the product)
The movie database and its public read API. All consumer-facing capabilities live here.
- **movies** *(built — CAT-001, CAT-002)* — the Movie aggregate: retrieve movie detail by id (CAT-001)
  and search/browse movies by title with filters (genre, year, rating), pagination and sorting
  (CAT-002). Both delivered.
- **credits** *(planned — next; UC-003 / CAT-003)* — a Movie's cast and crew as a sub-resource
  (`GET /movies/{id}/credits`), introducing Person/Credit modelling. Split from **people** below: this
  capability serves credits *from the Movie side* (a movie's cast/crew, Person exposed inline). Target
  design: once **people** exists, the inline Person carries a resolvable `person._links.self`; until
  then the Person is named-only (id + name) — see UC-003 open questions.
- **people** *(planned — UC-004…UC-006 / CAT-004…CAT-006)* — Person as an independently addressable
  resource (`GET /people/{id}`: id + name + `self`/`credits` links), later carrying a Person's
  **filmography** — the movies they are credited in, from the Person side (`GET /people/{id}/credits`),
  the inverse of **credits**' movie-side view — and a person collection/search-list (`GET /people`:
  name-filtered, paginated, sorted by name, default name ascending — the person-side analogue of movie
  search). Depends on **credits** (Person/Credit modelling).
- **genres-keywords** *(planned)* — browse/list genres and keywords; filter movies by them.
- **ratings-reviews** *(planned)* — a movie's aggregate rating and its curated reviews (read-only).

All `catalog` capabilities are **read-only** and **public** (no auth); see business-rules.md.

## Out of scope for this system
- **identity / auth** — there are no user accounts; the read API is open. (Any future *admin* surface
  for curation would introduce auth, but that is not part of this product.)
- **curation / ingestion** — data is created and maintained out-of-band; not modeled here.

## Context relationships
> These describe the **intended design and dependencies** across capabilities. Relationships that
> involve a *(planned)* capability (credits, people, filmography) are the target once those are built,
> not current behaviour — see [ROADMAP.md](../ROADMAP.md) for what exists today.

- `catalog` depends on `platform` (envelope, error handling, correlation id, OpenAPI pipeline).
- Within `catalog`: **search** and **movies** reference **genres-keywords** (filtering) and surface
  **ratings-reviews** on movie detail, and now **credits** via a navigational `credits` link (not
  inlined/embedded — CAT-003) rather than composing cast/crew directly into movie detail. A single
  read may compose across these.
- `credits` depends on `movies` (a movie must exist for its credits to be loaded — an unknown movie
  id is a 404 on the credits endpoint too) but movie detail never queries `credits` at read time; the
  link is assembled from the id alone, at zero extra DB cost.
- `people`'s filmography (CAT-005) reads the same `credits`/`movies`(+genres) tables as `credits`,
  from the Person side; a Person must exist for their filmography to be loaded (an unknown person id
  is a 404 there too), but person detail never queries the filmography at read time — its `credits`
  link is assembled from the id alone, mirroring the movies/credits relationship above.
- TODO: if the catalog grows, decide whether people/ratings become their own contexts vs. sub-areas of `catalog`.
