# CAT-005: Retrieve a person's filmography (the movies they are credited in)

**Type:** Feature
**Bounded context:** catalog (existing capability: **people** — second endpoint, a `people` sub-resource `GET /api/v1/people/{id}/credits`) — domain/bounded-contexts.md
**Status:** Ready (open questions resolved by the author; see Decisions)

## User story
As an **API consumer (developer)** (domain/actors-and-personas.md), I want to retrieve the **filmography**
of a single **Person** — the **Movies** they are credited in and in what capacity — so that I can show
"what this person has worked on" in my own app and navigate from a Person to their body of work, without
running my own catalog.

## Background & domain context
The direct follow-on to CAT-004 (person detail). CAT-004 made a **Person** independently addressable at
`GET /api/v1/people/{id}` (id + name + `self` link) but shipped **`_links.self` only** and **deliberately
deferred** any `credits`/`filmography` link because "no cross-filmography endpoint exists to point at"
(CAT-004 D6; domain/glossary.md "HAL relation naming (CAT-004)"; `openspec/specs/catalog/people/spec.md`
"HAL discipline on person detail"). This ticket creates that endpoint — the set of Movies a Person is
credited in, **from the Person side** — and then person detail can finally carry the reciprocal link,
exactly as CAT-003 added the movie-detail `credits` link when it shipped a movie's credits, and CAT-004
added the inline-Person `self` link on credits when it shipped person detail. Each ticket closes the
previous one's deferral in the same change.

This is the **inverse** of `catalog/credits` (`GET /api/v1/movies/{id}/credits`, CAT-003), which lists the
**people on one movie**; here we list the **movies for one person**. The domain terms are already agreed
(domain/glossary.md): a **Credit** links a **Person** to a **Movie** in a specific capacity — an acting
credit (**Cast**: `character` + `billingOrder`) or a non-acting credit (**Crew**: `department` + `job`);
each Movie in the result is exposed as a **movie summary** (the CAT-002 shape: `id`, `title`,
`releaseYear`, `genres`, `runtimeMinutes`/`rating` when present, plus its `self` link — no synopsis). No
new domain concept is introduced; this is a new *view* over the existing Person/Credit/Movie model.
Everything stays **public** and **read-only** (domain/business-rules.md).

This is a **collection** sub-resource under a Person, so the response follows the HAL collection
convention (standards/openapi.md §2/§2a; openspec/specs/platform/hypermedia-links) — items in
`data._embedded.<rel>`, a `self` link on the collection and each item where a target exists, reusing the
shared Envelope/Meta/Problem/Link components and the contract-first bundle→generate pipeline
(openspec/specs/platform/api-codegen) with **no** per-operation `<Operation><Status>Response*` codegen
duplicates. The shape/scope calls have been resolved with the author (see Decisions D8–D12): a **single
paginated `filmography` list**, one item per (movie, capacity), ordered newest-first, with person detail
gaining a reciprocal `_links.credits` in this same change.

## Decisions (settled by precedent — requirements, not open questions)
- **D1 — Second endpoint of the existing `catalog/people` capability** *(settled by
  bounded-contexts.md, which lists cross-filmography under `people` as planned)*. `GET /api/v1/people/{id}/credits`
  is authored into the existing `openspec/specs/catalog/people/spec.md`; it is a `people` **sub-resource**,
  analogous to how `GET /api/v1/movies/{id}/credits` is a `movies` sub-resource under `catalog/credits`.
- **D2 — Stable opaque UUID** *(settled by domain/business-rules.md "Catalog data integrity")*. `{id}` is
  the Person's stable opaque UUID — the same id already surfaced on person detail (CAT-004) and inline on
  credits (CAT-003). Internal DB ids are never exposed.
- **D3 — Public, read-only** *(settled by domain/business-rules.md)*. Served `security: []`; never mutates;
  the path pattern is registered in the single source of truth for public endpoints so the public-endpoint
  consistency test holds.
- **D4 — Movies exposed as movie summaries** *(settled by domain/glossary.md "Movie summary" + CAT-002)*.
  Each result ties a **Movie** to the person's **capacity** on it; the Movie is rendered as the existing
  **movie summary** (reuse the CAT-002 schema and its `_links.self` to `GET /api/v1/movies/{id}`), not a
  bespoke shape — no synopsis, `runtimeMinutes`/`rating` omitted when absent. The exact *combined* item
  shape (how capacity attaches to the summary) is Q1.
