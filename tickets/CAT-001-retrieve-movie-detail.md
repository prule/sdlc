# CAT-001: Retrieve a single movie's detail by id

**Type:** Feature
**Bounded context:** catalog (capability: movies) — see domain/bounded-contexts.md
**Status:** Ready (open questions resolved by the author; see Decisions)

## User story
As an **API consumer (developer)** (domain/actors-and-personas.md), I want to retrieve the detail of a
single **Movie** by its id, so that I can display accurate movie data (title, year, genres, synopsis,
rating) in my own app without running my own catalog.

## Background & domain context
This is the **first real `catalog` capability** on the completed platform. The product is a **public,
read-only REST API over an internally-curated catalog** (domain/overview.md, domain/business-rules.md).
Everything prior (health-check, api-codegen, HATEOAS/HAL) was platform foundation; this ticket delivers
the first persisted, consumer-facing catalog resource and proves the end-to-end path from HTTP down to
Postgres for real domain data.

The endpoint retrieves a **Movie** — the core catalog aggregate (domain/glossary.md), identified by a
stable **opaque UUID** in the URL; internal DB ids are never exposed (domain/business-rules.md, "Catalog
data integrity"). Movies with missing **optional** fields are still valid and must be returned.

**Representation principle (author's decision):** movie detail exposes the movie's **own** data plus HAL
links **only to related resources that already exist**. Since no `/people`, `/genres`, or
`/ratings-reviews` endpoints exist yet, movie detail carries **only a `self` link** for now; navigational
`_links` to related resources are **added incrementally as those capabilities ship** (and this resource
must be updated then). Related resources are **not embedded**.

## Decisions (resolved with the author — these are requirements, not open questions)
- **Genres:** exposed **inline** as plain data (genre **names/labels**) on the movie — they are cheap,
  intrinsic labels and need no endpoint.
- **Credits (cast & crew):** **deferred** — not on CAT-001 detail. They reference **Person** entities;
  they will be added as `_links` (and/or a credits sub-resource) once a people/credits endpoint exists.
  No Person/Credit modelling in this ticket.
- **Rating:** an **aggregate 0–5 star** rating (resolves the glossary TODO), **optional** (a movie may
  have no rating yet). Individual **Reviews are deferred** to the `ratings-reviews` capability — not on
  movie detail.
- **Links:** `self` only in CAT-001; no related `_links` yet (nothing to point at). No embedding.
- **Field set:** **required** = id, title, release year, at least one genre; **optional** = runtime,
  synopsis, rating.
- **Data:** ship **both** (a) a small **demo dataset** to make the endpoint demonstrable in a running
  app, and (b) **independent test fixtures**; tests **must not depend on the demo seed** so seed changes
  can't break tests.

## Acceptance criteria
- [ ] **Happy path — existing movie returns enveloped detail.** Given a Movie exists, when a client
      sends `GET /api/v1/movies/{id}` with a matching valid UUID (no `Authorization` header), then the
      response is `200`, `Content-Type: application/json`, the standard **Envelope** (`{data, meta}`);
      `data` exposes the movie's **id, title, release year, genre names**, and (when present) **runtime,
      synopsis, and aggregate 0–5 star rating**; `data._links.self.href` is the absolute URI of this
      operation; and `meta` carries `timestamp` and a `correlationId` (UUID). (standards/openapi.md §2,
      §2a; openspec/specs/platform/hypermedia-links)
- [ ] **Edge — valid movie with missing optional fields still returns 200.** Given a Movie with no
      synopsis, no runtime, and no rating, when retrieved, then `200` with a well-formed Envelope and
      those optional fields represented as absent (omitted, per the platform's non-null convention) —
      **not** `404` or `500`. Required fields (id, title, release year, ≥1 genre) are always present.
      (domain/business-rules.md)
- [ ] **HAL discipline.** `data` carries only the `self` link (no related `_links` yet); no
      `_embedded`; no action/write affordances or HAL-FORMS `_templates`. (hypermedia-links spec)
- [ ] **Failure — unknown id returns 404 problem+json.** Given no Movie for a syntactically valid UUID,
      then `404`, `Content-Type: application/problem+json` conforming to the shared `Problem` schema
      (stable `code`, `status`, request `correlationId`), **no** `_links`/`_embedded`, and **not** an
      empty `200`. (standards/error-handling.md §3)
- [ ] **Failure — malformed id is a 400, not a 500.** Given a path id that is not a valid UUID, then a
      `400` `application/problem+json` conforming to `Problem` with the `correlationId` — it does not
      fall through to a generic `500`. (standards/error-handling.md §3)
- [ ] **Public access.** Served with no authentication (`security: []`); never returns `401`/`403` for a
      missing token. (domain/business-rules.md, "Access & security posture")
- [ ] **Demo data + independent tests.** A small demo dataset makes the happy path demonstrable in a
      running app; the automated tests use their own fixtures (Testcontainers, real Postgres) and pass
      regardless of the demo seed's contents.

## Non-goals
- **Search / listing / filtering** — separate `catalog/search` capability.
- **Credits / cast & crew** on the response — deferred until a people/credits endpoint exists (see
  Decisions); no Person/Credit modelling here.
- **Individual reviews** — deferred to `ratings-reviews`; only the aggregate rating appears.
- Standalone **/people**, **/genres**, **/ratings-reviews** endpoints — not built here.
- Any **write** to catalog data; the **out-of-band curation/ingestion** pipeline.
- **Rate-limiting** design (product-level; domain/business-rules.md).

## NFRs / constraints (cite standards where relevant)
- **Contract-first:** the operation, the `Movie` `data` schema (with genre labels + `self` `_links`),
  and the reusable `404`/`400` problem responses are defined in the authored split OpenAPI 3.1 spec
  **before** controller code; controllers implement the generated interface; no hand-written DTOs
  (standards/openapi.md §1,§4,§5; openspec/specs/platform/api-codegen).
- **Representation:** success uses the Envelope `{data, meta}` as `application/json` with HAL `_links`
  **inside** `data`; errors use RFC 7807 `application/problem+json` and never carry `_links`/`_embedded`
  (standards/openapi.md §2,§2a,§3; hypermedia-links spec). Optional fields omitted, not null (platform
  non-null convention).
- **Security:** public, `security: []`, no auth, no secrets returned — the deliberate documented
  divergence from standards/security.md for the public read surface (domain/business-rules.md).
- **Errors:** not-found is a domain concept mapped to `404 NOT_FOUND` via the single global
  `@RestControllerAdvice`; malformed UUID → `400`; every response carries the `correlationId`
  (standards/error-handling.md §2,§3,§4).
- **Clean architecture:** a real **Movie** domain aggregate (no Spring/JPA) with genres as a value on the
  aggregate; an application use case + outbound port for load-by-id; a persistence adapter mapping JPA
  entity ↔ domain over **Postgres via Flyway**; link assembly only in the web adapter
  (standards/clean-architecture.md; hypermedia-links spec).
- **Testing:** happy/edge/failure per requirement; domain/application as fast unit tests; web layer via
  `@WebMvcTest`/MockMvc with the port mocked; persistence adapter against **real Postgres via
  Testcontainers (no H2)**; tests use their own fixtures, independent of the demo seed
  (standards/testing.md).

## Dependencies
- **hypermedia-links / PLAT-003** and **api-codegen** — the representation + contract pipeline to follow.
- **First persisted catalog data** — a Flyway schema for movies + genres; a demo seed and test fixtures.

## Open questions (residual — minor, for the architect / Gate 1)
- **Rating detail:** does the 0–5 star aggregate also expose a **vote count**, or the score only?
- **Demo seed mechanism & environments:** how the demo dataset is delivered so it is **not loaded in
  production** and **tests don't depend on it** (e.g. a dev/demo-profile loader or an env-scoped seed vs
  a committed Flyway data migration) — a design call, but must honour the "not prod / tests independent"
  constraint above.
- **Data-source licensing/attribution** (domain TODO): if attribution must appear in a movie's
  representation it becomes a field/rule this resource carries — unresolved; do not invent.

## Domain gaps (to reconcile in `domain/` as part of this work)
- **Rating scale resolved → update domain:** glossary.md and business-rules.md should record **0–5 star
  aggregate** (this ticket updates them).
- **"Movie detail" vs "movie summary":** the glossary defines **Movie** but not detail-vs-summary
  completeness; capture it once `search` (a later ticket) needs a summary shape, so the two stay
  consistent.
