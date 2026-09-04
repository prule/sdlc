# CAT-003: Expose a movie's credits (cast & crew)

**Type:** Feature
**Bounded context:** catalog (new capability: **credits** — endpoint is a `movies` sub-resource) — domain/bounded-contexts.md
**Status:** Ready (open questions resolved at Gate 1; see Decisions)

## User story
As an **API consumer (developer)** (domain/actors-and-personas.md), I want to retrieve a **Movie**'s
**credits** — its **cast** (acting **Credits**) and **crew** (non-acting **Credits**) — so that I can
show who acted in and made a movie (roles, characters, departments/jobs) in my own app without running
my own catalog.

## Background & domain context
The natural follow-on to CAT-001 (movie detail) and CAT-002 (search). CAT-001 **deliberately deferred
credits** — it emits only a `self` link and explicitly promised to add a navigational `credits` `_link`
"once a people/credits endpoint exists" (CAT-001 Decisions; catalog/movies spec, "Movie detail carries
only a self link"). This ticket delivers that endpoint and closes the loop.

This is the **first real Person/Credit modelling** in the system — CAT-001/CAT-002 deliberately omitted
it. The domain terms are already agreed (domain/glossary.md): a **Person** is an individual who worked
on movies (actor, director, writer, …); a **Credit** links a Person to a Movie in a specific capacity;
**Cast** is the set of acting Credits (with character name and billing order); **Crew** is the set of
non-acting Credits (director, writer, composer, …), grouped by department/job. Everything stays
**public** and **read-only** (domain/business-rules.md).

A movie's credits are a **collection**, so the response follows the HAL collection convention exactly as
CAT-002's search did (standards/openapi.md §2/§2a; openspec/specs/platform/hypermedia-links) — items in
`data._embedded.<rel>`, a `self` link on the collection and on each item where a target exists, counts
in `meta.pagination` **if** paginated — reusing the shared Envelope/Meta/Problem/Link components and the
contract-first bundle→generate pipeline (openspec/specs/platform/api-codegen) with **no** per-operation
`<Operation><Status>Response*` codegen duplicates.

**Representation principle (carried from CAT-001):** emit navigational `_links` **only to related
resources that already exist**. Whether each Credit's **Person** gets a resolvable `self` link therefore
depends on whether a `/people/{id}` endpoint is in scope (see Assumptions + Open questions) — we will not
emit a dangling person link.

## Decisions (resolved with the author at Gate 1 — requirements, not open questions)
- **D1 — Smallest slice: one endpoint.** This ticket delivers **only** `GET /api/v1/movies/{id}/credits`
  (a movie's cast + crew as a sub-resource). A standalone `GET /api/v1/people/{id}` (person detail) is
  **deferred** to a later `catalog/people` ticket.
- **D2 — Person is not addressable yet.** Each Credit exposes its Person **inline** (Person `id` +
  `name`) and carries **no** person `self` link — honouring CAT-001's "only link to resources that exist"
  rule (no dangling links). A person link is added when `catalog/people` ships.
- **D3 — Credits returned whole (not paginated).** The full credits collection is returned in one
  response (no `page`/`size`, no `meta.pagination`).
- **D4 — Credit detail (full).** Cast items expose Person (`id`+`name`), **character** name, and
  **billing order**; crew items expose Person (`id`+`name`), **department**, and **job**.
- **D5 — Field semantics.** `billingOrder` is a positive integer, **ascending = top billing** (lead
  first). `character`, `department`, and `job` are **free-text** strings for this slice (no controlled
  vocabulary yet — may be formalised later). Credits are **ordered** (see D6), stable across requests.
- **D6 — Ordering.** Cast ordered by `billingOrder` ascending; crew grouped by `department` then `job`,
  with a deterministic terminal tiebreak (person name, then a unique key) so paging/ordering is total and
  stable — mirror the CAT-002 determinism lesson.
- **D7 — New `catalog/credits` capability.** Its own spec under `openspec/specs/catalog/credits`;
  the endpoint is a `movies` sub-resource. `catalog/people` is added later when person detail ships.
- **D8 — This ticket adds the `credits` `_link` to movie detail.** `GET /api/v1/movies/{id}` gains a
  navigational `credits` link pointing at the new endpoint; CAT-001's otherwise `self`-only behaviour
  still holds (this updates the catalog/movies "Movie detail carries only a self link" requirement).

## Acceptance criteria
- [ ] **Happy path — a movie's credits return an enveloped HAL collection.** Given a Movie with cast and
      crew, when a client sends `GET /api/v1/movies/{id}/credits` with a matching valid UUID and no
      `Authorization` header, then `200`, `Content-Type: application/json`, the standard **Envelope**
      (`{data, meta}`); `data._embedded.<rel>` is an array of Credit items; `data._links.self.href` is
      the absolute URI of this operation; `meta` carries `timestamp` and a `correlationId` (UUID).
      (standards/openapi.md §2/§2a; hypermedia-links spec)
- [ ] **Cast item shape.** Each acting Credit exposes its **Person** (`id` + `name`), the **character
      name**, and **billing order**; and (per A2) carries **no** person `self` link.
- [ ] **Crew item shape.** Each non-acting Credit exposes its **Person** (`id` + `name`), the
      **department**, and the **job**; no person `self` link.
- [ ] **Ordering.** Cast is ordered by billing order ascending; crew is grouped by department then job
      with a deterministic tiebreak — ordering is stable across repeated requests.
- [ ] **Edge — movie with no credits is a normal 200.** Given an existing Movie with no cast or crew
      recorded, then `200` with an **empty** `data._embedded.<rel>` and `data._links.self` present —
      **not** `404`, not an error. (aligns with CAT-002's empty-collection convention)
- [ ] **Failure — unknown movie id returns 404 problem+json.** Given no Movie for a syntactically valid
      UUID, then `404`, `Content-Type: application/problem+json` conforming to the shared `Problem`
      schema (stable `code`, `status`, request `correlationId`), **no** `_links`/`_embedded`, and **not**
      an empty `200`. (standards/error-handling.md §3; catalog/movies "Unknown id returns 404")
- [ ] **Failure — malformed movie id is a 400, not a 500.** Given a path `{id}` that is not a valid
      UUID, then `400` `application/problem+json` conforming to `Problem` with the `correlationId`; it
      does not reach persistence and does not fall through to `500`. (error-handling.md §3)
- [ ] **HAL discipline.** No `_templates`, no action/write affordances; errors never carry
      `_links`/`_embedded`; success is `application/json` (HAL fields inside `data`), errors are
      `application/problem+json`. (standards/openapi.md §2a; hypermedia-links spec)
- [ ] **Public access.** Served with `security: []`; never returns `401`/`403` for a missing/absent
      `Authorization` header. (domain/business-rules.md, "Access & security posture")
- [ ] **Movie detail gains a `credits` link (per A6).** After this change, `GET /api/v1/movies/{id}`
      emits `data._links.credits.href` addressing this endpoint; the CAT-001 `self`-only behaviour
      otherwise still holds. (updates catalog/movies "Movie detail carries only a self link")
- [ ] **No N+1 loading credits.** Loading a movie's credits (each joining to a Person) issues a
      **bounded** number of SQL statements independent of the number of credits — not one Person query
      per row. A Testcontainers test/assertion guards the query count. (CAT-002 pattern; testing.md)
- [ ] **Demo data + independent tests.** The `@Profile("demo")` seed gains people + credits so the happy
      path is demonstrable in a running app; automated tests use their **own** fixtures (Testcontainers,
      real Postgres) and pass regardless of the demo seed's contents. (CAT-001 pattern)

## Non-goals
- **Standalone `/people/{id}` (person detail)** — deferred to a later `catalog/people` ticket (A1). No
  person `self` link is emitted here (A2).
- **Cross-filmography** ("all movies a person is credited in") — needs `/people`; out of scope.
- **Search/filtering within credits** (e.g. by department) — the full credits collection only.
- **Writes** to catalog data; the out-of-band curation/ingestion pipeline.
- **Rate-limiting** design (product-level; domain/business-rules.md).
- **Ratings/reviews, genres/keywords** endpoints — separate capabilities.

## NFRs / constraints (cite standards)
- **Contract-first:** the operation, the **Credit** (cast/crew) `data` item schemas, the HAL collection
  `_links`/`_embedded` shape, and any params are authored in the split OpenAPI 3.1 spec **before**
  controller code; the controller implements the generated interface; no hand-written DTOs
  (standards/openapi.md §1/§4/§5; api-codegen spec).
- **Reuse (no platform drift):** `$ref` the shared `Envelope`/`Meta`/`Problem`/`Link` schemas and the
  reusable `404`/`400` responses; codegen guarantees stay intact — no per-operation
  `<Operation><Status>Response*` duplicates (`GeneratedApiCodegenTest` stays green).
- **HAL representation:** Envelope `{data, meta}` as `application/json`, HAL `_links`/`_embedded` inside
  `data`; errors `application/problem+json`, never HAL. Link/collection assembly is **web-adapter-only**
  (Spring HATEOAS `WebMvcLinkBuilder` populating generated `_links` DTO fields); domain/application never
  import Spring HATEOAS nor reference `_links`/`_embedded`. (standards/openapi.md §2a)
- **Security:** public `security: []` — the documented divergence from standards/security.md for the
  public read surface (domain/business-rules.md). People data is public professional/biographical info
  (domain/business-rules.md, "Privacy / compliance"); no PII beyond that, no secrets returned.
- **Errors:** unknown movie → `404`, malformed UUID → `400`, both via the single global
  `@RestControllerAdvice`; `correlationId` on every response; no SQL/stack-trace leak
  (standards/error-handling.md §2/§3/§4).
- **Clean architecture:** introduce **Person** and **Credit** as domain model (no Spring/JPA imports) —
  the first Person/Credit aggregate(s); an application **use case** (get a movie's credits) + an
  **outbound port**; a persistence adapter mapping JPA entities ↔ domain over **Postgres via Flyway**
  (new migration; never edit an applied one). Architect owns aggregate boundaries / schema / query shape.
  (standards/clean-architecture.md)
- **Performance — bounded queries (no N+1):** loading a movie's credits joins each Credit to a Person;
  apply the CAT-002 avoidance pattern (id-page-then-fetch / fetch-join / `@EntityGraph` — architect's
  choice) so the statement count stays bounded and independent of credit count, with a query-count guard
  test. The CAT-001 detail path and CAT-002 search path must not regress. (see
  `MovieSearchPersistenceAdapter`; testing.md)
- **Testing:** happy/edge/failure per requirement; domain/application as fast unit tests (port mocked);
  web layer via `@WebMvcTest`/MockMvc (HAL collection shape, cast/crew item shapes, empty-collection
  `200`, `404`/`400`); persistence adapter against **real Postgres via Testcontainers (no H2)** with its
  own fixtures independent of the demo seed, including the **N+1 query-count guard** and ordering
  correctness. (standards/testing.md)

## Dependencies
- **CAT-001 / `catalog/movies`** — the Movie aggregate, the `movies` schema, and the
  `GET /api/v1/movies/{id}` route the `credits` link and the sub-resource path hang off.
- **hypermedia-links / PLAT-003** — the HAL collection convention. **api-codegen** — the contract
  pipeline. **CAT-002** — the reference N+1-avoidance and empty-collection patterns.
- **First persisted people + credits data** — a Flyway schema for people/credits + join to movies, a
  demo seed, and test fixtures.

## Open questions (residual — minor, for the architect / Gate 1)
- **`credits` empty-collection convention.** Align the empty-credits `200` shape (which `_links`,
  `meta` with no pagination) with the existing platform collection precedent — architect confirms
  consistency (no new convention invented).
- **Carried, not blocking:** rating vote count (CAT-001 residual) and rate-limiting design remain open
  product decisions, untouched here.

## Domain gaps (to reconcile in `domain/` as part of this work)
- **Credit / Cast / Crew fields underspecified.** glossary.md names Person/Credit/Cast/Crew but does not
  define **billing order** (what it means, ordering direction), the **department/job** vocabulary for
  crew (controlled list vs free text), or whether **credits are ordered**. Add precise definitions once
  A4/A5 are confirmed.
- **`credits` as a HAL relation name.** Record `credits` as the agreed link/embedded relation name so
  movie detail and this collection stay consistent (glossary/business-rules API-behaviour section).
- **Person addressability rule.** business-rules.md should state whether Person is addressable and
  whether a person link is emitted on a credit (mirrors the CAT-001 "only link to resources that exist"
  rule) — record once A2 is decided.
- **Bounded-contexts note.** bounded-contexts.md lists `people` (Person detail + their credits) as a
  single planned capability; if credits ship before person detail, update it to reflect the split
  (`catalog/credits` now, `catalog/people` later) once placement is confirmed.
- **Movie-detail representation update.** The catalog/movies spec's "Movie detail carries only a self
  link and no embedded resources" requirement must be updated to allow the `credits` `_link` (per A6).
