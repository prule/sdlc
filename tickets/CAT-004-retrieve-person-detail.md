# CAT-004: Retrieve a single person's detail by id

**Type:** Feature
**Bounded context:** catalog (new capability: **people** — `GET /api/v1/people/{id}`) — domain/bounded-contexts.md
**Status:** Ready (open questions resolved by the author; see Decisions)

## User story
As an **API consumer (developer)** (domain/actors-and-personas.md), I want to retrieve the detail of a
single **Person** by its id, so that I can display accurate person data (who acted in and made a movie)
in my own app and resolve the person a **Credit** refers to, without running my own catalog.

## Background & domain context
The natural follow-on to CAT-003 (movie credits). CAT-003 **deliberately deferred** making a **Person**
independently addressable: a Person is exposed **inline** wherever a **Credit** appears (`id` + `name`)
and carries **no** `self` link because "no `/people/{id}` endpoint exists yet" (domain/glossary.md,
"Person"; domain/business-rules.md; CAT-003 D2 and the `catalog/credits` spec "HAL discipline on credits
responses"). This ticket delivers that endpoint and closes the CAT-003 deferral exactly as CAT-003 closed
the CAT-001 credits deferral — after which each Person appearing in a credits response can gain a
resolvable `_links.self`.

The **Person** is already an agreed domain term (domain/glossary.md): an individual who worked on movies
(actor, director, writer, …), currently modelled with only an `id` + `name` and reachable only inline via
Credits. This ticket makes a Person its **own** addressable resource under a new **`catalog/people`**
capability (bounded-contexts.md already lists `people` as *planned*, distinct from `credits`). Everything
stays **public** and **read-only** (domain/business-rules.md).

Person detail is a **single-resource** read, so the response follows the HAL single-resource convention
exactly as CAT-001's movie detail did (standards/openapi.md §2/§2a; openspec/specs/platform/hypermedia-links)
— the resource's own data plus HAL `_links` inside the Envelope's `data`, reusing the shared
Envelope/Meta/Problem/Link components and the contract-first bundle→generate pipeline
(openspec/specs/platform/api-codegen) with **no** per-operation `<Operation><Status>Response*` codegen
duplicates.

**Representation principle (carried from CAT-001):** person detail exposes the person's **own** data plus
HAL links **only to related resources that already exist**. Person detail therefore carries **`self` only**
— no cross-filmography endpoint exists yet, so no dangling link is emitted.

## Decisions (resolved with the author — requirements, not open questions; those settled by precedent are marked)
- **D1 — Smallest slice: one endpoint** *(settled by the CAT-001→CAT-002 precedent)*. This ticket delivers
  **only** `GET /api/v1/people/{id}` (person detail). A person **collection** (`GET /api/v1/people`,
  search/list) is **deferred** to a later ticket, exactly as CAT-002 followed CAT-001.
- **D2 — Stable opaque UUID** *(settled by domain/business-rules.md "Catalog data integrity")*. A Person is
  identified by a stable opaque UUID in the URL; internal DB ids are never exposed — the same Person `id`
  already surfaced inline on credits (CAT-003 / `catalog/credits` spec).
- **D3 — Public, read-only** *(settled by domain/business-rules.md)*. Served `security: []`; never mutates.
- **D4 — New `catalog/people` capability** *(settled by bounded-contexts.md, which already lists it)*. Its
  own spec under `openspec/specs/catalog/people`; distinct from `catalog/credits` (credits serve a Person
  inline from the Movie side; this serves a Person as its own resource).
- **D5 — Minimal field set: `id` + `name`** *(resolved with author)*. Person detail exposes exactly the
  `id` + `name` already modelled in the domain. Biographical fields (birth date, biography, etc.) are **out
  of scope** — adding any is a domain-modelling decision (new glossary terms, a curation source, a Flyway
  migration, privacy considerations) deferred to a later ticket. No new `people` columns; no schema change.
- **D6 — `self` link only** *(resolved with author)*. Person detail carries **only** `data._links.self`. No
  `credits`/`filmography` link is emitted — no cross-filmography endpoint exists to point at, honouring
  CAT-001's "only link to resources that exist" rule (no dangling links). A person-filmography endpoint is a
  later ticket (see Non-goals).
- **D7 — Reciprocal Person `self` link on credits lands in THIS ticket** *(resolved with author)*. Once
  `/people/{id}` exists, each Person appearing in a `catalog/credits` response gains a resolvable
  `_links.self` — delivered **in this change**, mirroring how CAT-003 added the movie-detail `credits` link
  in the same ticket. This is a **MODIFIED requirement** to the `catalog/credits` spec — its "Cast item"/
  "Crew item"/"HAL discipline" requirements currently mandate **no** person `self` link. **Lesson from the
  CAT-003 archive-time header rename:** the credits-spec MODIFIED must reproduce each promoted requirement's
  **header text exactly** so the delta applies cleanly at archive — the architect must copy the headers
  verbatim from the promoted `openspec/specs/catalog/credits/spec.md`, not paraphrase them.
- **D8 — Not-found `code` is `PERSON_NOT_FOUND`** *(resolved with author)*. Mirrors the existing
  `MOVIE_NOT_FOUND` in the error taxonomy — a stable, predictable `code` on the `404` problem+json.

## Acceptance criteria
- [ ] **Happy path — existing person returns enveloped detail.** Given a Person exists, when a client sends
      `GET /api/v1/people/{id}` with a matching valid UUID and no `Authorization` header, then `200`,
      `Content-Type: application/json`, the standard **Envelope** (`{data, meta}`); `data` exposes the
      person's **id** (opaque UUID, never an internal DB id) and **name**; `data._links.self.href` is the
      absolute URI of this operation; `meta` carries `timestamp` and a `correlationId` (UUID).
      (standards/openapi.md §2/§2a; hypermedia-links spec)
- [ ] **Resource shape (minimal, per D5).** `data` contains exactly `id` and `name` (plus `_links`); it
      carries **no** biographical fields, no `_embedded`, and no `_templates`.
- [ ] **HAL discipline.** `data._links` carries **`self` only** (no `credits`/`filmography` link — D6); no
      HAL-FORMS `_templates`, no action/write affordances; success is `application/json` (HAL fields inside
      `data`), errors are `application/problem+json` and never carry `_links`/`_embedded`.
      (standards/openapi.md §2a; hypermedia-links spec)
- [ ] **Failure — unknown id returns 404 problem+json.** Given no Person for a syntactically valid UUID,
      then `404`, `Content-Type: application/problem+json` conforming to the shared `Problem` schema (stable
      `code` `PERSON_NOT_FOUND` (D8), `status`, request `correlationId`), **no** `_links`/`_embedded`, and
      **not** an empty `200`. (domain/business-rules.md "a request for a non-existent movie/person id returns
      404"; standards/error-handling.md §3)
- [ ] **Failure — malformed (non-UUID) id is a 400, not a 500.** Given a path `{id}` that is not a valid
      UUID, then `400` `application/problem+json` conforming to `Problem` with the `correlationId`; it does
      not reach persistence and does not fall through to `500`. (standards/error-handling.md §3)
- [ ] **Public access.** Served with `security: []`; never returns `401`/`403` for a missing/absent
      `Authorization` header; the new path pattern is registered in the single source of truth for public
      endpoints so the public-endpoint consistency test holds. (domain/business-rules.md "Access & security
      posture")
- [ ] **Demo data + independent tests.** The `@Profile("demo")` seed already has people (from CAT-003) — the
      happy path is demonstrable by reusing an existing seeded person; automated tests use their **own**
      fixtures (Testcontainers, real Postgres) and pass regardless of the demo seed's contents. (CAT-001/
      CAT-003 pattern)
- [ ] **Credits gain a resolvable person `self` link (D7 — in this ticket).** After this change, each Person
      in a `GET /api/v1/movies/{id}/credits` response (both cast and crew) carries `person._links.self.href`
      addressing `GET /api/v1/people/{id}`; the rest of the CAT-003 credits shape is otherwise unchanged
      (same fields, same cast/crew orderings). A credits web-layer test asserts the new person `self` link.
      (MODIFIES `catalog/credits`; delta headers must match the promoted spec verbatim — D7)

## Non-goals
- **Person collection / search-list** (`GET /api/v1/people`) — a later ticket, like CAT-002 was to CAT-001.
- **Cross-filmography** ("all movies a person is credited in") as a sub-resource/collection (e.g.
  `GET /api/v1/people/{id}/credits`) — deferred (D6); not built here.
- **New biographical data modelling** (birth date, biography, …) — deferred (D5); `id` + `name` only.
- Any **write** to catalog data; the out-of-band curation/ingestion pipeline.
- **Rate-limiting** design (product-level; domain/business-rules.md).
- **Ratings/reviews, genres/keywords** endpoints — separate capabilities.

## NFRs / constraints (cite standards)
- **Contract-first:** the operation, the **Person** `data` schema, the HAL single-resource `_links` shape,
  and the path param are authored in the split OpenAPI 3.1 spec **before** controller code; the controller
  implements the generated interface; no hand-written DTOs (standards/openapi.md §1/§4/§5; api-codegen spec).
- **Reuse (no platform drift):** `$ref` the shared `Envelope`/`Meta`/`Problem`/`Link` schemas and the
  reusable `404`/`400` responses; codegen guarantees stay intact — **no** per-operation
  `<Operation><Status>Response*` duplicates (`GeneratedApiCodegenTest` stays green).
- **HAL representation:** Envelope `{data, meta}` as `application/json`, HAL `_links` **inside** `data`;
  errors `application/problem+json`, never HAL. Link assembly is **web-adapter-only** (Spring HATEOAS
  `WebMvcLinkBuilder` populating generated `_links` DTO fields); domain/application never import Spring
  HATEOAS nor reference `_links`/`_embedded`. (standards/openapi.md §2a; hypermedia-links spec)
- **Security:** public `security: []` — the documented divergence from standards/security.md for the public
  read surface (domain/business-rules.md). People data is public professional/biographical info
  (domain/business-rules.md "Privacy / compliance"); no PII beyond that, no secrets returned.
- **Errors:** unknown person → `404`, malformed UUID → `400`, both via the single global
  `@RestControllerAdvice`; `correlationId` on every response; no SQL/stack-trace leak
  (standards/error-handling.md §2/§3/§4).
- **Clean architecture:** introduce a real **Person** domain aggregate (no Spring/JPA imports) — promoting
  today's inline-only Person into an addressable aggregate — an application **use case** (get a person by id)
  + an **outbound load-by-id port**; a persistence adapter mapping the JPA entity ↔ domain over **Postgres**,
  **reusing the existing `people` table** introduced in the CAT-003 (people + credits) migration. No schema
  change is anticipated unless Q1 adds biographical fields (then a **new** Flyway migration — never edit an
  applied one). Architect owns aggregate boundaries and query shape. (standards/clean-architecture.md)
- **Testing:** happy/edge/failure per requirement; domain/application as fast unit tests (port mocked); web
  layer via `@WebMvcTest`/MockMvc (single-resource HAL shape, `404`/`400`); persistence adapter against
  **real Postgres via Testcontainers (no H2)** with its **own** fixtures independent of the demo seed. If
  D7/Q3 lands here, add a credits web-layer assertion that the person `self` link now resolves.
  (standards/testing.md)

## Dependencies
- **CAT-003 / `catalog/credits`** — the first Person modelling, the `people` table + seed, and the credits
  responses whose inline Person gains a `self` link (the D7 MODIFIED target).
- **CAT-001 / `catalog/movies`** — the single-resource detail + representation pattern this mirrors.
- **hypermedia-links / PLAT-003** — the HAL single-resource convention. **api-codegen** — the contract
  pipeline.

## Open questions (residual — minor, for the architect / Gate 1)
All four scope/modelling questions were resolved with the author before this ticket went Ready:
- **Person field set** → `id` + `name` only; biographical data deferred (D5).
- **Filmography/credits link on person detail** → `self` only; no filmography endpoint yet (D6).
- **Reciprocal credits `self` link** → lands **in this ticket** as a `catalog/credits` MODIFIED (D7).
- **Not-found code** → `PERSON_NOT_FOUND` (D8).

Residual for the architect:
- **HAL relation name for the inline-Person `self`.** Confirm it is the plain `self` relation on the inline
  Person object within `_embedded.cast`/`_embedded.crew` (consistent with every other `self`), and record it
  in the glossary HAL relation-naming note.
- **Demo/seed sufficiency.** Confirm the existing CAT-003 demo seed contains at least one person reachable by
  a stable id for the running-app happy path (tests remain seed-independent regardless).

## Domain gaps (to reconcile in `domain/` as part of this work)
- **Person addressability rule flips.** domain/glossary.md ("Person") and domain/business-rules.md currently
  state a Person is **not independently addressable** and **never carries a `self` link** ("no `/people/{id}`
  endpoint exists yet"). This ticket makes that false — update both to record that `/people/{id}` now exists
  and a Person carries `self` (and that credits' inline Person gains a resolvable `self` link, per Q3).
- **Person field set stays `id` + `name`.** No new glossary terms this ticket (biographical data deferred,
  D5). A future ticket that adds bio fields must define each new term in glossary.md before exposing it.
- **`catalog/people` capability status.** bounded-contexts.md lists `people` as *planned* (Person detail +
  cross-filmography). Once this ships, update it: person **detail** now exists under `catalog/people`; the
  **collection** and **cross-filmography** remain planned (mirrors how `credits`/`people` were split at CAT-003).
- **HAL relation name for the inline-Person link.** This ticket adds a `self` link on the inline Person in
  credits responses (D7). Record in the glossary's HAL relation-naming note that inline Person now carries
  `self` (pointing at `GET /api/v1/people/{id}`), so all contexts stay consistent (as `credits` was recorded
  at CAT-003).
