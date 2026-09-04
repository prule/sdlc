# Bounded contexts (subdomains / capabilities)

Each context owns its own language and rules. A ticket should name the context it belongs to; new
capabilities become `openspec/specs/<context>/<capability>`.

## `platform`
Cross-cutting technical foundation every other context builds on (not a business domain).
- **health-check** — liveness/ping endpoint. (`openspec/specs/platform/health-check`)
- **api-codegen** — contract-first OpenAPI bundle→generate pipeline. (`openspec/specs/platform/api-codegen`)

## `catalog` (the product)
The movie database and its public read API. All consumer-facing capabilities live here.
- **movies** — the Movie aggregate; retrieve movie detail by id (CAT-001), search movies (CAT-002).
- **search** *(planned)* — search movies by title with filters (genre, year, rating), pagination, sorting.
- **credits** — a Movie's cast and crew as a sub-resource (`GET /movies/{id}/credits`), introducing
  Person/Credit modelling (CAT-003). Split from **people** below: this capability serves credits
  *from the Movie side* (a movie's cast/crew, Person exposed inline, now carrying a resolvable
  `person._links.self` since CAT-004); **people** serves a Person as its own addressable resource.
- **people** — Person as an independently addressable resource (`GET /people/{id}`: id + name +
  `self` link) since CAT-004. Person collection/search-list and cross-filmography credits remain
  *planned* — not yet added.
- **genres-keywords** *(planned)* — browse/list genres and keywords; filter movies by them.
- **ratings-reviews** *(planned)* — a movie's aggregate rating and its curated reviews (read-only).

All `catalog` capabilities are **read-only** and **public** (no auth); see business-rules.md.

## Out of scope for this system
- **identity / auth** — there are no user accounts; the read API is open. (Any future *admin* surface
  for curation would introduce auth, but that is not part of this product.)
- **curation / ingestion** — data is created and maintained out-of-band; not modeled here.

## Context relationships
- `catalog` depends on `platform` (envelope, error handling, correlation id, OpenAPI pipeline).
- Within `catalog`: **search** and **movies** reference **genres-keywords** (filtering) and surface
  **ratings-reviews** on movie detail, and now **credits** via a navigational `credits` link (not
  inlined/embedded — CAT-003) rather than composing cast/crew directly into movie detail. A single
  read may compose across these.
- `credits` depends on `movies` (a movie must exist for its credits to be loaded — an unknown movie
  id is a 404 on the credits endpoint too) but movie detail never queries `credits` at read time; the
  link is assembled from the id alone, at zero extra DB cost.
- TODO: if the catalog grows, decide whether people/ratings become their own contexts vs. sub-areas of `catalog`.
