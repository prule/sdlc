## Context

See proposal.md — Why. The API is public, read-only, contract-first (OpenAPI 3.1 →
`redocly bundle` → `openapi-generator`), with a `{data, meta}` success Envelope and RFC 7807
`application/problem+json` errors. Current surface is the walking skeleton (`/api/v1/ping` +
cross-cutting concerns; 34+ tests). Constraints that shape the approach: the codegen guarantee
(`GeneratedApiCodegenTest` — no per-operation `<Operation><Status>Response*` duplicates, one shared
`Problem`), clean architecture (domain/application must not import Spring), and no breaking change to
existing `application/json` content negotiation.

Several items below are **Gate-1 decisions**: recommended here with rationale for the human to confirm
before implementation. They are called out explicitly rather than silently assumed.

## Goals / Non-Goals

**Goals:**
- Establish and prove a HAL navigational-link convention that composes cleanly with the Envelope and
  Problem shapes and keeps OpenAPI authoritative.
- Keep the build green and codegen guarantees intact; no breaking change.

**Non-Goals:**
- Any real `catalog` capability (movie/search/people/genres/ratings) — separate feature tickets.
- HAL-FORMS / write affordances; cursor pagination; content negotiation on `application/hal+json`.
- Auth/rate-limiting changes; DB schema changes (none — read-only, no Flyway migration).

## Decisions

### D1 — Envelope vs HAL topology: `data` is a HAL resource (option a) [GATE 1]
`_links` (and `_embedded` for collections) live **inside** `data`; the Envelope root and `meta` are
unchanged. `meta.pagination` keeps the counts (`page`, `size`, `totalElements`, `totalPages`);
pagination link URLs (`next`/`prev`/`first`/`last`) live in `data._links` — counts vs URLs, no
duplication. Single-resource `data` gains an optional `_links`; collection `data` becomes an object with
`_embedded.<rel>` (array of HAL items, each with its own `self`) plus `_links`, replacing the previous
bare-array collection payload.
- **Why:** non-breaking (root and `meta` untouched; existing `application/json` and ping test stay
  green); keeps `meta` for cross-cutting request metadata and `_links` for resource hypermedia;
  HAL-idiomatic per-resource links usable by HAL clients that look inside `data`.
- **Alternatives:** (b) full-HAL envelope root — rejected: breaking, discards the `{data, meta}`
  convention, clashes with the envelope-per-payload codegen model. (c) links under `meta` — rejected:
  non-standard HAL, no HAL tooling recognises `_links` under `meta`.

### D2 — Success media type stays `application/json` [GATE 1]
HAL fields are embedded in `data`; the document root is our Envelope, not a pure HAL resource, so
`application/hal+json` at the root would misdescribe it. Errors stay `application/problem+json` (never
HAL).
- **Why:** correct for topology D1; preserves the skeleton's content negotiation and the ping test.
- **Alternative:** `application/hal+json` — rejected while the root is the Envelope; revisit only if
  D1 is overturned toward full-HAL roots.

### D3 — Contract-first ↔ runtime reconciliation: describe links in OpenAPI, build hrefs in the adapter
Add shared components to `components/schemas/common.yaml`: a `Link` schema (`href` required URI;
optional `templated`, `title`) and per-resource `_links` object schemas whose properties are the
supported relations, `$ref`ed by each resource `data` schema. The generator therefore emits a single
shared `Link` model and `_links` fields on the generated `data` DTOs. Spring HATEOAS is used **only**
to construct hrefs (`WebMvcLinkBuilder.linkTo(methodOn(...))`) in the web adapter, which populates the
generated `_links` DTO fields; responses serialize as the generated contract DTOs, not Spring HATEOAS
`RepresentationModel`. A conformance test asserts runtime `_links` relations are all declared in the
operation's documented `_links` schema.
- **Why:** keeps OpenAPI the single source of truth and generated clients able to see links, while
  still using the framework to avoid hand-built URL templates. Serializing via generated DTOs (not
  `RepresentationModel`) avoids a second, undocumented HAL serializer diverging from the contract and
  keeps the codegen guarantee.
- **Alternative:** let Spring HATEOAS HAL Jackson module serialize `RepresentationModel` — rejected:
  runtime shape would not be described by the contract and could drift.

### D4 — HAL, not HAL-FORMS [GATE 1, confirm]
Navigational links only; read-only API needs no write/action templates. `HAL_FORMS` media type and
`_templates` are not enabled.

### D5 — Pagination link strategy: page/size aligned with `meta.pagination`
Emit `self`, `next`/`prev` (present only when a next/previous page exists), `first`/`last` (derived from
`totalPages`). Cursor-based deferred. Default/max page size is a domain TODO (`domain/business-rules.md`
"API behaviour"); the proof uses a small fixed page size and does not settle the product default.
- **Why:** aligns with the existing `Pagination` counts; boundary rules are testable edge cases.

