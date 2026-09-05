# CAT-006: Search / list people

**Type:** Feature
**Bounded context:** catalog (existing capability: **people** — third endpoint, the collection `GET /api/v1/people`) — see domain/bounded-contexts.md
**Status:** Ready (open questions resolved by the author; see Decisions)

## User story
As an **API consumer (developer)** (domain/actors-and-personas.md), I want to **search and list People**
by name, sorted and paginated, so I can find and browse the people in the catalog — rather than only
fetching a person I already know the id of — without downloading or indexing the whole catalog myself.

## Background & domain context
The person-side analogue of CAT-002 (movie search): the first **collection** endpoint over
`catalog/people`. CAT-004 shipped single-person detail (`GET /api/v1/people/{id}`) and CAT-005 added a
person's filmography (`GET /api/v1/people/{id}/credits`); this adds the collection `GET /api/v1/people`,
which is to CAT-004 what CAT-002 was to CAT-001. It lets consumers find people by name (and browse them
alphabetically) instead of only resolving a known id. The person **collection/search-list** is listed as
*planned* under the `people` capability in domain/bounded-contexts.md — this ticket delivers it.

Each result item is a **person summary** — the light representation used in list results, carrying its own
`_links.self` to `GET /api/v1/people/{id}` for full detail. A **Person** (domain/glossary.md) is currently
modelled with only `id` + `name` (biographical fields were deliberately deferred at CAT-004 D5), so a
person summary is `id` + `name` + `_links.self`. This is a **collection** endpoint, so it follows the HAL
collection convention (standards/openapi.md §2/§2a; openspec/specs/platform/hypermedia-links), reusing the
shared Envelope/Meta/Problem/Link components, the reusable `page`/`size` parameters, the
`meta.pagination`/HAL pagination-link convention, and the reusable `400` response — nothing new at the
platform layer. An **empty result set is a normal 200** (domain/business-rules.md). No new domain concept is
introduced; this is a new *view* over the existing Person model.

## Decisions (settled by precedent — requirements, not open questions)
- **D1 — Third endpoint of the existing `catalog/people` capability** *(settled by bounded-contexts.md,
  which lists the person collection/search-list under `people` as planned)*. `GET /api/v1/people` is a
  **collection** endpoint authored into the existing `openspec/specs/catalog/people/spec.md` — analogous to
  how `GET /api/v1/movies` is the collection for `catalog/movies` (CAT-002). Do not create a second people
  capability.
- **D2 — Public, read-only** *(settled by domain/business-rules.md)*. Served `security: []`; never mutates;
  the path pattern is registered in the single source of truth for public endpoints so the public-endpoint
  consistency test holds.
