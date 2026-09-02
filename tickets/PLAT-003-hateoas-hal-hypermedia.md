# PLAT-003: Adopt HATEOAS (HAL) hypermedia in API responses

**Type:** Technical
**Area:** platform (representation / API contract) — supports the `catalog` bounded context (see domain/bounded-contexts.md)
**Status:** Draft

## Problem / rationale
Our public, read-only movie API (domain/overview.md) will grow a set of interlinked read
capabilities — movie detail, search, people/credits, genres/keywords, ratings/reviews (domain/bounded-contexts.md,
`catalog` planned capabilities). For a third-party developer (the "API consumer" persona,
domain/actors-and-personas.md) to **discover** related resources and **navigate pages** without
hard-coding URL templates, responses should carry hypermedia links.

We have decided to adopt **hypermedia (HATEOAS) using Spring HATEOAS in the HAL representation**.
This is a **foundational representation change**: establishing it now — while the surface is just
the walking skeleton — lets the planned catalog feature tickets (movie search, retrieve movie/person,
browse genres) build on a consistent, already-proven link convention rather than each reinventing it.
Because the API is public, read-only and rate-limited (domain/business-rules.md), links are for
**discoverability and pagination** (`self`, collection `next`/`prev`, and related resources such as a
movie's credits/genres), **not** for state-changing actions/transitions.

The crux to settle before design (Gate 1) is **how HAL `_links`/`_embedded` coexist with our two
existing representation standards** — the success Envelope (`{data, meta}`, standards/openapi.md §2)
and RFC 7807 Problem errors (§3) — under our contract-first pipeline where OpenAPI is the source of
truth and code is generated (standards/openapi.md §5, openspec/specs/platform/api-codegen).

## Scope
- Introduce **Spring HATEOAS (HAL)** as the hypermedia mechanism for the API's representations.
- Establish the **convention** (at requirements altitude — not the exact placement/design) for:
  - `self` link on single-resource responses.
  - `self` plus pagination links (`next`/`prev`, and `first`/`last` where derivable) on collection
    responses, aligned with the existing `meta.pagination` (page/size) model (standards/openapi.md §2).
  - links to **related resources** (e.g. a movie's credits/genres) as a discoverability affordance.
- **Document** the chosen HAL convention in the **OpenAPI contract** so the link shape is described in
  the spec (link relations present, their meaning), keeping OpenAPI the single source of truth even
  though Spring HATEOAS builds concrete URLs at runtime.
- **Update `standards/openapi.md`** to document how HAL `_links`/`_embedded` fit the representation
  alongside the Envelope and Problem shapes (so future tickets follow one rule).
- **Verify/update the existing walking skeleton** so the build stays green with HATEOAS on the
  classpath, and provide a **small proof** that the mechanism works — HAL `_links` on an existing
  read resource (e.g. the `/api/v1/ping` / health resource) or a trivial sample resource, mirroring
  how the skeleton proved each prior standard.

## Out of scope
- Any real `catalog` capability (movie/search/people/genres/ratings) — those are separate feature
  tickets that will *consume* this convention. This ticket only establishes and proves the mechanism.
- **Action/transition links** and write affordances (create/update/delete, HAL-FORMS templates) — the
  API is read-only (domain/business-rules.md); links are navigational only.
- Changing the language/stack — stays **Java 25 / Spring Boot** (decided; no Kotlin).
- Authentication/authorization changes — the API remains public, `security: []` (domain/business-rules.md).
- Rate-limiting design (domain/business-rules.md TODO) — untouched here.

## Constraints (standards that apply)
- **standards/openapi.md** — contract-first: the HAL representation MUST be described in the authored
  split OpenAPI spec (§1) and MUST coexist with the standard success Envelope (§2) and RFC 7807
  Problem errors (§3); error responses stay `application/problem+json` and are **not** HAL.
- **openspec/specs/platform/api-codegen** & **standards/openapi.md §5** — the redocly-bundle →
  openapi-generator pipeline MUST still produce shared component types with **no per-operation
  `<Operation><Status>Response*` duplicates**; `GeneratedApiCodegenTest` MUST stay green.
- **standards/clean-architecture.md** — hypermedia/link assembly is a web-adapter (representation)
  concern; domain and application layers MUST NOT import Spring HATEOAS or know about HAL.
- **standards/error-handling.md** — error handling via the single `@RestControllerAdvice` returning
  Problem+json is unchanged; correlation id still carried on every response.
- **standards/testing.md** — happy/edge/failure tests for the proof (Testcontainers for any DB test; MockMvc/WebTestClient for the web layer).

## Acceptance / done criteria
- [ ] Spring HATEOAS (HAL) is present and wired such that a read resource can emit HAL `_links`; a
      single-resource proof response includes a `self` link.
- [ ] A **collection** proof response includes `self` and pagination links (`next`/`prev` present when
      a next/previous page exists; absent/appropriately handled at boundaries — first page has no
      `prev`, last page has no `next`), consistent with `meta.pagination` (page/size).
- [ ] The HAL link shape (relations and their meaning) is **described in the OpenAPI contract**, and
      `standards/openapi.md` is updated to state how `_links`/`_embedded` coexist with the Envelope
      and Problem shapes.
- [ ] Error responses remain **RFC 7807 `application/problem+json`** (not HAL), and every response
      still carries the correlation id (verifiable on a triggered error).
- [ ] The contract-first pipeline still holds: `./gradlew build` is green, `GeneratedApiCodegenTest`
      passes, and no per-operation `<Operation><Status>Response*` duplicate models are generated.
- [ ] The existing walking skeleton stays green — all previously passing tests (34+) still pass, plus
      new tests covering the proof (a `self` link present; a pagination-boundary edge case; and a
      failure path returning Problem+json, not HAL).

## Risks
- **Envelope vs HAL collision.** HAL's top-level `_links`/`_embedded` and our `{data, meta}` envelope
  are two competing "shapes" for the response root; picking the wrong reconciliation now would force a
  breaking change on every future catalog endpoint. (This is the central open question below.)
- **Contract-first drift.** Spring HATEOAS composes links at runtime; if the OpenAPI contract does not
  describe the link shape, the spec silently stops being the source of truth and generated clients
  can't see links. The chosen approach must keep the spec authoritative.
- **Codegen regression.** Introducing HAL media types / schemas could nudge the generator into emitting
  per-operation duplicates or a divergent media type, breaking the api-codegen guarantees.
- **Media type / content negotiation.** HAL commonly uses `application/hal+json`; our envelope success
  responses currently use `application/json`. Reconciling the success media type is part of the design
  and must not break existing `application/json` expectations of the skeleton.
- **Scope creep into design.** Easy to drift from "establish + prove the convention" into building real
  catalog endpoints; kept out of scope above.

## Open questions (need a human/architect decision at Gate 1)
- **Envelope vs HAL topology.** How do HAL `_links` (and `_embedded`) sit relative to the `{data, meta}`
  Envelope? Options to weigh: (a) `data` is a HAL resource carrying its own `_links`/`_embedded` inside
  the envelope; (b) the envelope root itself becomes a HAL document; (c) links surfaced under `meta`.
  This is the crux — decide before any spec/design work.
- **Contract-first ↔ runtime links reconciliation.** How is contract-first honoured given Spring
  HATEOAS builds links at runtime? (e.g. describe a `_links` schema / link-relation catalogue in
  OpenAPI that the runtime output must conform to; how/whether to validate conformance.)
- **HAL vs HAL-FORMS vs plain.** Confirm **HAL** (navigational links only) is the target for this
  read-only API, rather than HAL-FORMS (which adds write/action templates we don't need).
- **Pagination link strategy.** Page/size (aligning with existing `meta.pagination`) vs cursor-based —
  and which relations to emit (`next`/`prev` only, or also `first`/`last`). Align with the confirmed
  default/max page size (domain/business-rules.md TODO).
- **Success media type.** Do HAL success responses use `application/hal+json`, or stay
  `application/json` with HAL fields embedded? (Impacts content negotiation and the skeleton's current
  expectations.)
- **Scope of the proof.** Is decorating the existing ping/health resource an acceptable proof, or is a
  dedicated trivial sample resource (including a collection, to exercise pagination links) preferred?

## Domain gaps (to add to `domain/` as part of this work, if approved)
- **Glossary:** no entry for **hypermedia / HAL / link relation (`self`, `next`, `prev`)** or the idea
  of navigational links as a representation concern. If HAL becomes a durable, cross-cutting product
  convention, add a short glossary note (or record it purely as a technical standard in
  standards/openapi.md — a human should decide whether it rises to ubiquitous language).
- **Business rules:** domain/business-rules.md "API behaviour" section describes the Envelope and
  Problem shapes but says nothing about hypermedia links; once the topology is decided it should note
  that collection/resource responses carry navigational links (read-only, no action affordances).