### D5b — Pagination validation: invalid params → 400, page-beyond-last → empty 200
Define shared `Page`/`Size` query parameters in `components/parameters/common.yaml` with bounds
(`page` min 0; `size` min 1, a documented max, both with defaults) per `standards/openapi.md` §4. The
generated controller params carry `@Min`/`@Max`; the sample controller is annotated `@Validated` so
those constraints are enforced on query params. Because query-param validation raises
`jakarta.validation.ConstraintViolationException` — which the current `GlobalExceptionHandler` does NOT
map (it handles only `MethodArgumentNotValidException`/body validation, and no `@Validated` exists yet)
— `?size=0`/`?page=-1` would today fall through to the generic handler and return `500`. This is exactly
the 4xx→500 regression class the ticket flags. Fix: add a `ConstraintViolationException` → `400`
`Problem` handler and apply `@Validated`. A **valid** page beyond the last page (page index > totalPages)
is a normal empty `200`, not a `400` — only out-of-bounds/invalid values are rejected.
- **Why:** invalid input must be a client error (`400`), not a server error (`500`); a documented `size`
  max also bounds the validation/response surface. Empty-200-beyond-last matches the existing
  "empty result set is a normal 200" business rule (`domain/business-rules.md`).
- **Test consequence:** the failure test asserts the exact `400` status (not merely `problem+json`,
  which a `500` also satisfies).

### D6 — Scope of the proof [GATE 1, confirm]
Two proofs: (i) add a `self` link to the existing `/api/v1/ping` `data` (single-resource proof, cheap);
(ii) add a minimal, clearly-labelled **trivial sample collection** endpoint to exercise pagination
links (`self`/`next`/`prev`/`first`/`last` + `_embedded` items with `self`). A sample collection is
required because `ping` cannot demonstrate a collection. The sample is demonstrative/throwaway, not a
real catalog capability, and is authored in its own spec/path files so it can be removed or replaced
when real catalog endpoints land.
- **Alternative:** decorate only ping — rejected: cannot satisfy the collection/pagination acceptance
  criteria.

### D7 — Components touched, dependency direction inward-only
- **domain / application:** unchanged; MUST NOT import Spring HATEOAS. The application returns domain
  results + a page-metadata value (page/size/total) with no link/HAL concept. An architecture test
  guards that no `org.springframework.hateoas` import appears under `domain`/`application`.
- **adapters/in/web:** `PingController` and a new sample controller build hrefs with `WebMvcLinkBuilder`
  and populate generated `_links` DTO fields; the sample controller is `@Validated`. The shared
  `GlobalExceptionHandler` gains a `ConstraintViolationException` → `400` `Problem` mapping (query-param
  validation), reusing the existing correlation-id/RFC-7807 machinery.
- **build:** add `spring-boot-starter-hateoas` (web-adapter use only). Pipeline order unchanged.
- No outbound persistence adapter and **no Flyway migration** (read-only; sample data is in-memory/
  static). No DB schema change → no rollback migration needed.

### OpenAPI operations added/changed (contract snippets)
- **Changed** `paths/health.yaml` `ping` `200` → `PingData` gains `_links` (`self`).
- **Added** `paths/samples.yaml` (or similar): `GET /api/v1/samples` (paginated collection; `page`/`size`
  params `$ref`ed from a new shared `Page`/`Size` in `parameters/common.yaml`; responses `200`, `400`
  BadRequest, `500`). Shared components (illustrative):
```yaml
# components/schemas/common.yaml
Link:
  type: object
  required: [href]
  properties:
    href: { type: string, format: uri }
    templated: { type: boolean }
    title: { type: string }
PingLinks:            # per-resource _links object
  type: object
  required: [self]
  properties:
    self: { $ref: '#/Link' }
SampleCollectionLinks:
  type: object
  required: [self]
  properties:
    self:  { $ref: '#/Link' }
    first: { $ref: '#/Link' }
    last:  { $ref: '#/Link' }
    prev:  { $ref: '#/Link' }
    next:  { $ref: '#/Link' }
```
`PingData` adds `_links: { $ref: '.../PingLinks' }`; the sample collection `data` carries
`_embedded.samples: [SampleItem]` (each with its own `_links`) and `_links: SampleCollectionLinks`.

## Risks / Trade-offs

- **Codegen regression** (HAL schemas nudge the generator into per-op duplicates or a divergent media
  type) → keep everything as shared `$ref`ed components; extend `GeneratedApiCodegenTest` to assert a
  single `Link`, no `<Op><Status>Response*`, one `Problem`, and the sample op returns its shared
  envelope. Run a clean `./gradlew build` before finishing.
- **Contract drift** (runtime `_links` diverges from the spec) → serialize via generated DTOs (D3) and
  add a runtime-vs-contract conformance test on the proof endpoints.
- **Envelope/HAL collision on future endpoints** → D1 fixes one rule now, documented in
  `standards/openapi.md`, so catalog tickets don't each re-decide.
- **Media-type expectations** (HAL clients expecting `application/hal+json`) → accepted trade-off of D1;
  documented. Revisit only if D1 is overturned.
- **Scope creep** (sample resource growing into real catalog) → keep it trivial, labelled demonstrative,
  isolated in its own files.

## Open Questions

Deferrable without changing specs/approach/tasks:
- Whether Hypermedia / HAL / Link-relation terms rise to **ubiquitous language** (a `domain/glossary.md`
  entry) or stay purely a technical standard in `standards/openapi.md`. The business-rules note is safe
  either way; the glossary addition awaits a human call.
- The product **default/max page size** (`domain/business-rules.md` TODO) — the proof uses a fixed small
  size and does not settle the default; a later catalog ticket confirms it.