- **D3 — Result items are person summaries** *(settled by CAT-002's collection-of-summaries precedent)*.
  Each item is a **person summary** carrying its own `_links.self` to `GET /api/v1/people/{id}`, not the
  full detail representation. Record "person summary" in domain/glossary.md as CAT-002 did for "movie
  summary" (see Domain gaps).
- **D4 — Pagination reuses the CAT-002 convention exactly** *(settled by domain/business-rules.md "Search"
  + CAT-002/CAT-005)*. Zero-based `page`; `size` **default 20, maximum 100**. `meta.pagination` carries
  `page`/`size`/`totalElements`/`totalPages`; `data._links` carries HAL pagination links
  (`self`/`first`/`last` on every page, `next` when a later page exists, `prev` when an earlier page
  exists). A valid page beyond the last returns an empty `200` (no `next`); an empty result set is a normal
  `200`. Invalid pagination (`page` < 0, `size` < 1, `size` > 100) → `400` problem+json, not `500`.
  Empty/last-page link and `totalPages` conventions follow the platform's existing CAT-002 HAL collection
  precedent (do not invent a new convention).
- **D5 — Unknown sort field → 400** *(settled by CAT-002)*. An unsupported sort field is rejected `400`
  `application/problem+json` — not silently ignored, not `500`.
- **D6 — No 404 for this endpoint** *(settled by domain/business-rules.md "empty result set is a normal
  200")*. This is a collection endpoint: no matches (or an empty catalog) is a `200` with an empty
  collection, never a `404`. Public access is never `401`/`403`.
- **D7 — Bounded queries per page (no N+1)** *(settled by the CAT-002/CAT-005 precedent + standards/
  testing.md)*. A page of N people must issue a **bounded** number of SQL statements independent of N — no
  per-row query. The N+1 risk here is **much lower than CAT-002/CAT-005** precisely because a person summary
  is only `id` + `name` with **no to-many associations** (no genres, no credits) to lazily load; the guard
  is retained regardless.

## Decisions (resolved with the author — requirements, not open questions)
- **D8 — Person summary field set = `id` + `name` + `_links.self`.** A Person has only `id` + `name`
  modelled today (CAT-004 D5 deferred all biographical fields), so there is nothing else to carry. Adding
  any field to the summary is a deferred **domain-modelling** decision (new glossary term, curation source,
  Flyway migration, privacy review) and is **out of scope** here.
- **D9 — Name query param `name`, case-insensitive substring match.** Mirrors CAT-002's title filter
  (`?title=matrix` matches "The Matrix"): `?name=<term>` returns people whose `name` contains `<term>`
  case-insensitively.
- **D10 — Name is the only filter.** A Person has no other person-level attribute modelled (role, department
  and "known-for" all live on **credits**, not the Person). Person search is **name-only** in this slice;
  role/department/known-for/"has credits" filters are out of scope (see Non-goals) — they need domain
  data/decisions that do not exist yet.
- **D11 — Sortable field `name`; default sort `name` ascending.** With only `id` + `name`, `name` is the
  sortable field (`asc`/`desc`). Default **`name` ascending** because people are naturally browsed
  alphabetically (deliberately *unlike* movies, which default to `releaseYear` descending — there is no
  date-like field on a Person).

## Acceptance criteria
- [ ] **Default list returns a paginated first page.** `GET /api/v1/people` (no params, no auth) → `200`,
      `application/json`, Envelope `{data, meta}`; `data._embedded.<rel>` is an array of person **summary**
      items (each with `_links.self` to its `GET /api/v1/people/{id}`); `data._links` carries `self`,
      `first`, `last`, and `next` (first page → **no `prev`**); `meta.pagination` has `page`, `size`
      (=20), `totalElements`, `totalPages`; results are ordered by the default sort (`name` ascending — D11);
      `meta` carries `timestamp` and a `correlationId` (UUID). (standards/openapi.md §2/§2a;
      hypermedia-links spec)
- [ ] **Summary shape (minimal, per D8).** Each item exposes exactly `id` (the Person's opaque UUID, never
      an internal DB id) and `name`, plus `_links.self` — and **no** biographical field, no `_embedded`, no
      `_templates`.
- [ ] **Name query narrows results (case-insensitive substring — D9).** `?name=<term>` returns only People
      whose `name` contains `<term>` case-insensitively; `meta.pagination` counts reflect the matched subset.
- [ ] **Sorting.** `?sort=name,asc` and `?sort=name,desc` order results accordingly (D11); an unsupported
      sort field is rejected `400` problem+json (not silently ignored, not `500` — D5).
- [ ] **Empty result set is a normal 200.** A query matching no Person (or an empty catalog) → `200` with an
      empty `data._embedded.<rel>`, `data._links.self` present and no `next`/`prev`, `meta.pagination`
      `totalElements: 0` (empty-page link/`totalPages` convention aligned with the platform CAT-002 HAL
      collection precedent — see Open questions) — **not** a `404`/error. (D6)
- [ ] **Pagination boundaries (D4).** First page → `self`/`first`/`last`/`next`, no `prev`; last page →
      `self`/`first`/`last`/`prev`, no `next`; a valid page index beyond the last → empty `200` (no `next`),
      not a `400`/`404`. Pagination navigation links preserve the active `name`/`sort` query parameters.
- [ ] **Invalid pagination/sort params → 400, not 500.** `page` < 0, `size` < 1, `size` > 100, or an unknown
      sort field → `400`, `application/problem+json` conforming to `Problem` (stable `code`,
      `correlationId`), no `_links`/`_embedded`. The controller is `@Validated` so `page`/`size` bounds are
      enforced. (standards/openapi.md §2a; error-handling.md §3)
- [ ] **Public access.** `security: []`; never `401`/`403` for a missing/absent `Authorization` header; the
      new path pattern is registered in the single source of truth for public endpoints so the
      public-endpoint consistency test holds. (domain/business-rules.md "Access & security posture")
- [ ] **HAL discipline.** Success is `application/json` with HAL `_links`/`_embedded` inside `data` and no
      `_templates` and no action/write affordances; each embedded item carries `_links.self` addressing
      `GET /api/v1/people/{id}`; errors are `application/problem+json` and never carry `_links`/`_embedded`.
      (standards/openapi.md §2a; hypermedia-links spec)
- [ ] **Bounded queries per page (no N+1 — D7).** A page of N people issues a **bounded** number of SQL
      statements independent of N — not one query per row; increasing `size` does not increase the
      statement count. A Testcontainers test/assertion guards the query count. (Simpler than CAT-002/CAT-005:
      a person summary has no to-many associations to load.) (testing.md)
- [ ] **Demo data + independent tests.** The `@Profile("demo")` seed already has people (from CAT-003) — the
      happy path is demonstrable by listing existing seeded people; automated tests use their **own**
      fixtures (Testcontainers, real Postgres) and pass regardless of the demo seed's contents.
      (CAT-001/CAT-003/CAT-004/CAT-005 pattern)

## Non-goals
- **Person detail** (stays CAT-004; search returns summaries only) and **filmography** (CAT-005) — this
  endpoint returns person summaries, not detail or credited movies.
- **Filtering people by role / department / "known-for" / "has credits"** — no person-level domain data or
  decision exists for these (they live on credits, not on the Person); explicitly out of scope, and **not**
  to be invented. A future role/credit-aware people filter would need new domain data/decisions.
- **New biographical Person fields** in the summary (birth date, biography, …) — still deferred (CAT-004 D5);
  this endpoint adds no `people` columns and no glossary terms for bio data.
- Any **write** to catalog data; the out-of-band curation/ingestion pipeline; **rate-limiting** design
  (product-level; domain/business-rules.md).
- **Ratings/reviews, genres/keywords** endpoints — separate capabilities.

## NFRs / constraints (cite standards)
- **Contract-first:** the operation, the **person summary** `data` schema, the HAL collection
  `_links`/`_embedded` shape, and the query params (`name`, `sort`, plus reusable `page`/`size`) are
  authored in the split OpenAPI 3.1 spec **before** controller code; the controller implements the generated
  interface; no hand-written DTOs (standards/openapi.md §1/§4/§5; api-codegen spec).
- **Reuse (no platform drift):** `$ref` the shared `Envelope`/`Meta`/`Problem`/`Link` schemas, the reusable
  `page`/`size` parameters, the `meta.pagination`/HAL pagination-link convention, and the reusable `400`
  response; the person summary schema should reuse/align with the existing `catalog/people` Person schema
  shape (`id` + `name`). Codegen guarantees stay intact — **no** per-operation
  `<Operation><Status>Response*` duplicates (`GeneratedApiCodegenTest` stays green).
- **HAL representation:** Envelope `{data, meta}` as `application/json`, HAL `_links`/`_embedded` inside
  `data`; each embedded item carries `_links.self`; pagination **counts** in `meta.pagination`, pagination
  **link URLs** in `data._links` (no duplication). Errors are `application/problem+json`, never HAL.
  Link/collection assembly is **web-adapter-only** (Spring HATEOAS `WebMvcLinkBuilder`); domain/application
  never import Spring HATEOAS nor reference `_links`/`_embedded`. (standards/openapi.md §2/§2a;
  hypermedia-links spec)
- **Security:** public `security: []` — the documented divergence from standards/security.md for the public
  read surface (domain/business-rules.md). People data is public professional info; no PII beyond that, no
  secrets returned.
- **Errors:** invalid params → `400` via the single global `@RestControllerAdvice`; `correlationId` on every
  response; no SQL/stack-trace leak (standards/error-handling.md §2/§3/§4). No `404` on this collection
  endpoint (D6).
- **Clean architecture:** add an application **search/list use case** + an **outbound search/query port**
  (taking the name filter + page/sort, returning matches + page metadata), and a persistence adapter
  querying **Postgres** with pagination/filter/sort over the existing `people` table (introduced in the
  CAT-003 people/credits migration and reused by CAT-004/CAT-005). **Reuse the existing `catalog/people`
  PersonJpaEntity/repository — do not create a second.** **No new migration is anticipated**; if a
  case-insensitive-name index is genuinely needed to keep the query bounded/performant, that is a **new**
  Flyway migration (`V<n>__desc.sql`) — never edit an applied one. Domain has no Spring/JPA imports; the
  architect owns the query shape. (standards/clean-architecture.md)
- **Performance — bounded queries per page (no N+1):** loading a page must stay within a bounded statement
  count regardless of `size`, guarded by a query-count test. Lower risk than CAT-002/CAT-005 (no to-many
  associations on a person summary), but the guard is retained. The CAT-001/CAT-002/CAT-003/CAT-004/CAT-005
  paths must not regress. (standards/testing.md)
- **Testing:** happy/edge/failure per requirement (mirror CAT-002's test list): domain/application unit
  tests (search port mocked); web layer via `@WebMvcTest`/MockMvc (HAL collection shape, name filter, sort,
  pagination boundaries, empty-200, invalid-params-400, public); persistence adapter against **real Postgres
  via Testcontainers (no H2)** with its own fixtures independent of the demo seed, including name-filter/
  sort/pagination correctness and the **bounded-query guard**. Use the established **manual Testcontainers
  singleton** — do **not** mix `@Testcontainers`/`@Container` with it (the CAT-001 lesson). (standards/testing.md)

## Dependencies
- **CAT-004 / `catalog/people`** — the addressable Person, the `GET /api/v1/people/{id}` target for each
  item's `self` link, and the Person schema/aggregate + PersonJpaEntity/repository reused here.
- **CAT-003 / `catalog/people`+`credits`** — the `people` table + demo seed reused by the search adapter.
- **CAT-002 / `catalog/movies`** — the collection-of-summaries precedent, the reusable `page`/`size` params,
  the `meta.pagination`/HAL pagination-link convention, and the N+1-avoidance/query-count-guard reference.
- **hypermedia-links / PLAT-003** — the HAL collection convention. **api-codegen** — the contract pipeline.

## Reciprocal links / MODIFIED
- **No `catalog/people` MODIFIED is expected.** This endpoint is purely **additive** — a new collection over
  existing people. Unlike CAT-003/CAT-004/CAT-005 (each of which closed the previous ticket's deferred
  reciprocal link), person detail and filmography already exist and do not gain a new reciprocal link from a
  people **collection** (a person summary points to detail via `self`; detail does not point back to the
  collection). If the architect nonetheless identifies a genuine MODIFIED to a promoted spec, apply the
  **verbatim-header lesson** (CAT-003/CAT-004/CAT-005): reproduce each promoted requirement's header text
  and scenario names **exactly** (copy, do not paraphrase) so the delta applies cleanly at archive.

## Open questions (residual — minor, for the architect / Gate 1)
All scope/shape questions were resolved with the author before this ticket went Ready:
- **Person-summary field set** → `id` + `name` + `_links.self` (D8).
- **Name-query param + semantics** → `?name=`, case-insensitive substring (D9).
- **Filter set** → name only, no other filters (D10).
- **Default sort** → sortable `name`, default `name` ascending (D11).
- **MODIFIED needed?** → none; purely additive (see Reciprocal links / MODIFIED).

Residual for the architect:
- **Empty-result pagination convention.** For zero matches, follow whatever the platform's existing CAT-002
  HAL collection proof already does for `totalPages` and `first`/`last` on an empty collection — do not
  invent a new convention.
- **Case-insensitive name matching mechanism.** Choose how the substring match is implemented so it stays
  bounded (e.g. `LOWER(name) LIKE` with proper `ESCAPE`, per the CAT-002 LIKE-escaping lesson); decide
  whether a name index is genuinely needed (if so, a NEW Flyway migration — never edit an applied one).

## Domain gaps (to reconcile in `domain/` as part of this work)
- **"Person summary" term.** Add a **Person summary** row to domain/glossary.md (the light representation
  used in `GET /api/v1/people` list results: `id` + `name` + `self` link to detail), mirroring the existing
  **Movie summary** row (CAT-002), so search and detail stay consistent.
- **People search/list policy.** Record in domain/business-rules.md that the person **collection**
  (`GET /api/v1/people`) exists and is **paginated** (zero-based `page`; `size` default 20, max 100, same
  convention as Search/filmography), **name-filtered** (case-insensitive substring — confirm), and
  **sortable** by `name` with **default `name` ascending** (confirm) — noting explicitly that, unlike movie
  search's `releaseYear`-desc default, people default to alphabetical because a Person has no date-like field.
- **`catalog/people` capability status.** domain/bounded-contexts.md lists the person collection/search-list
  under `people` as *planned*. Once this ships, update it: the person **collection** now exists under
  `catalog/people` (`GET /api/v1/people`).
- **No new biographical terms.** The person summary stays `id` + `name` (CAT-004 D5); a future ticket adding
  bio fields must define each new term in glossary.md before exposing it. Do not add any here.