- **D5 — Existing person with no credits is a normal 200 (empty), not 404** *(settled by CAT-003's
  empty-credits rule + domain/business-rules.md "empty result set is a normal 200")*. An addressable Person
  who is credited in no Movie returns `200` with empty collection(s), never `404`.
- **D6 — Unknown / malformed id failure modes reuse the existing taxonomy** *(settled by CAT-004 + the
  error taxonomy)*. Unknown person id → `404` `application/problem+json` with the **existing**
  `PERSON_NOT_FOUND` code (reuse `ResourceNotFoundException`; do NOT invent a new code — CAT-004 D8).
  Malformed (non-UUID) `{id}` → `400` `application/problem+json`, not `500`, not reaching persistence.
  Public access is never `401`/`403`.
- **D7 — Bounded queries (no N+1)** *(settled by the CAT-002/CAT-003 precedent + standards/testing.md)*.
  Loading a person's filmography — each credit joining to a Movie and that Movie's genres — SHALL issue a
  **bounded** number of SQL statements independent of the number of credits/movies, guarded by a
  query-count test. This is a real N+1 risk here (prolific person → many movies, each with genres).
- **D8 — Single `filmography` list, not split cast/crew** *(resolved with author)*. The collection is
  presented as **one** embedded relation `data._embedded.filmography` (not two `cast`/`crew` relations as
  in movie-credits). The organising axis here is the Movie (a person's body of work), so one list reads
  naturally and carries one ordering.
- **D9 — One item per (movie, capacity)** *(resolved with author)*. Each item ties a **movie summary** to
  exactly **one** capacity. A Person credited on one Movie in several capacities (e.g. actor *and*
  director) yields **several** items — one per capacity — each a clean single (movie, capacity) pair
  matching the flat `credits` rows. The capacity is a **typed object** on the item (an acting capacity
  carries `character` + `billingOrder`; a non-acting capacity carries `department` + `job`) — do not
  flatten both field sets onto the summary.
- **D10 — Ordering: newest-first, total and stable** *(resolved with author)*. `releaseYear` **descending**,
  then `title` **ascending**, then a unique terminal key (credit/movie id) so the order is total and
  identical across repeated requests (mirrors the CAT-003 determinism rule).
- **D11 — Paginated** *(resolved with author)*. The filmography is **paginated**, reusing the CAT-002
  convention: `page` (zero-based) / `size` (default **20**, max **100**) params, `meta.pagination`
  (`page`/`size`/`totalElements`/`totalPages`), and HAL pagination links (`self`/`first`/`last`/`next`/
  `prev`) in `data._links`. The domain rule that a *movie's* credits are returned whole does **not** extend
  here — a prolific person's filmography can be large. Empty/last-page link + `totalPages` conventions
  follow the platform's existing CAT-002 HAL collection precedent (do not invent a new convention).
- **D12 — Person detail gains reciprocal `_links.credits`, in this ticket** *(resolved with author)*. Once
  this endpoint exists, `GET /api/v1/people/{id}` gains `_links.credits` → `GET /api/v1/people/{id}/credits`,
  delivered **in this change** (closing the CAT-004 D6 deferral, mirroring CAT-003/CAT-004). The relation
  name is **`credits`**, consistent with movie detail's `_links.credits` → `/movies/{id}/credits`. This is
  a **MODIFIED** to the `catalog/people` spec — its "HAL discipline on person detail" requirement currently
  mandates **`self` only, no `credits`/`filmography` link**. **Lesson from CAT-003/CAT-004:** the MODIFIED
  requirement header(s) must reproduce the promoted `openspec/specs/catalog/people/spec.md` text
  **verbatim** (copy, do not paraphrase) and preserve scenario names, so the delta applies cleanly at
  archive.

## Acceptance criteria
- [ ] **Happy path — a person's filmography returns an enveloped HAL collection.** Given a Person credited
      in one or more Movies, when a client sends `GET /api/v1/people/{id}/credits` with a matching valid
      UUID and no `Authorization` header, then `200`, `Content-Type: application/json`, the standard
      **Envelope** (`{data, meta}`); the credited Movies appear under `data._embedded.filmography` (D8);
      `data._links` carries `self` and the pagination links (D11); `meta` carries `timestamp`, a
      `correlationId` (UUID), and `pagination` (D11). (standards/openapi.md §2/§2a; hypermedia-links spec)
- [ ] **Item shape — a movie summary plus one typed capacity (D9).** Each item exposes the credited **Movie
      as a movie summary** (`id`, `title`, `releaseYear`, `genres`; `runtimeMinutes`/`rating` when present,
      omitted not `null`; **no** synopsis) carrying its own `_links.self` to `GET /api/v1/movies/{id}`,
      **plus** a typed **capacity** object for the one capacity this item represents — an acting capacity
      carries `character` and `billingOrder`; a non-acting capacity carries `department` and `job`. A Person
      credited on one Movie in several capacities produces **several** items, one per capacity (D9).
- [ ] **Ordering is total and stable (D10).** Filmography items are ordered `releaseYear` descending, then
      `title` ascending, then a unique terminal key (credit/movie id), identical across repeated requests
      (mirroring the CAT-003 determinism rule). Ordering holds across page boundaries (no cross-page skip or
      duplicate, per the CAT-002 lesson).
- [ ] **Edge — existing person with no credits is a normal 200.** Given an addressable Person credited in
      no Movie, then `200` with an **empty** `data._embedded.filmography`, `data._links.self` present, and
      `meta.pagination.totalElements: 0` — **not** `404`, not an error (empty/`totalPages` convention per
      the CAT-002 precedent, D11). (D5; CAT-003 empty-credits rule)
- [ ] **Pagination (D11).** `page`/`size` (zero-based `page`, `size` default 20, max 100) page the
      filmography; first page → `self`/`first`/`last`/`next`, no `prev`; last page → no `next`; a valid page
      beyond the last → empty `200` (no `next`), not 404/400; invalid `page`/`size` (`page<0`, `size<1`,
      `size>100`) → `400` problem+json, not `500`. Counts in `meta.pagination`, link URLs in `data._links`.
- [ ] **Failure — unknown person id returns 404 problem+json.** Given no Person for a syntactically valid
      UUID, then `404`, `Content-Type: application/problem+json` conforming to the shared `Problem` schema
      (stable `code` `PERSON_NOT_FOUND` (D6), `status`, request `correlationId`), **no** `_links`/
      `_embedded`, and **not** an empty `200`. (domain/business-rules.md; standards/error-handling.md §3)
- [ ] **Failure — malformed (non-UUID) id is a 400, not a 500.** Given a path `{id}` that is not a valid
      UUID, then `400` `application/problem+json` conforming to `Problem` with the `correlationId`; it does
      not reach persistence and does not fall through to `500`. (standards/error-handling.md §3)
- [ ] **HAL discipline.** Success is `application/json` with HAL fields inside `data` and **no**
      `_templates` and no action/write affordances; errors are `application/problem+json` and never carry
      `_links`/`_embedded`. (standards/openapi.md §2a; hypermedia-links spec)
- [ ] **Public access.** Served with `security: []`; never returns `401`/`403` for a missing/absent
      `Authorization` header; the new path pattern is registered in the single source of truth for public
      endpoints so the public-endpoint consistency test holds. (domain/business-rules.md "Access & security")
- [ ] **No N+1 loading the filmography.** Loading a person's filmography (each credit joining to a Movie,
      each Movie to its genres) issues a **bounded** number of SQL statements independent of the number of
      credits/movies — not one Movie (or one genre) query per credit row. A Testcontainers test/assertion
      guards the query count and confirms it does not grow as the filmography grows. (D7; CAT-002/CAT-003
      pattern; testing.md)
