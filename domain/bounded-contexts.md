# Bounded contexts (subdomains / capabilities)

Each context owns its own language and rules. A ticket should name the context it belongs to; new
capabilities become `openspec/specs/<context>/<capability>`.

## `platform`
Cross-cutting technical foundation every other context builds on (not a business domain).
- **health-check** — liveness/ping endpoint. (`openspec/specs/platform/health-check`)
- **api-codegen** — contract-first OpenAPI bundle→generate pipeline. (`openspec/specs/platform/api-codegen`)

## `catalog` (the product)
The movie database and its public read API. All consumer-facing capabilities live here.
- **movies** *(planned)* — the Movie aggregate; retrieve movie detail by id.
- **search** *(planned)* — search movies by title with filters (genre, year, rating), pagination, sorting.
- **people** *(planned)* — Person detail and their credits (cast/crew).
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
  **ratings-reviews** and **people** (credits) on movie detail. A single read may compose across these.
- TODO: if the catalog grows, decide whether people/ratings become their own contexts vs. sub-areas of `catalog`.