- [ ] **Person detail gains the reciprocal `_links.credits` (D12 — in this ticket).** After this change,
      `GET /api/v1/people/{id}` emits `data._links.credits.href` addressing `GET /api/v1/people/{id}/credits`;
      the rest of the CAT-004 person-detail shape (exactly `id`, `name`, `_links`) is otherwise unchanged. A
      person-detail web-layer test asserts the new link resolves. (MODIFIES `catalog/people`; the MODIFIED
      requirement header(s) must reproduce the promoted `openspec/specs/catalog/people/spec.md` text
      **verbatim** and preserve scenario names — D12)
- [ ] **Demo data + independent tests.** The `@Profile("demo")` seed already has people + credits (CAT-003)
      — the happy path is demonstrable by reusing an existing seeded person with a filmography; automated
      tests use their **own** fixtures (Testcontainers, real Postgres) and pass regardless of the demo
      seed's contents. (CAT-001/CAT-003/CAT-004 pattern)

## Non-goals
- **Person collection / search-list** (`GET /api/v1/people`) — still a separate later ticket (CAT-004 D1).
- **New biographical Person fields** (birth date, biography, …) — still deferred (CAT-004 D5); this endpoint
  adds no `people` columns.
- **Filtering/searching within a filmography** (e.g. only acting credits, by genre or year) — the full
  filmography only for this slice (paginated per D11), no filter params.
- Any **write** to catalog data; the out-of-band curation/ingestion pipeline.
- **Rate-limiting** design (product-level; domain/business-rules.md).
- **Ratings/reviews, genres/keywords** endpoints — separate capabilities.

## NFRs / constraints (cite standards)
- **Contract-first:** the operation, the filmography item schema (movie summary + typed capacity, D9), the
  HAL collection `_links`/`_embedded` shape, the path param, and the reusable `page`/`size` params (D11), are
  authored in the split OpenAPI 3.1 spec **before** controller code; the controller implements the generated
  interface; no hand-written DTOs (standards/openapi.md §1/§4/§5; api-codegen spec).
- **Reuse (no platform drift):** `$ref` the shared `Envelope`/`Meta`/`Problem`/`Link` schemas, the existing
  **movie-summary** schema, the reusable `404`/`400` responses, the reusable `page`/`size` parameters, and
  the `meta.pagination`/HAL pagination-link convention; codegen guarantees stay intact — **no**
  per-operation `<Operation><Status>Response*` duplicates (`GeneratedApiCodegenTest` stays green).
- **HAL representation:** Envelope `{data, meta}` as `application/json`, HAL `_links`/`_embedded` inside
  `data`; each embedded Movie carries `_links.self`; pagination **counts** in `meta.pagination` and
  pagination **link URLs** in `data._links` (no duplication). Errors are
  `application/problem+json`, never HAL. Link/collection assembly is **web-adapter-only** (Spring HATEOAS
  `WebMvcLinkBuilder`); domain/application never import Spring HATEOAS nor reference `_links`/`_embedded`.
  (standards/openapi.md §2/§2a; hypermedia-links spec)
- **Security:** public `security: []` — the documented divergence from standards/security.md for the public
  read surface (domain/business-rules.md). People/movie data is public professional/catalog info; no PII
  beyond that, no secrets returned.
- **Errors:** unknown person → `404` (`PERSON_NOT_FOUND`), malformed UUID → `400`, both via the single
  global `@RestControllerAdvice`; `correlationId` on every response; no SQL/stack-trace leak
  (standards/error-handling.md §2/§3/§4).
- **Clean architecture:** add an application **use case** (get a person's filmography) + an **outbound port**
  that loads a person's credits joined to their movie summaries, and a persistence adapter over **Postgres**
  reusing the existing `credits` + `movies` tables (the CAT-003 people/credits migration and the CAT-001
  movies migration) — **no new migration is anticipated**. If the query needs a new index to stay bounded,
  that is a **new** Flyway migration (`V<n>__desc.sql`) — never edit an applied one. Domain has no
  Spring/JPA imports; the architect owns the query shape and aggregate boundaries. (standards/clean-architecture.md)
- **Performance — bounded queries (no N+1):** apply the CAT-002/CAT-003 avoidance pattern (id-page-then-fetch
  / fetch-join / `@EntityGraph` — architect's choice) so the statement count stays bounded and independent of
  the filmography size, with a query-count guard test. The CAT-001 detail, CAT-002 search, CAT-003 credits,
  and CAT-004 person-detail paths must not regress. (standards/testing.md)
- **Testing:** happy/edge/failure per requirement; domain/application as fast unit tests (port mocked); web
  layer via `@WebMvcTest`/MockMvc (HAL collection shape, item shape, empty-filmography `200`, `404`/`400`,
  public); persistence adapter against **real Postgres via Testcontainers (no H2)** with its own fixtures
  independent of the demo seed, including the **N+1 query-count guard** and ordering correctness. Use the
  established **manual Testcontainers singleton** — do **not** mix `@Testcontainers`/`@Container` with it
  (the CAT-001 lesson). If Q5 lands here, add a person-detail web-layer assertion that the new filmography
  link now resolves. (standards/testing.md)

## Dependencies
- **CAT-004 / `catalog/people`** — the addressable Person and the `GET /api/v1/people/{id}` route this
  sub-resource hangs off and whose detail gains the reciprocal link (the Q5 MODIFIED target).
- **CAT-003 / `catalog/credits`** — the Person/Credit model, the `credits` + `people` tables, and the
  cast/crew capacity fields (`character`/`billingOrder`, `department`/`job`) this endpoint reads inversely.
- **CAT-002 / `catalog/movies`** — the **movie-summary** schema reused for each item, the reusable
  `page`/`size` params (if paginated), and the N+1-avoidance reference pattern.
- **CAT-001 / `catalog/movies`** — the `GET /api/v1/movies/{id}` target for each item's `self` link and the
  `movies` schema.
- **hypermedia-links / PLAT-003** — the HAL collection convention. **api-codegen** — the contract pipeline.

## Open questions (residual — minor, for the architect / Gate 1)
All six shape/scope questions were resolved with the author before this ticket went Ready:
- **Item shape** → movie summary + one **typed capacity** object; one item per (movie, capacity) (D9).
- **Cast/crew split vs single list** → one `_embedded.filmography` list (D8).
- **Ordering** → `releaseYear` desc, `title` asc, unique tiebreak (D10).
- **Pagination** → paginated, CAT-002 `page`/`size` convention (D11).
- **Reciprocal person-detail link** → `_links.credits`, in this ticket, a `catalog/people` MODIFIED (D12).
- **Empty/empty-page convention** → follow the CAT-002 HAL collection precedent (D11).

Residual for the architect:
- **Capacity discriminator.** Confirm how the typed capacity object distinguishes acting vs non-acting in
  the contract (e.g. a `type` discriminator field, or a `oneOf` of an acting/non-acting capacity schema) so
  the generated model stays clean and each field set is fully populated — reason from how CAT-003 typed
  cast vs crew items.
- **Bounded-query mechanism.** Choose the N+1-avoidance approach for a paginated filmography (id-page the
  credits, then fetch their movies + genres in bounded queries — architect's call), and site the
  query-count guard accordingly.
- **Demo/seed sufficiency.** Confirm the CAT-003 demo seed has a person with a multi-movie (ideally
  multi-capacity) filmography for the running-app happy path (tests remain seed-independent regardless).

## Domain gaps (to reconcile in `domain/` as part of this work)
- **`catalog/people` capability status.** bounded-contexts.md lists cross-filmography under `people` as
  *planned*. Once this ships, update it: person **filmography** now exists under `catalog/people`
  (`GET /api/v1/people/{id}/credits`); the person **collection** remains planned.
- **Person addressability / HAL relation-naming note.** domain/glossary.md ("HAL relation naming (CAT-004)")
  and domain/business-rules.md currently state person detail carries **`self` only** with **no
  `credits`/`filmography` link** "since no cross-filmography endpoint exists to address." This ticket makes
  that false (D12). Update both to record that the endpoint now exists and person detail carries
  `_links.credits`, and record that relation name in the glossary HAL relation-naming note so all contexts
  stay consistent (as `credits` was recorded at CAT-003 for movie detail).
- **Filmography as a term / ordering + pagination policy.** Add **filmography** to glossary.md as the
  Person-side inverse of a Movie's credits (the embedded relation name for `GET /people/{id}/credits`, D8).
  Record the ordering (`releaseYear` desc, `title` asc — D10) and the pagination decision (paginated,
  `size` default 20/max 100 — D11) in domain/business-rules.md, **noting explicitly** that the "movie
  credits returned whole" rule does **not** extend to a person's filmography, so future tickets stay
  consistent.
- **No new glossary terms for capacity fields.** `character`/`billingOrder` (Cast) and `department`/`job`
  (Crew) are already defined (CAT-003); this endpoint reuses them and introduces none.
